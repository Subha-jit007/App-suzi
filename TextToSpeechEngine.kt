package com.nova.ai.voice

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nova.ai.models.Language
import com.nova.ai.models.VoiceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class TextToSpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var customVoicePath: String? = null
    private var customMediaPlayer: MediaPlayer? = null
    private var currentSpeechRate: Float = 1.0f
    private var currentVoiceType: VoiceType = VoiceType.FEMALE
    private var currentLanguage: Language = Language.ENGLISH

    fun initialize(onReady: (() -> Unit)? = null) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                configureTTS()
                onReady?.invoke()
            }
        }
    }

    private fun configureTTS() {
        val ttsEngine = tts ?: return
        val locale = when (currentLanguage) {
            Language.HINDI -> Locale("hi", "IN")
            Language.BENGALI -> Locale("bn", "IN")
            else -> Locale.US
        }

        ttsEngine.language = locale
        ttsEngine.setSpeechRate(currentSpeechRate)
        ttsEngine.setPitch(if (currentVoiceType == VoiceType.FEMALE) 1.2f else 0.85f)

        // Set voice by gender
        val voices = ttsEngine.voices
        if (voices != null) {
            val targetVoice = when (currentVoiceType) {
                VoiceType.FEMALE -> voices.find {
                    it.locale == locale && it.name.contains("female", ignoreCase = true)
                }
                VoiceType.MALE -> voices.find {
                    it.locale == locale && it.name.contains("male", ignoreCase = true)
                }
                else -> voices.find { it.locale == locale }
            }
            targetVoice?.let { ttsEngine.voice = it }
        }

        ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
        })
    }

    fun speak(text: String) {
        if (!isReady) return

        // Play custom voice greeting if set and text matches greeting
        if (customVoicePath != null && text.length < 50) {
            playCustomVoiceAudio()
            return
        }

        // Clean text for TTS
        val cleaned = text
            .replace(Regex("[🔦🔒📳🔋💡⏱️📋💬🌐🎵⏰🌤️🕐📅🤖📱🗺️▶️📷👤🎬📧🐦]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500) // Limit length

        tts?.speak(
            cleaned,
            TextToSpeech.QUEUE_FLUSH,
            null,
            UUID.randomUUID().toString()
        )
    }

    fun speakSilently(text: String) {
        // Speak without queue flush (for queued responses)
        val cleaned = text.replace(Regex("[^\\w\\s.,!?।৷]"), "").trim().take(500)
        tts?.speak(cleaned, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts?.stop()
        customMediaPlayer?.stop()
    }

    fun setCustomVoicePath(path: String) {
        customVoicePath = if (File(path).exists()) path else null
    }

    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate
        tts?.setSpeechRate(rate)
    }

    fun setVoiceType(type: VoiceType) {
        currentVoiceType = type
        if (isReady) configureTTS()
    }

    fun setLanguage(language: Language) {
        currentLanguage = language
        if (isReady) configureTTS()
    }

    private fun playCustomVoiceAudio() {
        val path = customVoicePath ?: return
        try {
            customMediaPlayer?.release()
            customMediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Fallback to system TTS
            customVoicePath = null
        }
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        customMediaPlayer?.release()
        customMediaPlayer = null
        isReady = false
    }
}
