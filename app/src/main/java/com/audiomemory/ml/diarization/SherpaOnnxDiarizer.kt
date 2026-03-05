package com.audiomemory.ml.diarization

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.FastClusteringConfig
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
 * On-device speaker diarization using sherpa-onnx.
 *
 * Pipeline:
 * 1. Pyannote segmentation model detects speech segments per speaker
 * 2. Speaker embedding extractor produces d-vectors (192 or 512 dim)
 * 3. Fast clustering groups segments by speaker
 *
 * Model files expected in assets/:
 *   - sherpa-onnx-pyannote-segmentation-3-0/model.onnx     (segmentation)
 *   - 3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx  (embedding)
 */
@Singleton
class SherpaOnnxDiarizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeakerDiarizer {

    companion object {
        private const val TAG = "SherpaOnnxDiarize"
        private const val SAMPLE_RATE = 16000
        private const val SEGMENTATION_MODEL = "sherpa-onnx-pyannote-segmentation-3-0/model.onnx"
        private const val EMBEDDING_MODEL = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
    }

    private var diarizer: OfflineSpeakerDiarization? = null
    private var embeddingExtractor: SpeakerEmbeddingExtractor? = null
    private var isInitialized = false

    @Synchronized
    fun initialize() {
        if (isInitialized) return

        try {
            // Copy model files from assets to internal storage
            val filesToCopy = listOf(SEGMENTATION_MODEL, EMBEDDING_MODEL)
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

            val segModelPath = File(context.filesDir, SEGMENTATION_MODEL).absolutePath
            val embModelPath = File(context.filesDir, EMBEDDING_MODEL).absolutePath

            // Configure segmentation model (Pyannote)
            val pyannoteConfig = OfflineSpeakerSegmentationPyannoteModelConfig(
                model = segModelPath,
            )

            val segConfig = OfflineSpeakerSegmentationModelConfig(
                pyannote = pyannoteConfig,
                numThreads = 2,
                provider = "cpu",
            )

            // Configure speaker embedding extractor
            val embConfig = SpeakerEmbeddingExtractorConfig(
                model = embModelPath,
                numThreads = 2,
                provider = "cpu",
            )

            // Configure clustering
            val clusterConfig = FastClusteringConfig(
                numClusters = -1, // auto-detect number of speakers
                threshold = 0.5f,
            )

            // Create the diarization pipeline
            val diarizationConfig = OfflineSpeakerDiarizationConfig(
                segmentation = segConfig,
                embedding = embConfig,
                clustering = clusterConfig,
                minDurationOn = 0.3f,
                minDurationOff = 0.5f,
            )

            OfflineSpeakerDiarization(assetManager = null, config = diarizationConfig)

            // Also create a standalone embedding extractor for per-segment embeddings
            SpeakerEmbeddingExtractor(assetManager = null, config = embConfig)

            isInitialized = true
            Log.d(TAG, "sherpa-onnx speaker diarization initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize diarization: ${e.message}", e)
            isInitialized = false
        }
    }

    override suspend fun diarize(wavFilePath: String): DiarizationResult {
        return withContext(Dispatchers.Default) {
            initialize()

            val dia = diarizer ?: return@withContext DiarizationResult(emptyList(), 0)
            val extractor = embeddingExtractor ?: return@withContext DiarizationResult(emptyList(), 0)

            try {
                val samples = readWavSamples(wavFilePath)
                if (samples.isEmpty()) {
                    return@withContext DiarizationResult(emptyList(), 0)
                }

                // Run diarization
                val segments = dia.process(samples)

                // Collect unique speaker labels
                val speakerLabels = mutableSetOf<String>()
                val diarizedSegments = mutableListOf<DiarizedSegment>()

                for (seg in segments) {
                    val label = "SPEAKER_%02d".format(seg.speaker)
                    speakerLabels.add(label)

                    // Extract embedding for this segment's audio
                    val startSample = (seg.start * SAMPLE_RATE).toInt().coerceIn(0, samples.size - 1)
                    val endSample = (seg.end * SAMPLE_RATE).toInt().coerceIn(startSample + 1, samples.size)
                    val segmentSamples = samples.copyOfRange(startSample, endSample)

                    val embedding = extractEmbedding(extractor, segmentSamples)

                    diarizedSegments.add(
                        DiarizedSegment(
                            startMs = (seg.start * 1000).toLong(),
                            endMs = (seg.end * 1000).toLong(),
                            speakerLabel = label,
                            embedding = embedding,
                        )
                    )
                }

                Log.d(TAG, "Diarized: ${diarizedSegments.size} segments, ${speakerLabels.size} speakers")

                DiarizationResult(
                    segments = diarizedSegments,
                    numSpeakers = speakerLabels.size,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Diarization failed: ${e.message}", e)
                DiarizationResult(emptyList(), 0)
            }
        }
    }

    /**
     * Extract a speaker embedding vector from audio samples.
     */
    private fun extractEmbedding(extractor: SpeakerEmbeddingExtractor, samples: FloatArray): FloatArray {
        return try {
            val stream = extractor.createStream()
            stream.acceptWaveform(samples, SAMPLE_RATE)
            stream.inputFinished()

            if (extractor.isReady(stream)) {
                extractor.compute(stream)
            } else {
                FloatArray(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Embedding extraction failed: ${e.message}")
            FloatArray(0)
        }
    }

    /**
     * Read a WAV file and return normalized float samples.
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
}
