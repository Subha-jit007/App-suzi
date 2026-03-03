package com.nova.ai.commands

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Vibrator
import android.provider.AlarmClock
import android.provider.Settings
import com.nova.ai.ai.ClaudeRepository
import com.nova.ai.models.*
import com.nova.ai.services.NovaDeviceAdminReceiver
import com.nova.ai.utils.FileGenerator
import com.nova.ai.utils.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val claude: ClaudeRepository,
    private val fileGen: FileGenerator,
    private val storage: SecureStorage
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null
    private var torchOn = false

    init {
        try { cameraId = camManager.cameraIdList.firstOrNull() } catch (e: Exception) {}
    }

    suspend fun execute(intent: CommandIntent): CommandResult {
        return when (intent.type) {
            // ── COMMUNICATION ──
            CommandType.CALL -> executeCall(intent)
            CommandType.SEND_WHATSAPP -> executeWhatsApp(intent)
            CommandType.SEND_SMS -> executeSMS(intent)
            CommandType.OPEN_APP -> executeOpenApp(intent)

            // ── MEDIA ──
            CommandType.PLAY_MUSIC -> executePlayMusic()
            CommandType.PAUSE_MUSIC -> executePauseMusic()
            CommandType.NEXT_SONG -> executeNextSong()
            CommandType.YOUTUBE_SEARCH -> executeYouTubeSearch(intent)

            // ── VOLUME ──
            CommandType.VOLUME_UP -> executeVolumeUp()
            CommandType.VOLUME_DOWN -> executeVolumeDown()
            CommandType.VOLUME_MUTE -> executeVolumeMute()
            CommandType.VOLUME_MAX -> executeVolumeMax()

            // ── SYSTEM ──
            CommandType.FLASHLIGHT_ON -> executeFlashlightOn()
            CommandType.FLASHLIGHT_OFF -> executeFlashlightOff()
            CommandType.LOCK_SCREEN -> executeLockScreen()

            // ── FILES ──
            CommandType.CREATE_PDF -> executeCreatePDF(intent)
            CommandType.CREATE_PPT -> executeCreatePPT(intent)
            CommandType.CREATE_TXT -> executeCreateTXT(intent)

            // ── IMAGE ──
            CommandType.GENERATE_IMAGE -> executeImageGen(intent)

            // ── INFO ──
            CommandType.BATTERY_STATUS -> executeBattery()
            CommandType.CURRENT_TIME -> executeTime()
            CommandType.CURRENT_DATE -> executeDate()

            // ── ALARMS / TIMERS ──
            CommandType.SET_ALARM -> executeAlarm(intent)
            CommandType.SET_TIMER -> executeTimer(intent)

            // ── NOTES ──
            CommandType.SAVE_NOTE -> executeSaveNote(intent)

            // ── CONVERSATION ──
            CommandType.GREETING -> executeGreeting(intent.language)
            CommandType.HELP -> executeHelp(intent.language)
            CommandType.WHO_ARE_YOU -> executeWhoAmI(intent.language)
            CommandType.JOKE -> executeJoke(intent.language)

            // ── AI BRAIN ──
            CommandType.ASK_AI -> executeAskAI(intent)
            CommandType.SEARCH_WEB -> executeAskAI(intent)

            CommandType.SAVE_NOTE -> executeSaveNote(intent)
            CommandType.READ_NOTES -> executeReadNotes(intent.language)

            else -> CommandResult.Error("Command not recognized")
        }
    }

    // ─── CALL ───
    private fun executeCall(intent: CommandIntent): CommandResult {
        val contact = intent.params["contact"] ?: return CommandResult.Error("No contact name")
        val uri = Uri.parse("tel:${Uri.encode(contact)}")
        val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(callIntent)
        return CommandResult.Success("Calling $contact...")
    }

    // ─── WHATSAPP ───
    private fun executeWhatsApp(intent: CommandIntent): CommandResult {
        val contact = intent.params["contact"] ?: ""
        val message = intent.params["message"] ?: ""
        val number = contact.filter { it.isDigit() }

        val uri = if (number.length >= 7) {
            Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}")
        } else {
            // Open WhatsApp with contact name
            Uri.parse("whatsapp://send?text=${Uri.encode(message)}")
        }

        val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(waIntent)
            return CommandResult.Success("Opening WhatsApp to $contact")
        } catch (e: Exception) {
            // Fallback: open WhatsApp web
            val webIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://web.whatsapp.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            return CommandResult.Success("Opening WhatsApp Web")
        }
    }

    // ─── SMS ───
    private fun executeSMS(intent: CommandIntent): CommandResult {
        val contact = intent.params["contact"] ?: ""
        val message = intent.params["message"] ?: ""
        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${contact.filter { it.isDigit() }}")
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(smsIntent)
        return CommandResult.Success("Opening SMS to $contact")
    }

    // ─── OPEN APP ───
    private fun executeOpenApp(intent: CommandIntent): CommandResult {
        val appName = intent.params["app"] ?: return CommandResult.Error("No app specified")
        val appMap = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "chrome" to "com.android.chrome",
            "camera" to "android.media.action.IMAGE_CAPTURE",
            "gallery" to "com.android.gallery3d",
            "settings" to "com.android.settings",
            "calculator" to "com.android.calculator2",
            "clock" to "com.android.deskclock"
        )

        val lower = appName.lowercase().trim()
        val pkg = appMap.entries.firstOrNull { lower.contains(it.key) }?.value

        return if (pkg != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                CommandResult.Success("Opening $appName")
            } else {
                // Open Play Store if not installed
                val playIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$pkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playIntent)
                CommandResult.Error("$appName not installed — opening Play Store")
            }
        } else {
            // Search Play Store
            val searchIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("market://search?q=$appName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(searchIntent)
            CommandResult.Success("Searching for $appName")
        }
    }

    // ─── MEDIA ───
    private fun executePlayMusic(): CommandResult {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Play Music").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return CommandResult.Success("Opening music player")
    }

    private fun executePauseMusic(): CommandResult {
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(
            android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(
            android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
        return CommandResult.Success("Music paused")
    }

    private fun executeNextSong(): CommandResult {
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(
            android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(
            android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_NEXT))
        return CommandResult.Success("Next song")
    }

    private fun executeYouTubeSearch(intent: CommandIntent): CommandResult {
        val query = intent.params["query"] ?: return CommandResult.Error("No search query")
        val ytIntent = Intent(Intent.ACTION_SEARCH).apply {
            `package` = "com.google.android.youtube"
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(ytIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
        return CommandResult.Success("Searching YouTube for: $query")
    }

    // ─── VOLUME ───
    private fun executeVolumeUp(): CommandResult {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        return CommandResult.Success("Volume up")
    }

    private fun executeVolumeDown(): CommandResult {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        return CommandResult.Success("Volume down")
    }

    private fun executeVolumeMute(): CommandResult {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        return CommandResult.Success("Muted")
    }

    private fun executeVolumeMax(): CommandResult {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
        return CommandResult.Success("Volume at maximum")
    }

    // ─── FLASHLIGHT ───
    private fun executeFlashlightOn(): CommandResult {
        return try {
            val camId = cameraId ?: throw Exception("No camera")
            camManager.setTorchMode(camId, true)
            torchOn = true
            CommandResult.Success("Flashlight ON 🔦")
        } catch (e: Exception) {
            CommandResult.Error("Could not turn on flashlight: ${e.message}")
        }
    }

    private fun executeFlashlightOff(): CommandResult {
        return try {
            val camId = cameraId ?: throw Exception("No camera")
            camManager.setTorchMode(camId, false)
            torchOn = false
            CommandResult.Success("Flashlight OFF")
        } catch (e: Exception) {
            CommandResult.Error("Could not turn off flashlight")
        }
    }

    // ─── LOCK SCREEN (DevicePolicyManager — official API) ───
    private fun executeLockScreen(): CommandResult {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComp = ComponentName(context, NovaDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(adminComp)) {
                dpm.lockNow()
                CommandResult.Success("Screen locked 🔒")
            } else {
                // Guide user to enable device admin
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "NOVA needs admin to lock your screen on command")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                CommandResult.Pending("Please grant admin permission to enable screen lock")
            }
        } catch (e: Exception) {
            CommandResult.Error("Lock failed: ${e.message}")
        }
    }

    // ─── FILE GENERATION ───
    private suspend fun executeCreatePDF(intent: CommandIntent): CommandResult {
        val title = intent.params["title"] ?: "Nova Document"
        val content = intent.params["content"] ?: ""
        return fileGen.generatePDF(title, content)
    }

    private suspend fun executeCreatePPT(intent: CommandIntent): CommandResult {
        val title = intent.params["title"] ?: "Presentation"
        val content = intent.params["content"] ?: ""
        return fileGen.generatePPT(title, content)
    }

    private suspend fun executeCreateTXT(intent: CommandIntent): CommandResult {
        val content = intent.params["content"] ?: intent.rawText
        return fileGen.generateTXT(content)
    }

    // ─── IMAGE GENERATION ───
    private suspend fun executeImageGen(intent: CommandIntent): CommandResult {
        val prompt = intent.params["prompt"] ?: intent.rawText
        return claude.generateImage(prompt)
    }

    // ─── BATTERY ───
    private fun executeBattery(): CommandResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        val msg = "Battery at $level%${if (isCharging) " ⚡ Charging" else ""}"
        return CommandResult.Success(msg)
    }

    // ─── TIME ───
    private fun executeTime(): CommandResult {
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        return CommandResult.Success("Current time is $time 🕐")
    }

    private fun executeDate(): CommandResult {
        val date = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date())
        return CommandResult.Success("Today is $date 📅")
    }

    // ─── ALARM ───
    private fun executeAlarm(intent: CommandIntent): CommandResult {
        val timeStr = intent.params["time"] ?: ""
        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, "NOVA Alarm")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Try parsing time
        Regex("(\\d{1,2}):?(\\d{2})?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(timeStr)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: 0
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val isPM = m.groupValues[3].equals("pm", true)
            alarmIntent.putExtra(AlarmClock.EXTRA_HOUR, if (isPM && hour < 12) hour + 12 else hour)
            alarmIntent.putExtra(AlarmClock.EXTRA_MINUTES, min)
        }
        context.startActivity(alarmIntent)
        return CommandResult.Success("Alarm set ⏰")
    }

    // ─── TIMER ───
    private fun executeTimer(intent: CommandIntent): CommandResult {
        val secs = intent.params["seconds"]?.toIntOrNull() ?: 60
        val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, secs)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(timerIntent)
        val mins = secs / 60
        return CommandResult.Success("Timer set for ${if (mins > 0) "$mins min" else "$secs sec"} ⏱️")
    }

    // ─── NOTES ───
    private fun executeSaveNote(intent: CommandIntent): CommandResult {
        val content = intent.params["content"] ?: return CommandResult.Error("Nothing to save")
        val notes = storage.getString("nova_notes", "")
        val timestamp = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date())
        storage.setString("nova_notes", "$notes\n[$timestamp] $content")
        return CommandResult.Success("Note saved 📋")
    }

    private fun executeReadNotes(lang: Language): CommandResult {
        val notes = storage.getString("nova_notes", "")
        return if (notes.isBlank())
            CommandResult.Success("No saved notes yet")
        else
            CommandResult.Success("Your notes:\n$notes")
    }

    // ─── CONVERSATION ───
    private fun executeGreeting(lang: Language): CommandResult {
        val name = storage.getString(SecureStorage.KEY_ASSISTANT_NAME, "NOVA")
        val msg = when (lang) {
            Language.HINDI -> "नमस्ते! मैं $name हूँ। कैसे मदद करूँ? 👋"
            Language.BENGALI -> "নমস্কার! আমি $name। কিভাবে সাহায্য করব? 👋"
            else -> "Hey there! I'm $name, your AI assistant. How can I help? 👋"
        }
        return CommandResult.Success(msg)
    }

    private fun executeHelp(lang: Language): CommandResult {
        val msg = when (lang) {
            Language.HINDI -> "मैं कर सकता हूँ: 📞 कॉल करना · 💬 WhatsApp भेजना · 🔦 टॉर्च · 🔒 लॉक · 🔊 वॉल्यूम · 🎵 म्यूज़िक · 📄 PDF बनाना · 🖼️ Image बनाना · 🧠 AI से सवाल पूछना!"
            Language.BENGALI -> "আমি করতে পারি: 📞 কল · 💬 WhatsApp · 🔦 টর্চ · 🔒 লক · 🔊 ভলিউম · 🎵 মিউজিক · 📄 PDF তৈরি · 🖼️ ছবি তৈরি · 🧠 AI-এ প্রশ্ন করা!"
            else -> "I can: 📞 Call contacts · 💬 WhatsApp/SMS · 🔦 Flashlight · 🔒 Lock screen · 🔊 Volume · 🎵 Music · 📄 Create PDF/PPT/TXT · 🖼️ Generate images · 🧠 Answer anything with AI!"
        }
        return CommandResult.Success(msg)
    }

    private fun executeWhoAmI(lang: Language): CommandResult {
        val name = storage.getString(SecureStorage.KEY_ASSISTANT_NAME, "NOVA")
        val msg = when (lang) {
            Language.HINDI -> "मैं $name हूँ — आपका AI असिस्टेंट। Claude AI से powered! 🤖"
            Language.BENGALI -> "আমি $name — আপনার AI সহকারী। Claude AI দিয়ে চালিত! 🤖"
            else -> "I'm $name — your AI-powered phone assistant. Powered by Claude AI! 🤖"
        }
        return CommandResult.Success(msg)
    }

    private fun executeJoke(lang: Language): CommandResult {
        val jokes = when (lang) {
            Language.HINDI -> listOf(
                "प्रोग्रामर डार्क मोड क्यों पसंद करते हैं? रोशनी में कीड़े आते हैं! 🐛😄",
                "मेरी मेमोरी पूरी है... फिर भी मैं तुम्हें याद करता हूँ! 😊"
            )
            Language.BENGALI -> listOf(
                "প্রোগ্রামাররা ডার্ক মোড কেন পছন্দ করে? আলোতে পোকা আসে! 🐛😄",
                "আমার মেমোরি ভর্তি... তবু তোমাকে মনে আছে! 😊"
            )
            else -> listOf(
                "Why do programmers prefer dark mode? Because light attracts bugs! 🐛😄",
                "I told a joke about memory once... I forgot how it ended! 😄",
                "Why don't scientists trust atoms? They make up everything! 😄"
            )
        }
        return CommandResult.Success(jokes.random())
    }

    // ─── AI BRAIN ───
    private suspend fun executeAskAI(intent: CommandIntent): CommandResult {
        return claude.askClaude(intent.rawText, intent.language)
    }
}
