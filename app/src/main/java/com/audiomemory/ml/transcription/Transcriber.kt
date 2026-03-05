package com.audiomemory.ml.transcription

/**
 * Abstraction for speech-to-text engines.
 * Implementations: SherpaOnnxTranscriber (on-device), OpenAiWhisperTranscriber (cloud).
 */
interface Transcriber {
    /**
     * Transcribe audio from a WAV file.
     * @param wavFilePath Path to 16kHz mono PCM WAV file
     * @return TranscriptionResult with text, language, and confidence
     */
    suspend fun transcribe(wavFilePath: String): TranscriptionResult
}

data class TranscriptionResult(
    val text: String,
    val language: String,
    val confidence: Float,
    val segments: List<TranscriptionSegment> = emptyList(),
)

data class TranscriptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)
