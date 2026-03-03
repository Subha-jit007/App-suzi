package com.nova.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nova.ai.databinding.ActivityMainBinding
import com.nova.ai.services.VoiceListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // ─── PERMISSION LAUNCHER ───
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            startVoiceService()
            showToast("✅ Microphone ready — NOVA is listening!")
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeState()
        checkAndRequestPermissions()
    }

    private fun setupUI() {
        // Navigation
        binding.navHome.setOnClickListener { viewModel.navigateTo(Page.HOME) }
        binding.navChat.setOnClickListener { viewModel.navigateTo(Page.CHAT) }
        binding.navTools.setOnClickListener { viewModel.navigateTo(Page.TOOLS) }
        binding.navSettings.setOnClickListener { viewModel.navigateTo(Page.SETTINGS) }

        // Chat mic button
        binding.micButton.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.isListening) viewModel.stopListening()
            else viewModel.startListening()
        }

        // Chat send button
        binding.sendButton.setOnClickListener {
            val text = binding.chatInput.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                viewModel.sendText(text)
                binding.chatInput.setText("")
            }
        }

        // Home orb
        binding.novaOrb.setOnClickListener {
            viewModel.navigateTo(Page.CHAT)
        }

        // Quick action tiles
        binding.tileFlash.setOnClickListener { viewModel.sendText("flashlight toggle") }
        binding.tileLock.setOnClickListener { viewModel.sendText("lock screen") }
        binding.tileVibrate.setOnClickListener { viewModel.sendText("vibrate") }
        binding.tileBattery.setOnClickListener { viewModel.sendText("battery level") }

        // Settings save
        binding.saveSettingsBtn.setOnClickListener { saveSettings() }
        binding.saveApiKeyBtn.setOnClickListener {
            val key = binding.apiKeyInput.text?.toString()?.trim() ?: ""
            if (key.isNotEmpty()) {
                viewModel.saveApiKey(key)
                showToast("✅ AI Key saved!")
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                updateNavigation(state.currentPage)
                updatePages(state.currentPage)

                // Mic button state
                binding.micButton.isSelected = state.isListening

                // Partial text
                binding.partialText.text = state.partialText
                binding.partialText.visibility =
                    if (state.partialText.isNotEmpty()) View.VISIBLE else View.GONE

                // Chat messages
                updateChatMessages(state.messages)

                // Orb animation
                if (state.isListening) {
                    binding.novaOrb.animate().scaleX(1.15f).scaleY(1.15f).setDuration(300).start()
                } else {
                    binding.novaOrb.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
                }
            }
        }
    }

    private fun updateNavigation(page: Page) {
        binding.navHome.isSelected = page == Page.HOME
        binding.navChat.isSelected = page == Page.CHAT
        binding.navTools.isSelected = page == Page.TOOLS
        binding.navSettings.isSelected = page == Page.SETTINGS
    }

    private fun updatePages(page: Page) {
        binding.pageHome.visibility = if (page == Page.HOME) View.VISIBLE else View.GONE
        binding.pageChat.visibility = if (page == Page.CHAT) View.VISIBLE else View.GONE
        binding.pageTools.visibility = if (page == Page.TOOLS) View.VISIBLE else View.GONE
        binding.pageSettings.visibility = if (page == Page.SETTINGS) View.VISIBLE else View.GONE
    }

    private fun updateChatMessages(messages: List<com.nova.ai.models.ChatMessage>) {
        // In a real implementation, use RecyclerView with adapter
        // Simplified here for brevity
        val lastMessage = messages.lastOrNull() ?: return
        if (!lastMessage.isFromUser) {
            binding.lastAiResponse.text = lastMessage.text
            binding.lastAiResponse.visibility = View.VISIBLE
        }
    }

    private fun saveSettings() {
        val name = binding.assistantNameInput.text?.toString()?.trim() ?: "NOVA"
        viewModel.saveAssistantName(name)

        val langPos = binding.languageSpinner.selectedItemPosition
        val language = when (langPos) {
            1 -> com.nova.ai.models.Language.HINDI
            2 -> com.nova.ai.models.Language.BENGALI
            else -> com.nova.ai.models.Language.ENGLISH
        }
        viewModel.setLanguage(language)
        showToast("✅ Settings saved!")
    }

    // ─── PERMISSIONS ───
    private fun checkAndRequestPermissions() {
        val required = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.VIBRATE,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
            required.add(Manifest.permission.READ_MEDIA_IMAGES)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startVoiceService()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startVoiceService() {
        val serviceIntent = VoiceListenerService.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Microphone Required")
            .setMessage("NOVA needs microphone access to listen to your voice commands. Please grant it in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
            }
            .setNegativeButton("Continue Without Voice") { d, _ -> d.dismiss() }
            .show()
    }

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
