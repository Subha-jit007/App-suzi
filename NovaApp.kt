package com.nova.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NovaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Voice listener channel
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_VOICE,
                    "NOVA Voice Listener",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "NOVA is listening for your voice commands"
                    setShowBadge(false)
                }
            )

            // Command results channel
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_COMMANDS,
                    "NOVA Commands",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Results from NOVA commands"
                }
            )
        }
    }

    companion object {
        const val CHANNEL_VOICE = "nova_voice_channel"
        const val CHANNEL_COMMANDS = "nova_commands_channel"
    }
}
