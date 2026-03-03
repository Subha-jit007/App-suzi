package com.nova.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.nova.ai.models.Language
import com.nova.ai.models.VoiceResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recognizer: SpeechRecognizer? = null
    private val _results = Channel<VoiceResult>(Channel.UNLIMITED)
    val results: Flow<VoiceResult> = _results.receiveAsFlow()

    private var currentLanguage: Language = Language.ENGLISH
    private var isListening = false
    private var onPartialResult: ((String) -> Unit)? = null
    private var autoRestart = false

    fun setLanguage(lang: Language) { currentLanguage = lang }

    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(buildListener())
        }
    }

    fun startListening(
        continuous: Boolean = false,
        onPartial: ((String) -> Unit)? = null
    ) {
        if (isListening) return
        onPartialResult = onPartial
        autoRestart = continuous
        isListening = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage.code)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguage.code)
            putExtra(RecognizerIntent.EXTRA_ALSO_RECOGNIZE_SPEECH, "en-US,hi-IN,bn-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            isListening = false
        }
    }

    fun stopListening() {
        autoRestart = false
        isListening = false
        recognizer?.stopListening()
    }

    fun destroy() {
        autoRestart = false
        isListening = false
        recognizer?.destroy()
        recognizer = null
        _results.close()
    }

    private fun buildListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isListening = false }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            onPartialResult?.invoke(partial)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            val transcript = matches?.firstOrNull() ?: return
            val confidence = scores?.firstOrNull() ?: 0.8f

            _results.trySend(VoiceResult(
                transcript = transcript,
                confidence = confidence,
                language = currentLanguage.code,
                isFinal = true
            ))

            // Auto-restart for continuous mode
            if (autoRestart) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startListening(continuous = true, onPartial = onPartialResult)
                }, 300L)
            }
        }

        override fun onError(error: Int) {
            isListening = false
            val isNetworkError = error == SpeechRecognizer.ERROR_NETWORK
            val isNoMatch = error == SpeechRecognizer.ERROR_NO_MATCH
            val isSilence = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

            // Silently restart on common non-critical errors
            if (autoRestart && (isNoMatch || isSilence)) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startListening(continuous = true, onPartial = onPartialResult)
                }, 500L)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
