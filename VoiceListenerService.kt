package com.nova.ai.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nova.ai.NovaApp
import com.nova.ai.R
import com.nova.ai.commands.CommandExecutor
import com.nova.ai.commands.IntentParser
import com.nova.ai.models.Language
import com.nova.ai.ui.MainActivity
import com.nova.ai.utils.SecureStorage
import com.nova.ai.voice.SpeechEngine
import com.nova.ai.voice.TextToSpeechEngine
import com.nova.ai.voice.WakeWordDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Always-on foreground service.
 * Lifecycle: START_STICKY — restarts automatically if killed.
 * Handles wake word → full recognition → command execution.
 */
@AndroidEntryPoint
class VoiceListenerService : Service() {

    @Inject lateinit var wakeWordDetector: WakeWordDetector
    @Inject lateinit var speechEngine: SpeechEngine
    @Inject lateinit var ttsEngine: TextToSpeechEngine
    @Inject lateinit var intentParser: IntentParser
    @Inject lateinit var commandExecutor: CommandExecutor
    @Inject lateinit var storage: SecureStorage

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isFullListening = false

    companion object {
        const val ACTION_STOP = "com.nova.ai.STOP_SERVICE"
        const val ACTION_FULL_LISTEN = "com.nova.ai.FULL_LISTEN"
        const val NOTIFICATION_ID = 1001

        fun startIntent(context: android.content.Context) =
            Intent(context, VoiceListenerService::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("NOVA is listening..."))
        initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_FULL_LISTEN -> triggerFullListen("")
        }
        return START_STICKY
    }

    private fun initialize() {
        // Init engines
        ttsEngine.initialize {
            ttsEngine.speak("NOVA is ready.")
        }
        speechEngine.initialize()

        // Always-listening mode
        if (storage.getBoolean(SecureStorage.KEY_ALWAYS_LISTENING, true)) {
            startWakeWordMode()
        }

        // Collect speech results
        scope.launch {
            speechEngine.results.collect { result ->
                if (result.isFinal && result.transcript.isNotBlank()) {
                    processVoiceInput(result.transcript)
                }
            }
        }
    }

    private fun startWakeWordMode() {
        wakeWordDetector.start()

        scope.launch {
            wakeWordDetector.wakeWordDetected.collect { commandAfterWake ->
                updateNotification("NOVA heard you!")
                if (commandAfterWake.isNotBlank()) {
                    // Command was given together with wake word
                    processVoiceInput(commandAfterWake)
                } else {
                    // Wake word only — start full listening for command
                    triggerFullListen("")
                }
            }
        }
    }

    private fun triggerFullListen(initialText: String) {
        if (isFullListening) return
        isFullListening = true
        updateNotification("Listening for command...")

        if (initialText.isNotBlank()) {
            processVoiceInput(initialText)
            isFullListening = false
            return
        }

        speechEngine.startListening(
            continuous = false,
            onPartial = { partial ->
                updateNotification("Hearing: $partial")
            }
        )

        // Timeout — revert to wake word mode after 10s
        scope.launch {
            delay(10_000L)
            if (isFullListening) {
                isFullListening = false
                speechEngine.stopListening()
                updateNotification("NOVA is listening...")
            }
        }
    }

    private fun processVoiceInput(text: String) {
        isFullListening = false
        updateNotification("NOVA is listening...")

        scope.launch {
            try {
                val langCode = storage.getString(SecureStorage.KEY_LANGUAGE, "en-US")
                val language = Language.values().firstOrNull { it.code == langCode } ?: Language.ENGLISH
                val intent = intentParser.parse(text, language)
                val result = commandExecutor.execute(intent)

                when (result) {
                    is com.nova.ai.models.CommandResult.Success -> {
                        val msg = result.message
                        if (!msg.startsWith("IMAGE_SAVED:") &&
                            !msg.startsWith("PDF_SAVED:") &&
                            !msg.startsWith("PPT_SAVED:") &&
                            !msg.startsWith("TXT_SAVED:") &&
                            !msg.startsWith("OPEN_URL:")) {
                            if (storage.getBoolean(SecureStorage.KEY_SPEAK_RESPONSES, true)) {
                                ttsEngine.speak(msg)
                            }
                        }
                        broadcastResult(text, msg, true)
                    }
                    is com.nova.ai.models.CommandResult.Error -> {
                        val msg = result.message
                        if (storage.getBoolean(SecureStorage.KEY_SPEAK_RESPONSES, true)) {
                            ttsEngine.speak(msg)
                        }
                        broadcastResult(text, msg, false)
                    }
                    is com.nova.ai.models.CommandResult.Pending -> {
                        ttsEngine.speak(result.message)
                        broadcastResult(text, result.message, true)
                    }
                }
            } catch (e: Exception) {
                // Silent fail — don't crash service
            }
        }
    }

    private fun broadcastResult(input: String, output: String, success: Boolean) {
        val broadcastIntent = Intent("com.nova.ai.COMMAND_RESULT").apply {
            putExtra("input", input)
            putExtra("output", output)
            putExtra("success", success)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceListenerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NovaApp.CHANNEL_VOICE)
            .setContentTitle(storage.getString(SecureStorage.KEY_ASSISTANT_NAME, "NOVA") + " AI")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        wakeWordDetector.stop()
        speechEngine.destroy()
        ttsEngine.destroy()
    }
}
