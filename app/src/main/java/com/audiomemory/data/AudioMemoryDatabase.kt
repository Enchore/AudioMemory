package com.audiomemory.data

import androidx.room.*
import com.audiomemory.data.dao.*
import com.audiomemory.data.entity.*

@Database(
    entities = [
        RecordingSessionEntity::class,
        AudioChunkEntity::class,
        TranscriptionEntity::class,
        SpeakerEntity::class,
        SpeakerSegmentEntity::class,
        MemoryEntity::class,
        MemoryTagEntity::class,
        MemoryTagCrossRef::class,
        OwnerProfileEntity::class,
        MemoryFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AudioMemoryDatabase : RoomDatabase() {
    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun speakerDao(): SpeakerDao
    abstract fun speakerSegmentDao(): SpeakerSegmentDao
    abstract fun memoryDao(): MemoryDao
    abstract fun ownerProfileDao(): OwnerProfileDao
}

/**
 * Room type converters for enums and FloatArray.
 */
class Converters {
    // SessionStatus
    @TypeConverter fun fromSessionStatus(v: SessionStatus): String = v.name
    @TypeConverter fun toSessionStatus(v: String): SessionStatus = SessionStatus.valueOf(v)

    // ProcessingStatus
    @TypeConverter fun fromProcessingStatus(v: ProcessingStatus): String = v.name
    @TypeConverter fun toProcessingStatus(v: String): ProcessingStatus = ProcessingStatus.valueOf(v)

    // TranscriptionEngine
    @TypeConverter fun fromEngine(v: TranscriptionEngine): String = v.name
    @TypeConverter fun toEngine(v: String): TranscriptionEngine = TranscriptionEngine.valueOf(v)

    // MemoryType
    @TypeConverter fun fromMemoryType(v: MemoryType): String = v.name
    @TypeConverter fun toMemoryType(v: String): MemoryType = MemoryType.valueOf(v)

    // FloatArray (speaker embeddings stored as comma-separated string)
    @TypeConverter
    fun fromFloatArray(v: FloatArray?): String? = v?.joinToString(",") { it.toString() }

    @TypeConverter
    fun toFloatArray(v: String?): FloatArray? =
        v?.takeIf { it.isNotBlank() }?.split(",")?.map { it.toFloat() }?.toFloatArray()
}
