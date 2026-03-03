package com.nova.ai.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ─── VOICE COMMAND RESULT ───
data class VoiceResult(
    val transcript: String,
    val confidence: Float,
    val language: String,
    val isFinal: Boolean
)

// ─── PARSED COMMAND INTENT ───
data class CommandIntent(
    val type: CommandType,
    val rawText: String,
    val params: Map<String, String> = emptyMap(),
    val language: Language = Language.ENGLISH
)

enum class CommandType {
    // Communication
    CALL, SEND_WHATSAPP, SEND_SMS, OPEN_APP,
    // Media
    PLAY_MUSIC, PAUSE_MUSIC, NEXT_SONG, YOUTUBE_SEARCH,
    // Volume
    VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE, VOLUME_MAX,
    // System
    FLASHLIGHT_ON, FLASHLIGHT_OFF, LOCK_SCREEN,
    // Files
    CREATE_PDF, CREATE_PPT, CREATE_TXT,
    // Image
    GENERATE_IMAGE,
    // AI
    ASK_AI, SEARCH_WEB,
    // Notes
    SAVE_NOTE, READ_NOTES,
    // Time
    SET_ALARM, SET_TIMER, CURRENT_TIME, CURRENT_DATE,
    // Info
    BATTERY_STATUS, WEATHER,
    // Conversation
    GREETING, HELP, WHO_ARE_YOU, JOKE,
    // Unknown
    UNKNOWN
}

enum class Language(val code: String, val displayName: String) {
    ENGLISH("en-US", "English"),
    HINDI("hi-IN", "हिंदी"),
    BENGALI("bn-IN", "বাংলা"),
    AUTO("auto", "Auto-detect")
}

// ─── AI RESPONSE ───
data class AIResponse(
    val text: String,
    val isFromAI: Boolean = false,
    val confidence: Float = 1.0f,
    val sourceLanguage: Language = Language.ENGLISH
)

// ─── COMMAND EXECUTION RESULT ───
sealed class CommandResult {
    data class Success(val message: String, val data: Any? = null) : CommandResult()
    data class Error(val message: String, val exception: Exception? = null) : CommandResult()
    data class Pending(val message: String) : CommandResult()
}

// ─── CHAT MESSAGE ───
@Parcelize
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isAIGenerated: Boolean = false,
    val commandType: String? = null
) : Parcelable

// ─── NOVA CONFIG ───
data class NovaConfig(
    val assistantName: String = "NOVA",
    val wakeWord: String = "hey nova",
    val language: Language = Language.ENGLISH,
    val voiceType: VoiceType = VoiceType.FEMALE,
    val speechRate: Float = 1.0f,
    val alwaysListening: Boolean = true,
    val speakResponses: Boolean = true,
    val personality: Personality = Personality.FRIENDLY,
    val claudeApiKey: String = "",
    val stabilityApiKey: String = ""
)

enum class VoiceType { FEMALE, MALE, NEUTRAL }
enum class Personality { FRIENDLY, PROFESSIONAL, SASSY }

// ─── FILE GENERATION REQUEST ───
data class FileRequest(
    val type: FileType,
    val title: String,
    val content: String,
    val bulletPoints: List<String> = emptyList()
)

enum class FileType { PDF, PPT, TXT }

// ─── CLAUDE API MODELS ───
data class ClaudeRequest(
    val model: String = "claude-haiku-4-5-20251001",
    val max_tokens: Int = 500,
    val system: String,
    val messages: List<ClaudeMessage>
)

data class ClaudeMessage(
    val role: String,
    val content: String
)

data class ClaudeResponse(
    val content: List<ClaudeContent>,
    val usage: ClaudeUsage?
)

data class ClaudeContent(val type: String, val text: String)
data class ClaudeUsage(val input_tokens: Int, val output_tokens: Int)

// ─── IMAGE GENERATION ───
data class ImageRequest(
    val prompt: String,
    val width: Int = 512,
    val height: Int = 512
)
