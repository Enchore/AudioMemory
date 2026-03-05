package com.audiomemory.ui.speaker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiomemory.data.entity.SpeakerEntity
import com.audiomemory.data.repository.MemoryRepository
import com.audiomemory.ml.speaker.SpeakerMatcher
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class SpeakerViewModel @Inject constructor(
    private val repository: MemoryRepository,
    private val speakerMatcher: SpeakerMatcher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val TAG = "SpeakerVM"
        private const val SAMPLE_RATE = 16000
        private const val ENROLLMENT_DURATION_MS = 10_000L
        private const val EMBEDDING_MODEL = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
        private const val MIN_SPEECH_ENERGY = 0.005f // minimum RMS energy to consider as speech
    }

    val speakers: StateFlow<List<SpeakerEntity>> =
        repository.getAllSpeakers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Enrollment state
    private val _enrollmentState = MutableStateFlow<EnrollmentState>(EnrollmentState.Idle)
    val enrollmentState: StateFlow<EnrollmentState> = _enrollmentState.asStateFlow()

    private val _enrollmentProgress = MutableStateFlow(0f)
    val enrollmentProgress: StateFlow<Float> = _enrollmentProgress.asStateFlow()

    private val _enrollmentSecondsLeft = MutableStateFlow(10)
    val enrollmentSecondsLeft: StateFlow<Int> = _enrollmentSecondsLeft.asStateFlow()

    private var enrollmentJob: Job? = null

    fun startOwnerEnrollment() {
        if (_enrollmentState.value is EnrollmentState.Recording) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _enrollmentState.value = EnrollmentState.Error("Microphone permission required")
            return
        }

        enrollmentJob = viewModelScope.launch {
            try {
                _enrollmentState.value = EnrollmentState.Recording
                _enrollmentProgress.value = 0f
                _enrollmentSecondsLeft.value = 10

                val samples = recordAudio()

                if (samples.isEmpty()) {
                    _enrollmentState.value = EnrollmentState.Error("Recording failed")
                    return@launch
                }

                // Check if the recording contains actual speech
                val rmsEnergy = kotlin.math.sqrt(samples.map { it * it }.average().toFloat())
                Log.d(TAG, "Recording RMS energy: $rmsEnergy")
                if (rmsEnergy < MIN_SPEECH_ENERGY) {
                    _enrollmentState.value = EnrollmentState.Error("No speech detected. Please speak clearly and try again.")
                    return@launch
                }

                _enrollmentState.value = EnrollmentState.Processing

                val embedding = extractEmbedding(samples)

                if (embedding == null || embedding.isEmpty()) {
                    _enrollmentState.value = EnrollmentState.Error("Voice extraction failed. Try again.")
                    return@launch
                }

                repository.registerOwner(embedding)
                _enrollmentState.value = EnrollmentState.Success
                Log.d(TAG, "Owner enrolled, embedding dim=${embedding.size}")

                delay(2000)
                _enrollmentState.value = EnrollmentState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Enrollment failed: ${e.message}", e)
                _enrollmentState.value = EnrollmentState.Error("Enrollment failed: ${e.message}")
            }
        }
    }

    fun cancelEnrollment() {
        enrollmentJob?.cancel()
        _enrollmentState.value = EnrollmentState.Idle
        _enrollmentProgress.value = 0f
    }

    fun dismissError() {
        _enrollmentState.value = EnrollmentState.Idle
    }

    fun updateSpeakerName(speakerId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateSpeakerName(speakerId, newName.trim())
        }
    }

    fun deleteSpeaker(speaker: SpeakerEntity) {
        viewModelScope.launch {
            repository.deleteSpeaker(speaker)
        }
    }

    private suspend fun recordAudio(): FloatArray = withContext(Dispatchers.IO) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)
            .coerceAtLeast(SAMPLE_RATE * 2)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                channelConfig, audioFormat, bufferSize,
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "No mic permission", e)
            return@withContext floatArrayOf()
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed")
            recorder.release()
            return@withContext floatArrayOf()
        }

        val totalSamples = SAMPLE_RATE * (ENROLLMENT_DURATION_MS / 1000).toInt()
        val allSamples = FloatArray(totalSamples)
        var collected = 0
        val readBuf = ShortArray(bufferSize / 2)

        recorder.startRecording()
        val startTime = System.currentTimeMillis()

        try {
            while (collected < totalSamples) {
                val read = recorder.read(readBuf, 0, readBuf.size.coerceAtMost(totalSamples - collected))
                if (read > 0) {
                    for (i in 0 until read) {
                        allSamples[collected + i] = readBuf[i].toFloat() / 32768f
                    }
                    collected += read
                }
                val elapsed = System.currentTimeMillis() - startTime
                _enrollmentProgress.value = (elapsed.toFloat() / ENROLLMENT_DURATION_MS).coerceIn(0f, 1f)
                _enrollmentSecondsLeft.value = ((ENROLLMENT_DURATION_MS - elapsed) / 1000).toInt().coerceAtLeast(0)
            }
        } finally {
            recorder.stop()
            recorder.release()
        }

        Log.d(TAG, "Recorded $collected samples")
        if (collected < totalSamples) allSamples.copyOf(collected) else allSamples
    }

    private suspend fun extractEmbedding(samples: FloatArray): FloatArray? = withContext(Dispatchers.IO) {
        try {
            val destFile = File(context.filesDir, EMBEDDING_MODEL)
            if (!destFile.exists()) {
                destFile.parentFile?.mkdirs()
                context.assets.open(EMBEDDING_MODEL).use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
            }

            val config = SpeakerEmbeddingExtractorConfig(
                model = destFile.absolutePath, numThreads = 2, provider = "cpu",
            )
            val extractor = SpeakerEmbeddingExtractor(assetManager = null, config = config)

            val segmentLength = samples.size / 3
            val embeddings = mutableListOf<FloatArray>()

            for (i in 0 until 3) {
                val start = i * segmentLength
                val end = if (i == 2) samples.size else start + segmentLength
                val segment = samples.copyOfRange(start, end)

                val stream = extractor.createStream()
                stream.acceptWaveform(segment, SAMPLE_RATE)
                stream.inputFinished()

                if (extractor.isReady(stream)) {
                    val emb = extractor.compute(stream)
                    if (emb.isNotEmpty()) embeddings.add(emb)
                }
            }

            extractor.release()

            if (embeddings.isEmpty()) {
                Log.e(TAG, "No embeddings extracted")
                return@withContext null
            }

            speakerMatcher.averageEmbeddings(embeddings)
        } catch (e: Exception) {
            Log.e(TAG, "Embedding extraction failed: ${e.message}", e)
            null
        }
    }
}

sealed class EnrollmentState {
    data object Idle : EnrollmentState()
    data object Recording : EnrollmentState()
    data object Processing : EnrollmentState()
    data object Success : EnrollmentState()
    data class Error(val message: String) : EnrollmentState()
}
