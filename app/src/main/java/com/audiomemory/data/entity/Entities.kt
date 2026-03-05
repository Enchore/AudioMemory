package com.audiomemory.data.entity

import androidx.room.*

/**
 * Tracks each recording session from start to stop.
 */
@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    val status: SessionStatus = SessionStatus.RECORDING,
    @ColumnInfo(name = "total_chunks") val totalChunks: Int = 0,
)

enum class SessionStatus { RECORDING, PAUSED, COMPLETED }

/**
 * A 30-60 second audio segment split at silence boundaries via VAD.
 */
@Entity(
    tableName = "audio_chunks",
    foreignKeys = [ForeignKey(
        entity = RecordingSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("session_id"), Index("processing_status")]
)
data class AudioChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "file_path") val filePath: String? = null,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "processing_status") val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long = 0,
)

enum class ProcessingStatus {
    PENDING, TRANSCRIBING, DIARIZING, SUMMARIZING, COMPLETED, FAILED
}

/**
 * Transcription output for a single audio chunk.
 */
@Entity(
    tableName = "transcriptions",
    foreignKeys = [ForeignKey(
        entity = AudioChunkEntity::class,
        parentColumns = ["id"],
        childColumns = ["chunk_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("chunk_id")]
)
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "chunk_id") val chunkId: Long,
    val text: String,
    val language: String = "unknown",
    val confidence: Float = 0f,
    val engine: TranscriptionEngine = TranscriptionEngine.SHERPA_ONNX,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

enum class TranscriptionEngine { SHERPA_ONNX, OPENAI_WHISPER }

/**
 * A known speaker with voice fingerprint.
 */
@Entity(tableName = "speakers")
data class SpeakerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "is_owner") val isOwner: Boolean = false,
    @ColumnInfo(name = "embedding_vector") val embeddingVector: FloatArray? = null,
    @ColumnInfo(name = "first_seen") val firstSeen: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_seen") val lastSeen: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "total_segments") val totalSegments: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpeakerEntity) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

/**
 * A time-aligned speech segment attributed to a speaker.
 */
@Entity(
    tableName = "speaker_segments",
    foreignKeys = [
        ForeignKey(entity = AudioChunkEntity::class, parentColumns = ["id"], childColumns = ["chunk_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SpeakerEntity::class, parentColumns = ["id"], childColumns = ["speaker_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("chunk_id"), Index("speaker_id")]
)
data class SpeakerSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "chunk_id") val chunkId: Long,
    @ColumnInfo(name = "speaker_id") val speakerId: Long? = null,
    @ColumnInfo(name = "start_time_ms") val startTimeMs: Long,
    @ColumnInfo(name = "end_time_ms") val endTimeMs: Long,
    val text: String = "",
)

/**
 * An extracted memory — a key piece of information from conversation.
 */
@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(entity = SpeakerEntity::class, parentColumns = ["id"], childColumns = ["speaker_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = AudioChunkEntity::class, parentColumns = ["id"], childColumns = ["source_chunk_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("speaker_id"), Index("source_chunk_id"), Index("type"), Index("is_owner_speech")]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val type: MemoryType,
    @ColumnInfo(name = "importance_score") val importanceScore: Int = 1,
    @ColumnInfo(name = "speaker_id") val speakerId: Long? = null,
    @ColumnInfo(name = "is_owner_speech") val isOwnerSpeech: Boolean = false,
    @ColumnInfo(name = "source_chunk_id") val sourceChunkId: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class MemoryType { FACT, DECISION, ACTION, INSIGHT, PREFERENCE }

/**
 * Tags for categorizing memories.
 */
@Entity(tableName = "memory_tags")
data class MemoryTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/**
 * Many-to-many relationship between Memory and MemoryTag.
 */
@Entity(
    tableName = "memory_tag_cross_ref",
    primaryKeys = ["memory_id", "tag_id"],
    foreignKeys = [
        ForeignKey(entity = MemoryEntity::class, parentColumns = ["id"], childColumns = ["memory_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MemoryTagEntity::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag_id")]
)
data class MemoryTagCrossRef(
    @ColumnInfo(name = "memory_id") val memoryId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

/**
 * Accumulated owner learning profile stored as JSON fields.
 */
@Entity(tableName = "owner_profile")
data class OwnerProfileEntity(
    @PrimaryKey val id: Long = 1, // singleton
    @ColumnInfo(name = "topics_json") val topicsJson: String = "{}",
    @ColumnInfo(name = "preferences_json") val preferencesJson: String = "{}",
    @ColumnInfo(name = "patterns_json") val patternsJson: String = "{}",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * FTS4 virtual table for full-text search on memories.
 */
@Fts4(contentEntity = MemoryEntity::class)
@Entity(tableName = "memories_fts")
data class MemoryFtsEntity(
    val content: String,
)

/**
 * Relation: Memory with its tags.
 */
data class MemoryWithTags(
    @Embedded val memory: MemoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MemoryTagCrossRef::class,
            parentColumn = "memory_id",
            entityColumn = "tag_id"
        )
    )
    val tags: List<MemoryTagEntity>,
)
