package com.audiomemory.ml.transcription

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device transcription using sherpa-onnx Whisper model.
 *
 * Supports Chinese + English with automatic language detection.
 *
 * Model files expected in assets/:
 *   - sherpa-onnx-whisper-tiny/tiny-encoder.int8.onnx
 *   - sherpa-onnx-whisper-tiny/tiny-decoder.int8.onnx
 *   - sherpa-onnx-whisper-tiny/tiny-tokens.txt
 */
@Singleton
class SherpaOnnxTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
) : Transcriber {

    companion object {
        private const val TAG = "SherpaOnnxSTT"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "sherpa-onnx-whisper-tiny"
    }

    private var recognizer: OfflineRecognizer? = null
    private var isInitialized = false

    /**
     * Copy model files from assets to internal storage and initialize the recognizer.
     */
    @Synchronized
    fun initialize() {
        if (isInitialized) return

        try {
            val filesToCopy = listOf(
                "$MODEL_DIR/tiny-encoder.int8.onnx",
                "$MODEL_DIR/tiny-decoder.int8.onnx",
                "$MODEL_DIR/tiny-tokens.txt",
            )
            for (assetPath in filesToCopy) {
                val destFile = File(context.filesDir, assetPath)
                if (!destFile.exists()) {
                    destFile.parentFile?.mkdirs()
                    context.assets.open(assetPath).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            val encoderPath = File(context.filesDir, "$MODEL_DIR/tiny-encoder.int8.onnx").absolutePath
            val decoderPath = File(context.filesDir, "$MODEL_DIR/tiny-decoder.int8.onnx").absolutePath
            val tokensPath = File(context.filesDir, "$MODEL_DIR/tiny-tokens.txt").absolutePath

            val whisper = OfflineWhisperModelConfig(
                encoder = encoderPath,
                decoder = decoderPath,
                language = "",
                task = "transcribe",
            )

            val modelConfig = OfflineModelConfig(
                whisper = whisper,
                tokens = tokensPath,
                numThreads = 2,
                provider = "cpu",
                modelType = "whisper",
            )

            val config = OfflineRecognizerConfig(
                modelConfig = modelConfig,
                decodingMethod = "greedy_search",
            )

            OfflineRecognizer(assetManager = null, config = config)
            isInitialized = true
            Log.d(TAG, "sherpa-onnx Whisper recognizer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize sherpa-onnx: ${e.message}", e)
            isInitialized = false
        }
    }

    override suspend fun transcribe(wavFilePath: String): TranscriptionResult {
        return withContext(Dispatchers.Default) {
            initialize()

            val rec = recognizer ?: return@withContext TranscriptionResult(
                text = "", language = "unknown", confidence = 0f,
            )

            try {
                val samples = readWavSamples(wavFilePath)
                if (samples.isEmpty()) {
                    return@withContext TranscriptionResult("", "unknown", 0f)
                }

                val stream = rec.createStream()
                stream.acceptWaveform(samples, SAMPLE_RATE)
                rec.decode(stream)

                val result = rec.getResult(stream)
                val text = result.text.trim()

                val confidence = when {
                    text.isEmpty() -> 0f
                    text.length < 5 -> 0.5f
                    else -> 0.8f
                }

                val language = detectLanguage(text)

                Log.d(TAG, "Transcribed ${samples.size} samples -> '${text.take(80)}' (lang=$language, conf=$confidence)")

                TranscriptionResult(
                    text = text,
                    language = language,
                    confidence = confidence,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed: ${e.message}", e)
                TranscriptionResult("", "unknown", 0f)
            }
        }
    }

    /**
     * Read a WAV file and return normalized float samples in [-1, 1].
     */
    private fun readWavSamples(wavFilePath: String): FloatArray {
        val file = File(wavFilePath)
        if (!file.exists()) return floatArrayOf()

        val bytes = file.readBytes()
        if (bytes.size < 44) return floatArrayOf()

        val dataSize = bytes.size - 44
        val numSamples = dataSize / 2
        val samples = FloatArray(numSamples)
        val buffer = ByteBuffer.wrap(bytes, 44, dataSize).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            samples[i] = buffer.short.toFloat() / 32768f
        }

        return samples
    }

    /**
     * Simple language detection based on Unicode ranges.
     */
    private fun detectLanguage(text: String): String {
        val chineseCount = text.count { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
        val totalChars = text.count { !it.isWhitespace() }
        if (totalChars == 0) return "unknown"
        val chineseRatio = chineseCount.toFloat() / totalChars
        return when {
            chineseRatio > 0.3f -> "zh"
            else -> "en"
        }
    }
}
