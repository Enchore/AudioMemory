package com.audiomemory

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AudioMemoryApp : Application(), Configuration.Provider {

    companion object {
        const val RECORDING_CHANNEL_ID = "recording_channel"
        const val PROCESSING_CHANNEL_ID = "processing_channel"
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val recordingChannel = NotificationChannel(
                RECORDING_CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when audio recording is active"
                setShowBadge(false)
            }

            val processingChannel = NotificationChannel(
                PROCESSING_CHANNEL_ID,
                "Processing",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Shows audio processing progress"
                setShowBadge(false)
            }

            manager.createNotificationChannels(listOf(recordingChannel, processingChannel))
        }
    }
}