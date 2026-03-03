package com.nova.ai.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.ai.commands.CommandExecutor
import com.nova.ai.commands.IntentParser
import com.nova.ai.models.*
import com.nova.ai.services.VoiceListenerService
import com.nova.ai.utils.SecureStorage
import com.nova.ai.voice.SpeechEngine
import com.nova.ai.voice.TextToSpeechEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NovaUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isListening: Boolean = false,
    val partialText: String = "",
    val currentPage: Page = Page.HOME,
    val assistantName: String = "NOVA",
    val isServiceRunning: Boolean = false,
    val batteryLevel: Int = -1,
    val isFlashOn: Boolean = false,
    val isScreenOn: Boolean = false,
    val timerSecs: Int = 0,
    val isTimerRunning: Boolean = false,
    val language: Language = Language.ENGLISH,
    val notes: String = ""
)

enum class Page { HOME, CHAT, TOOLS, SETTINGS }

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: SecureStorage,
    private val intentParser: IntentParser,
    private val commandExecutor: CommandExecutor,
    private val speechEngine: SpeechEngine,
    private val ttsEngine: TextToSpeechEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(NovaUiState())
    val uiState: StateFlow<NovaUiState> = _uiState.asStateFlow()

    private val commandResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val input = intent.getStringExtra("input") ?: return
            val output = intent.getStringExtra("output") ?: return
            val success = intent.getBooleanExtra("success", true)

            addMessage(ChatMessage(text = input, isFromUser = true))
            addMessage(ChatMessage(text = output, isFromUser = false, isAIGenerated = success))
        }
    }

    init {
        loadConfig()
        registerResultReceiver()

        viewModelScope.launch {
            speechEngine.results.collect { result ->
                if (result.isFinal) {
                    processText(result.transcript)
                } else {
                    _uiState.update { it.copy(partialText = result.transcript) }
                }
            }
        }
    }

    private fun loadConfig() {
        val name = storage.getString(SecureStorage.KEY_ASSISTANT_NAME, "NOVA")
        val langCode = storage.getString(SecureStorage.KEY_LANGUAGE, "en-US")
        val language = Language.values().firstOrNull { it.code == langCode } ?: Language.ENGLISH
        val notes = storage.getString("nova_notes", "")

        _uiState.update {
            it.copy(
                assistantName = name,
                language = language,
                notes = notes
            )
        }
    }

    private fun registerResultReceiver() {
        context.registerReceiver(
            commandResultReceiver,
            IntentFilter("com.nova.ai.COMMAND_RESULT")
        )
    }

    fun navigateTo(page: Page) {
        _uiState.update { it.copy(currentPage = page) }
        if (page == Page.CHAT && _uiState.value.messages.isEmpty()) {
            val name = _uiState.value.assistantName
            val greeting = when (_uiState.value.language) {
                Language.HINDI -> "नमस्ते! मैं $name हूँ। कैसे मदद करूँ? 👋"
                Language.BENGALI -> "নমস্কার! আমি $name। কিভাবে সাহায্য করব? 👋"
                else -> "Hey! I'm $name, your AI assistant. Say anything or type below! 👋"
            }
            addMessage(ChatMessage(text = greeting, isFromUser = false))
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        processText(text)
    }

    private fun processText(text: String) {
        val language = _uiState.value.language
        addMessage(ChatMessage(text = text, isFromUser = true))
        _uiState.update { it.copy(partialText = "") }

        viewModelScope.launch {
            val intent = intentParser.parse(text, language)
            val result = commandExecutor.execute(intent)

            val responseText = when (result) {
                is CommandResult.Success -> result.message
                is CommandResult.Error -> result.message
                is CommandResult.Pending -> result.message
            }

            // Handle special results
            when {
                responseText.startsWith("OPEN_URL:") -> {
                    val url = responseText.removePrefix("OPEN_URL:")
                    openUrl(url)
                    addMessage(ChatMessage(text = "🌐 Opening: $url", isFromUser = false))
                }
                responseText.startsWith("IMAGE_SAVED:") -> {
                    val path = responseText.removePrefix("IMAGE_SAVED:").substringBefore("\n")
                    addMessage(ChatMessage(text = "🖼️ Image saved to Downloads!\nPath: $path", isFromUser = false, isAIGenerated = true))
                }
                responseText.startsWith("PDF_SAVED:") || responseText.startsWith("PPT_SAVED:") || responseText.startsWith("TXT_SAVED:") -> {
                    val msg = responseText.substringAfter("\n")
                    addMessage(ChatMessage(text = "📁 $msg", isFromUser = false))
                }
                else -> {
                    addMessage(ChatMessage(
                        text = responseText,
                        isFromUser = false,
                        isAIGenerated = intent.type == CommandType.ASK_AI
                    ))
                    if (storage.getBoolean(SecureStorage.KEY_SPEAK_RESPONSES, true)) {
                        ttsEngine.speak(responseText)
                    }
                }
            }
        }
    }

    fun startListening() {
        speechEngine.initialize()
        _uiState.update { it.copy(isListening = true) }
        speechEngine.startListening(
            continuous = false,
            onPartial = { partial ->
                _uiState.update { it.copy(partialText = partial) }
            }
        )
    }

    fun stopListening() {
        speechEngine.stopListening()
        _uiState.update { it.copy(isListening = false, partialText = "") }
    }

    fun saveNote(content: String) {
        val existing = storage.getString("nova_notes", "")
        val updated = if (existing.isBlank()) content else "$existing\n$content"
        storage.setString("nova_notes", updated)
        _uiState.update { it.copy(notes = updated) }
    }

    fun clearNotes() {
        storage.setString("nova_notes", "")
        _uiState.update { it.copy(notes = "") }
    }

    fun saveApiKey(key: String) {
        storage.setClaudeApiKey(key)
    }

    fun saveAssistantName(name: String) {
        storage.setString(SecureStorage.KEY_ASSISTANT_NAME, name)
        _uiState.update { it.copy(assistantName = name) }
    }

    fun setLanguage(language: Language) {
        storage.setString(SecureStorage.KEY_LANGUAGE, language.code)
        speechEngine.setLanguage(language)
        ttsEngine.setLanguage(language)
        _uiState.update { it.copy(language = language) }
    }

    private fun addMessage(msg: ChatMessage) {
        _uiState.update {
            it.copy(messages = it.messages + msg)
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun onCleared() {
        super.onCleared()
        try { context.unregisterReceiver(commandResultReceiver) } catch (e: Exception) {}
    }
}
