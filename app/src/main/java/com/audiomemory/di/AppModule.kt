package com.audiomemory.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.audiomemory.data.AudioMemoryDatabase
import com.audiomemory.data.dao.*
import com.audiomemory.ml.diarization.SherpaOnnxDiarizer
import com.audiomemory.ml.diarization.SpeakerDiarizer
import com.audiomemory.service.processing.PipelineOrchestrator
import com.audiomemory.util.ApiConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AudioMemoryDatabase {
        return Room.databaseBuilder(
            context,
            AudioMemoryDatabase::class.java,
            "audio_memory.db"
        ).build()
    }

    @Provides fun provideRecordingSessionDao(db: AudioMemoryDatabase): RecordingSessionDao = db.recordingSessionDao()
    @Provides fun provideAudioChunkDao(db: AudioMemoryDatabase): AudioChunkDao = db.audioChunkDao()
    @Provides fun provideTranscriptionDao(db: AudioMemoryDatabase): TranscriptionDao = db.transcriptionDao()
    @Provides fun provideSpeakerDao(db: AudioMemoryDatabase): SpeakerDao = db.speakerDao()
    @Provides fun provideSpeakerSegmentDao(db: AudioMemoryDatabase): SpeakerSegmentDao = db.speakerSegmentDao()
    @Provides fun provideMemoryDao(db: AudioMemoryDatabase): MemoryDao = db.memoryDao()
    @Provides fun provideOwnerProfileDao(db: AudioMemoryDatabase): OwnerProfileDao = db.ownerProfileDao()

    @Provides
    @Singleton
    fun provideApiConfig(@ApplicationContext context: Context): ApiConfig = ApiConfig(context)

    @Provides
    @Singleton
    fun provideSpeakerDiarizer(@ApplicationContext context: Context): SpeakerDiarizer = SherpaOnnxDiarizer(context)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun providePipelineOrchestrator(workManager: WorkManager): PipelineOrchestrator = PipelineOrchestrator(workManager)
}
