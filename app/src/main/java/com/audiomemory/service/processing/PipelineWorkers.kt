package com.audiomemory.service.processing

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.audiomemory.data.entity.*
import com.audiomemory.data.repository.MemoryRepository
import com.audiomemory.ml.diarization.SpeakerDiarizer
import com.audiomemory.ml.speaker.SpeakerCandidate
import com.audiomemory.ml.speaker.SpeakerMatcher
import com.audiomemory.ml.transcription.SherpaOnnxTranscriber
import com.audiomemory.ml.transcription.OpenAiWhisperTranscriber
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.UUID

/**
 * Orchestrates the 4-stage processing pipeline for each audio chunk.
 * Enqueues a WorkManager chain: Transcribe → Diarize → Summarize → Cleanup
 */
class PipelineOrchestrator(
    private val workManager: WorkManager,
) {
    /**
     * Enqueue processing for a new audio chunk.
     */
    fun enqueueProcessing(chunkId: Long) {
        val inputData = workDataOf("chunk_id" to chunkId)

        val transcribeWork = OneTimeWorkRequestBuilder<TranscribeWorker>()
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val diarizeWork = OneTimeWorkRequestBuilder<DiarizeWorker>()
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val summarizeWork = OneTimeWorkRequestBuilder<SummarizeWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val cleanupWork = OneTimeWorkRequestBuilder<CleanupWorker>()
            .build()

        workManager.beginUniqueWork(
            "process_chunk_$chunkId",
            ExistingWorkPolicy.KEEP,
            transcribeWork,
        )
            .then(diarizeWork)
            .then(summarizeWork)
            .then(cleanupWork)
            .enqueue()
    }
}

// ==========================================
// Stage 1: Transcription
// ==========================================
@HiltWorker
class TranscribeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MemoryRepository,
    private val sherpaTranscriber: SherpaOnnxTranscriber,
    private val whisperTranscriber: OpenAiWhisperTranscriber,
) : CoroutineWorker(context, params) {

    companion object {
        const val CONFIDENCE_THRESHOLD = 0.7f
    }

    override suspend fun doWork(): Result {
        val chunkId = inputData.getLong("chunk_id", -1)
        if (chunkId < 0) return Result.failure()

        repository.updateChunkStatus(chunkId, ProcessingStatus.TRANSCRIBING)

        return try {
            // Try on-device first
            val chunks = repository.getPendingChunks(1)
            val chunk = chunks.firstOrNull() ?: return Result.failure()
            val filePath = chunk.filePath ?: return Result.failure()

            var result = sherpaTranscriber.transcribe(filePath)

            // Fallback to cloud if confidence is low
            if (result.confidence < CONFIDENCE_THRESHOLD && result.text.isNotBlank()) {
                result = whisperTranscriber.transcribe(filePath)
            }

            if (result.text.isBlank()) {
                // No speech detected — mark completed and skip further processing
                repository.updateChunkStatus(chunkId, ProcessingStatus.COMPLETED)
                return Result.success(workDataOf("chunk_id" to chunkId, "skip" to true))
            }

            val engine = if (result.confidence >= CONFIDENCE_THRESHOLD)
                TranscriptionEngine.SHERPA_ONNX else TranscriptionEngine.OPENAI_WHISPER

            repository.insertTranscription(
                TranscriptionEntity(
                    chunkId = chunkId,
                    text = result.text,
                    language = result.language,
                    confidence = result.confidence,
                    engine = engine,
                )
            )

            Result.success(workDataOf("chunk_id" to chunkId))
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else {
                repository.updateChunkStatus(chunkId, ProcessingStatus.FAILED)
                Result.failure()
            }
        }
    }
}

// ==========================================
// Stage 2: Speaker Diarization
// ==========================================
@HiltWorker
class DiarizeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MemoryRepository,
    private val diarizer: SpeakerDiarizer,
    private val speakerMatcher: SpeakerMatcher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val chunkId = inputData.getLong("chunk_id", -1)
        val skip = inputData.getBoolean("skip", false)
        if (chunkId < 0 || skip) return Result.success(inputData)

        repository.updateChunkStatus(chunkId, ProcessingStatus.DIARIZING)

        return try {
            val chunks = repository.getPendingChunks(1)
            val chunk = chunks.firstOrNull() ?: return Result.failure()
            val filePath = chunk.filePath ?: return Result.failure()

            val diarizationResult = diarizer.diarize(filePath)

            // Build known speaker candidates
            val owner = repository.getOwner()
            val otherSpeakers = repository.getNonOwnerSpeakers()
            val candidates = buildList {
                owner?.embeddingVector?.let { emb ->
                    add(SpeakerCandidate(owner.id, owner.name, true, emb))
                }
                otherSpeakers.forEach { s ->
                    s.embeddingVector?.let { emb ->
                        add(SpeakerCandidate(s.id, s.name, false, emb))
                    }
                }
            }

            // Match each diarized segment to a known speaker or create new
            val segments = mutableListOf<SpeakerSegmentEntity>()
            val newSpeakerCache = mutableMapOf<String, Long>() // diarization label → DB speaker ID

            for (seg in diarizationResult.segments) {
                val match = speakerMatcher.findBestMatch(seg.embedding, candidates)

                val speakerId = if (match != null) {
                    match.speakerId
                } else {
                    // Check if we already created a speaker for this diarization label
                    newSpeakerCache[seg.speakerLabel] ?: run {
                        val newId = repository.insertSpeaker(
                            SpeakerEntity(
                                name = "Speaker ${('A'.code + newSpeakerCache.size).toChar()}",
                                embeddingVector = seg.embedding,
                            )
                        )
                        newSpeakerCache[seg.speakerLabel] = newId
                        newId
                    }
                }

                segments.add(
                    SpeakerSegmentEntity(
                        chunkId = chunkId,
                        speakerId = speakerId,
                        startTimeMs = seg.startMs,
                        endTimeMs = seg.endMs,
                    )
                )
            }

            repository.insertSpeakerSegments(segments)
            Result.success(workDataOf("chunk_id" to chunkId))
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else {
                repository.updateChunkStatus(chunkId, ProcessingStatus.FAILED)
                Result.failure()
            }
        }
    }
}

// ==========================================
// Stage 3: Summarize + Memory Extraction
// ==========================================
@HiltWorker
class SummarizeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MemoryRepository,
    private val extractor: GptMemoryExtractor,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val chunkId = inputData.getLong("chunk_id", -1)
        val skip = inputData.getBoolean("skip", false)
        if (chunkId < 0 || skip) return Result.success(inputData)

        repository.updateChunkStatus(chunkId, ProcessingStatus.SUMMARIZING)

        return try {
            val transcription = repository.getTranscriptionForChunk(chunkId)
                ?: return Result.failure()

            // Build speaker-annotated transcript
            // For MVP, use basic transcription text without full speaker alignment
            val result = extractor.extract(
                transcription = transcription.text,
                speakerSegments = emptyList(), // TODO: align transcription with speaker segments
            )

            // Save extracted memories
            for (mem in result.memories) {
                val memoryType = try { MemoryType.valueOf(mem.type) } catch (_: Exception) { MemoryType.FACT }

                repository.insertMemoryWithTags(
                    memory = MemoryEntity(
                        content = mem.content,
                        type = memoryType,
                        importanceScore = mem.importanceScore.coerceIn(1, 5),
                        isOwnerSpeech = mem.isOwnerSpeech,
                        sourceChunkId = chunkId,
                    ),
                    tagNames = mem.tags,
                )
            }

            // Update owner profile with insights
            if (result.ownerInsights.isNotEmpty()) {
                updateOwnerProfile(result.ownerInsights)
            }

            Result.success(workDataOf("chunk_id" to chunkId))
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else {
                repository.updateChunkStatus(chunkId, ProcessingStatus.FAILED)
                Result.failure()
            }
        }
    }

    private suspend fun updateOwnerProfile(insights: List<OwnerInsight>) {
        val profile = repository.getOwnerProfile() ?: OwnerProfileEntity()
        // TODO: Merge insights into profile JSON fields
        // For MVP, append insights as simple JSON entries
        repository.updateOwnerProfile(profile)
    }
}

// ==========================================
// Stage 4: Cleanup
// ==========================================
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MemoryRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val chunkId = inputData.getLong("chunk_id", -1)
        if (chunkId < 0) return Result.success()

        return try {
            // Delete the audio file from disk
            val chunks = repository.getPendingChunks(1)
            val chunk = chunks.firstOrNull()
            chunk?.filePath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }

            // Clear file path in DB and mark completed
            repository.clearChunkFile(chunkId)
            repository.updateChunkStatus(chunkId, ProcessingStatus.COMPLETED)

            Result.success()
        } catch (e: Exception) {
            // Cleanup failure is non-critical — mark completed anyway
            repository.updateChunkStatus(chunkId, ProcessingStatus.COMPLETED)
            Result.success()
        }
    }
}
