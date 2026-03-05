package com.audiomemory.service.recording

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.audiomemory.AudioMemoryApp
import com.audiomemory.R
import com.audiomemory.data.entity.AudioChunkEntity
import com.audiomemory.data.repository.MemoryRepository
import com.audiomemory.service.processing.PipelineOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * Foreground service that continuously records audio and produces
 * VAD-chunked WAV files for downstream processing.
 *
 * Audio format: PCM 16-bit, 16kHz, mono
 * Chunk size: 30-60 seconds, split at silence boundaries
 */
@AndroidEntryPoint
class AudioRecordingService : Service() {

    companion object {
        const val ACTION_START = "com.audiomemory.action.START_RECORDING"
        const val ACTION_STOP = "com.audiomemory.action.STOP_RECORDING"
        const val ACTION_PAUSE = "com.audiomemory.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.audiomemory.action.RESUME_RECORDING"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val NOTIFICATION_ID = 1001

        // VAD parameters
        private const val SILENCE_THRESHOLD = 500 // amplitude threshold for silence detection
        private const val MIN_CHUNK_DURATION_MS = 30_000L // 30 seconds minimum
        private const val MAX_CHUNK_DURATION_MS = 60_000L // 60 seconds maximum
        private const val SILENCE_DURATION_MS = 1_000L // 1 second of silence triggers split

        val isRecording = MutableStateFlow(false)
        val isPaused = MutableStateFlow(false)
        val elapsedSeconds = MutableStateFlow(0L)
        val currentSessionId = MutableStateFlow<Long?>(null)
    }

    @Inject lateinit var repository: MemoryRepository
    @Inject lateinit var pipelineOrchestrator: PipelineOrchestrator

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var sessionId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        startForeground()
        acquireWakeLock()

        scope.launch {
            sessionId = repository.startSession()
            currentSessionId.value = sessionId
            isRecording.value = true
            isPaused.value = false
            elapsedSeconds.value = 0

            startTimer()
            recordAudio()
        }
    }

    private fun stopRecording() {
        scope.launch {
            isRecording.value = false
            isPaused.value = false
            recordingJob?.cancel()
            timerJob?.cancel()
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            if (sessionId > 0) {
                repository.endSession(sessionId)
            }
            currentSessionId.value = null
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pauseRecording() {
        isPaused.value = true
        timerJob?.cancel()
        scope.launch {
            if (sessionId > 0) repository.pauseSession(sessionId)
        }
    }

    private fun resumeRecording() {
        isPaused.value = false
        startTimer()
    }

    private fun startTimer() {
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (!isPaused.value) {
                    elapsedSeconds.value++
                }
            }
        }
    }

    /**
     * Core recording loop: reads PCM audio in a tight loop, detects silence
     * boundaries, and writes 30-60s WAV chunks to disk.
     */
    private fun recordAudio() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(SAMPLE_RATE * 2) // at least 1 second buffer

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            stopRecording()
            return
        }

        audioRecord?.startRecording()

        recordingJob = scope.launch {
            val readBuffer = ShortArray(bufferSize / 2)
            var chunkData = mutableListOf<Short>()
            var chunkStartTime = System.currentTimeMillis()
            var silentFrames = 0
            val silentFrameThreshold = (SILENCE_DURATION_MS * SAMPLE_RATE / 1000).toInt()

            while (isActive && isRecording.value) {
                if (isPaused.value) {
                    delay(100)
                    continue
                }

                val readCount = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                if (readCount <= 0) continue

                // Add samples to current chunk
                for (i in 0 until readCount) {
                    chunkData.add(readBuffer[i])
                }

                // Check for silence (simple energy-based VAD)
                val maxAmplitude = readBuffer.take(readCount).maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0
                if (maxAmplitude < SILENCE_THRESHOLD) {
                    silentFrames += readCount
                } else {
                    silentFrames = 0
                }

                val elapsed = System.currentTimeMillis() - chunkStartTime

                // Split chunk if: (silence detected AND past min duration) OR past max duration
                val shouldSplit = (silentFrames > silentFrameThreshold && elapsed >= MIN_CHUNK_DURATION_MS)
                    || elapsed >= MAX_CHUNK_DURATION_MS

                if (shouldSplit && chunkData.isNotEmpty()) {
                    val endTime = System.currentTimeMillis()
                    saveChunk(chunkData.toShortArray(), chunkStartTime, endTime)
                    chunkData = mutableListOf()
                    chunkStartTime = System.currentTimeMillis()
                    silentFrames = 0
                }
            }

            // Save any remaining data
            if (chunkData.isNotEmpty()) {
                saveChunk(chunkData.toShortArray(), chunkStartTime, System.currentTimeMillis())
            }
        }
    }

    /**
     * Writes a PCM chunk as a WAV file and creates a database record.
     */
    private suspend fun saveChunk(data: ShortArray, startTime: Long, endTime: Long) {
        val dir = File(filesDir, "audio_chunks")
        dir.mkdirs()
        val file = File(dir, "chunk_${sessionId}_${startTime}.wav")

        withContext(Dispatchers.IO) {
            writeWavFile(file, data)
        }

        val chunk = AudioChunkEntity(
            sessionId = sessionId,
            filePath = file.absolutePath,
            startTime = startTime,
            endTime = endTime,
            sizeBytes = file.length(),
        )
        val chunkId = repository.insertChunk(chunk)

        // Trigger the processing pipeline for this chunk
        pipelineOrchestrator.enqueueProcessing(chunkId)
    }

    /**
     * Writes PCM 16-bit data as a standard WAV file.
     */
    private fun writeWavFile(file: File, data: ShortArray) {
        val byteData = ByteArray(data.size * 2)
        for (i in data.indices) {
            byteData[i * 2] = (data[i].toInt() and 0xFF).toByte()
            byteData[i * 2 + 1] = (data[i].toInt() shr 8 and 0xFF).toByte()
        }

        val totalDataLen = byteData.size.toLong()
        val totalAudioLen = totalDataLen + 36

        FileOutputStream(file).use { fos ->
            // WAV header
            fos.write("RIFF".toByteArray())
            fos.write(intToByteArray(totalAudioLen.toInt()))
            fos.write("WAVE".toByteArray())
            fos.write("fmt ".toByteArray())
            fos.write(intToByteArray(16)) // PCM chunk size
            fos.write(shortToByteArray(1)) // PCM format
            fos.write(shortToByteArray(1)) // mono
            fos.write(intToByteArray(SAMPLE_RATE))
            fos.write(intToByteArray(SAMPLE_RATE * 2)) // byte rate
            fos.write(shortToByteArray(2)) // block align
            fos.write(shortToByteArray(16)) // bits per sample
            fos.write("data".toByteArray())
            fos.write(intToByteArray(totalDataLen.toInt()))
            fos.write(byteData)
        }
    }

    private fun intToByteArray(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte(),
        (v shr 16 and 0xFF).toByte(), (v shr 24 and 0xFF).toByte()
    )

    private fun shortToByteArray(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte()
    )

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, AudioMemoryApp.RECORDING_CHANNEL_ID)
            .setContentTitle("AudioMemory Recording")
            .setContentText("Listening and remembering...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause",
                createPendingIntent(ACTION_PAUSE))
            .addAction(android.R.drawable.ic_delete, "Stop",
                createPendingIntent(ACTION_STOP))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, AudioRecordingService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudioMemory::RecordingLock")
        wakeLock?.acquire(8 * 60 * 60 * 1000L) // 8 hours max
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
}
