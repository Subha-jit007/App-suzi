package com.nova.ai.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure encrypted storage for API keys and sensitive data.
 * Uses AES256-GCM encryption via Android Keystore.
 * Keys are NEVER stored in plain text or in code.
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "nova_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ─── API Keys ───
    fun setClaudeApiKey(key: String) = prefs.edit().putString(KEY_CLAUDE, key).apply()
    fun getClaudeApiKey(): String = prefs.getString(KEY_CLAUDE, "") ?: ""
    fun hasClaudeKey(): Boolean = getClaudeApiKey().isNotBlank()

    fun setStabilityApiKey(key: String) = prefs.edit().putString(KEY_STABILITY, key).apply()
    fun getStabilityApiKey(): String = prefs.getString(KEY_STABILITY, "") ?: ""

    // ─── Config ───
    fun setString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getString(key: String, default: String = ""): String = prefs.getString(key, default) ?: default

    fun setBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)

    fun setFloat(key: String, value: Float) = prefs.edit().putFloat(key, value).apply()
    fun getFloat(key: String, default: Float = 1.0f): Float = prefs.getFloat(key, default)

    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        const val KEY_CLAUDE = "claude_api_key"
        const val KEY_STABILITY = "stability_api_key"
        const val KEY_ASSISTANT_NAME = "assistant_name"
        const val KEY_WAKE_WORD = "wake_word"
        const val KEY_LANGUAGE = "language"
        const val KEY_VOICE_TYPE = "voice_type"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_ALWAYS_LISTENING = "always_listening"
        const val KEY_SPEAK_RESPONSES = "speak_responses"
        const val KEY_PERSONALITY = "personality"
        const val KEY_CUSTOM_VOICE_PATH = "custom_voice_path"
    }
}
