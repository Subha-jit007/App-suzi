package com.nova.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.nova.ai.utils.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight always-on wake word detector.
 * Uses a minimal STT loop — only activates full AI on wake word match.
 * Supports custom wake words set by user.
 */
@Singleton
class WakeWordDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: SecureStorage
) {
    private var recognizer: SpeechRecognizer? = null
    private var isRunning = false

    private val _wakeWordDetected = MutableSharedFlow<String>()
    val wakeWordDetected: SharedFlow<String> = _wakeWordDetected

    private var wakeWords: List<String> = listOf("hey nova", "nova", "okay nova")

    fun updateWakeWords(primary: String) {
        wakeWords = buildList {
            add(primary.lowercase().trim())
            add("hey ${primary.lowercase().trim()}")
            add("ok ${primary.lowercase().trim()}")
            add("okay ${primary.lowercase().trim()}")
        }.distinct()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        val name = storage.getString(SecureStorage.KEY_ASSISTANT_NAME, "NOVA")
        updateWakeWords(name.lowercase())
        initAndListen()
    }

    fun stop() {
        isRunning = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun initAndListen() {
        if (!isRunning) return
        recognizer?.destroy()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.lowercase() ?: return

                    if (isWakeWord(text)) {
                        // Extract command after wake word
                        val command = extractCommandAfterWakeWord(text)
                        _wakeWordDetected.tryEmit(command)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.lowercase() ?: ""

                    if (isWakeWord(text)) {
                        val command = extractCommandAfterWakeWord(text)
                        _wakeWordDetected.tryEmit(command)
                    }
                    // Auto-restart loop
                    restart()
                }

                override fun onError(error: Int) {
                    restart()
                }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US,hi-IN,bn-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Keep timeout long for always-on mode
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }

        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            restart()
        }
    }

    private fun isWakeWord(text: String): Boolean {
        return wakeWords.any { wake -> text.contains(wake) }
    }

    private fun extractCommandAfterWakeWord(text: String): String {
        for (wake in wakeWords) {
            val idx = text.indexOf(wake)
            if (idx >= 0) {
                val after = text.substring(idx + wake.length).trim()
                if (after.isNotEmpty()) return after
            }
        }
        return "" // Just the wake word, no command yet
    }

    private fun restart() {
        if (!isRunning) return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            initAndListen()
        }, 400L)
    }
}
