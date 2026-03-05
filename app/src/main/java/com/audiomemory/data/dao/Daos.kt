package com.audiomemory.data.dao

import androidx.room.*
import com.audiomemory.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    @Insert
    suspend fun insert(session: RecordingSessionEntity): Long

    @Update
    suspend fun update(session: RecordingSessionEntity)

    @Query("SELECT * FROM recording_sessions ORDER BY start_time DESC")
    fun getAllSessions(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE status = 'RECORDING' LIMIT 1")
    suspend fun getActiveSession(): RecordingSessionEntity?

    @Query("UPDATE recording_sessions SET status = :status, end_time = :endTime WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SessionStatus, endTime: Long? = null)

    @Query("UPDATE recording_sessions SET total_chunks = total_chunks + 1 WHERE id = :sessionId")
    suspend fun incrementChunkCount(sessionId: Long)
}

@Dao
interface AudioChunkDao {
    @Insert
    suspend fun insert(chunk: AudioChunkEntity): Long

    @Update
    suspend fun update(chunk: AudioChunkEntity)

    @Query("SELECT * FROM audio_chunks WHERE session_id = :sessionId ORDER BY start_time")
    fun getChunksForSession(sessionId: Long): Flow<List<AudioChunkEntity>>

    @Query("SELECT * FROM audio_chunks WHERE processing_status = :status ORDER BY start_time LIMIT :limit")
    suspend fun getChunksByStatus(status: ProcessingStatus, limit: Int = 10): List<AudioChunkEntity>

    @Query("UPDATE audio_chunks SET processing_status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ProcessingStatus)

    @Query("UPDATE audio_chunks SET file_path = NULL WHERE id = :id")
    suspend fun clearFilePath(id: Long)

    @Query("SELECT COUNT(*) FROM audio_chunks WHERE processing_status = 'PENDING'")
    fun getPendingCount(): Flow<Int>
}

@Dao
interface TranscriptionDao {
    @Insert
    suspend fun insert(transcription: TranscriptionEntity): Long

    @Query("SELECT * FROM transcriptions WHERE chunk_id = :chunkId")
    suspend fun getForChunk(chunkId: Long): TranscriptionEntity?

    @Query("SELECT * FROM transcriptions ORDER BY created_at DESC LIMIT :limit")
    fun getRecent(limit: Int = 20): Flow<List<TranscriptionEntity>>
}

@Dao
interface SpeakerDao {
    @Insert
    suspend fun insert(speaker: SpeakerEntity): Long

    @Update
    suspend fun update(speaker: SpeakerEntity)

    @Query("SELECT * FROM speakers ORDER BY is_owner DESC, total_segments DESC")
    fun getAllSpeakers(): Flow<List<SpeakerEntity>>

    @Query("SELECT * FROM speakers WHERE is_owner = 1 LIMIT 1")
    suspend fun getOwner(): SpeakerEntity?

    @Query("SELECT * FROM speakers WHERE id = :id")
    suspend fun getById(id: Long): SpeakerEntity?

    @Query("SELECT * FROM speakers WHERE is_owner = 0")
    suspend fun getNonOwnerSpeakers(): List<SpeakerEntity>

    @Query("UPDATE speakers SET total_segments = total_segments + 1, last_seen = :timestamp WHERE id = :id")
    suspend fun incrementSegments(id: Long, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(speaker: SpeakerEntity)

    @Query("UPDATE speakers SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)
}

@Dao
interface SpeakerSegmentDao {
    @Insert
    suspend fun insertAll(segments: List<SpeakerSegmentEntity>)

    @Query("SELECT * FROM speaker_segments WHERE chunk_id = :chunkId ORDER BY start_time_ms")
    suspend fun getForChunk(chunkId: Long): List<SpeakerSegmentEntity>
}

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Insert
    suspend fun insertTag(tag: MemoryTagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: MemoryTagCrossRef)

    @Transaction
    @Query("SELECT * FROM memories ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMemories(limit: Int = 50): Flow<List<MemoryWithTags>>

    @Transaction
    @Query("SELECT * FROM memories WHERE type = :type ORDER BY timestamp DESC")
    fun getMemoriesByType(type: MemoryType): Flow<List<MemoryWithTags>>

    @Transaction
    @Query("SELECT * FROM memories WHERE is_owner_speech = 1 ORDER BY timestamp DESC")
    fun getOwnerMemories(): Flow<List<MemoryWithTags>>

    @Transaction
    @Query("SELECT * FROM memories WHERE speaker_id = :speakerId ORDER BY timestamp DESC")
    fun getMemoriesBySpeaker(speakerId: Long): Flow<List<MemoryWithTags>>

    @Query("""
        SELECT memories.* FROM memories
        JOIN memories_fts ON memories.rowid = memories_fts.rowid
        WHERE memories_fts MATCH :query
        ORDER BY memories.timestamp DESC
    """)
    fun searchMemories(query: String): Flow<List<MemoryEntity>>

    @Query("SELECT COUNT(*) FROM memories")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM memories WHERE is_owner_speech = 1")
    fun getOwnerMemoryCount(): Flow<Int>

    @Query("SELECT * FROM memory_tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): MemoryTagEntity?
}

@Dao
interface OwnerProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: OwnerProfileEntity)

    @Query("SELECT * FROM owner_profile WHERE id = 1")
    suspend fun getProfile(): OwnerProfileEntity?

    @Query("SELECT * FROM owner_profile WHERE id = 1")
    fun getProfileFlow(): Flow<OwnerProfileEntity?>
}
