package com.audiomemory.ml.diarization

/**
 * Abstraction for speaker diarization — segmenting audio by who spoke when.
 *
 * Implementation uses sherpa-onnx speaker diarization model.
 */
interface SpeakerDiarizer {
    /**
     * Run speaker diarization on a WAV file.
     * @param wavFilePath Path to 16kHz mono PCM WAV
     * @return List of diarized segments, each with speaker embedding and timestamps
     */
    suspend fun diarize(wavFilePath: String): DiarizationResult
}

data class DiarizationResult(
    val segments: List<DiarizedSegment>,
    val numSpeakers: Int,
)

data class DiarizedSegment(
    val startMs: Long,
    val endMs: Long,
    val speakerLabel: String, // "SPEAKER_00", "SPEAKER_01", etc.
    val embedding: FloatArray, // d-vector for this speaker
) {
    override fun equals(other: Any?) = false
    override fun hashCode() = startMs.hashCode()
}
