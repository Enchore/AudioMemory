package com.audiomemory.data.repository

import com.audiomemory.data.dao.*
import com.audiomemory.data.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val sessionDao: RecordingSessionDao,
    private val chunkDao: AudioChunkDao,
    private val transcriptionDao: TranscriptionDao,
    private val speakerDao: SpeakerDao,
    private val segmentDao: SpeakerSegmentDao,
    private val memoryDao: MemoryDao,
    private val ownerProfileDao: OwnerProfileDao,
) {
    // ---- Recording Sessions ----
    suspend fun startSession(): Long {
        return sessionDao.insert(RecordingSessionEntity(startTime = System.currentTimeMillis()))
    }

    suspend fun endSession(sessionId: Long) {
        sessionDao.updateStatus(sessionId, SessionStatus.COMPLETED, System.currentTimeMillis())
    }

    suspend fun pauseSession(sessionId: Long) {
        sessionDao.updateStatus(sessionId, SessionStatus.PAUSED)
    }

    suspend fun getActiveSession() = sessionDao.getActiveSession()
    fun getAllSessions() = sessionDao.getAllSessions()

    // ---- Audio Chunks ----
    suspend fun insertChunk(chunk: AudioChunkEntity): Long {
        val id = chunkDao.insert(chunk)
        sessionDao.incrementChunkCount(chunk.sessionId)
        return id
    }

    suspend fun updateChunkStatus(chunkId: Long, status: ProcessingStatus) {
        chunkDao.updateStatus(chunkId, status)
    }

    suspend fun getPendingChunks(limit: Int = 5) =
        chunkDao.getChunksByStatus(ProcessingStatus.PENDING, limit)

    fun getPendingChunkCount() = chunkDao.getPendingCount()

    suspend fun clearChunkFile(chunkId: Long) {
        chunkDao.clearFilePath(chunkId)
    }

    // ---- Transcriptions ----
    suspend fun insertTranscription(transcription: TranscriptionEntity): Long {
        return transcriptionDao.insert(transcription)
    }

    suspend fun getTranscriptionForChunk(chunkId: Long) = transcriptionDao.getForChunk(chunkId)

    // ---- Speakers ----
    fun getAllSpeakers() = speakerDao.getAllSpeakers()
    suspend fun getOwner() = speakerDao.getOwner()
    suspend fun getSpeakerById(id: Long) = speakerDao.getById(id)
    suspend fun getNonOwnerSpeakers() = speakerDao.getNonOwnerSpeakers()

    suspend fun registerOwner(embedding: FloatArray): Long {
        // Delete any existing owner record
        val existing = speakerDao.getOwner()
        if (existing != null) {
            speakerDao.delete(existing)
        }
        return speakerDao.insert(
            SpeakerEntity(
                name = "Owner",
                isOwner = true,
                embeddingVector = embedding,
            )
        )
    }

    suspend fun updateSpeakerName(id: Long, name: String) = speakerDao.updateName(id, name)
    suspend fun deleteSpeaker(speaker: SpeakerEntity) = speakerDao.delete(speaker)

    suspend fun insertSpeaker(speaker: SpeakerEntity): Long = speakerDao.insert(speaker)
    suspend fun updateSpeaker(speaker: SpeakerEntity) = speakerDao.update(speaker)

    suspend fun insertSpeakerSegments(segments: List<SpeakerSegmentEntity>) {
        segmentDao.insertAll(segments)
        segments.mapNotNull { it.speakerId }.distinct().forEach { speakerId ->
            speakerDao.incrementSegments(speakerId)
        }
    }

    // ---- Memories ----
    fun getRecentMemories(limit: Int = 50) = memoryDao.getRecentMemories(limit)
    fun getMemoriesByType(type: MemoryType) = memoryDao.getMemoriesByType(type)
    fun getOwnerMemories() = memoryDao.getOwnerMemories()
    fun getMemoriesBySpeaker(speakerId: Long) = memoryDao.getMemoriesBySpeaker(speakerId)
    fun searchMemories(query: String) = memoryDao.searchMemories(query)
    fun getTotalMemoryCount() = memoryDao.getTotalCount()
    fun getOwnerMemoryCount() = memoryDao.getOwnerMemoryCount()

    suspend fun insertMemoryWithTags(memory: MemoryEntity, tagNames: List<String>): Long {
        val memoryId = memoryDao.insert(memory)
        tagNames.forEach { tagName ->
            val tag = memoryDao.getTagByName(tagName)
                ?: MemoryTagEntity(name = tagName).let { memoryDao.insertTag(it); memoryDao.getTagByName(tagName)!! }
            memoryDao.insertCrossRef(MemoryTagCrossRef(memoryId, tag.id))
        }
        return memoryId
    }

    // ---- Owner Profile ----
    suspend fun getOwnerProfile() = ownerProfileDao.getProfile()
    fun getOwnerProfileFlow() = ownerProfileDao.getProfileFlow()

    suspend fun updateOwnerProfile(profile: OwnerProfileEntity) {
        ownerProfileDao.upsert(profile.copy(updatedAt = System.currentTimeMillis()))
    }
}
