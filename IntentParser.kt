package com.nova.ai.commands

import com.nova.ai.models.CommandIntent
import com.nova.ai.models.CommandType
import com.nova.ai.models.Language
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multilingual natural language command parser.
 * Supports English, Hindi (हिंदी), Bengali (বাংলা).
 * No external library required — pure keyword matching + regex.
 */
@Singleton
class IntentParser @Inject constructor() {

    fun parse(text: String, language: Language = Language.ENGLISH): CommandIntent {
        val lower = text.lowercase().trim()

        // ─── COMMUNICATION ───
        parseCall(lower, text, language)?.let { return it }
        parseWhatsApp(lower, text, language)?.let { return it }
        parseSMS(lower, text, language)?.let { return it }

        // ─── APPS ───
        parseOpenApp(lower, text, language)?.let { return it }

        // ─── MEDIA ───
        parseMedia(lower, language)?.let { return it }
        parseYouTube(lower, text, language)?.let { return it }

        // ─── VOLUME ───
        parseVolume(lower, language)?.let { return it }

        // ─── FLASHLIGHT ───
        parseFlashlight(lower, language)?.let { return it }

        // ─── LOCK ───
        parseLock(lower, language)?.let { return it }

        // ─── FILES ───
        parsePDF(lower, text, language)?.let { return it }
        parsePPT(lower, text, language)?.let { return it }
        parseTXT(lower, text, language)?.let { return it }

        // ─── IMAGE GEN ───
        parseImageGen(lower, text, language)?.let { return it }

        // ─── TIMER / ALARM ───
        parseTimer(lower, text, language)?.let { return it }
        parseAlarm(lower, text, language)?.let { return it }

        // ─── INFO ───
        parseTime(lower, language)?.let { return it }
        parseBattery(lower, language)?.let { return it }

        // ─── NOTES ───
        parseNote(lower, text, language)?.let { return it }

        // ─── CONVERSATION ───
        parseConversation(lower, language)?.let { return it }

        // ─── FALLBACK: Ask AI ───
        return CommandIntent(CommandType.ASK_AI, text, mapOf("query" to text), language)
    }

    // ─── CALL ───
    private fun parseCall(lower: String, original: String, lang: Language): CommandIntent? {
        val triggers = listOf(
            "call ", "phone ", "dial ",        // English
            "कॉल करो ", "फोन करो ", "call करो ", // Hindi
            "কল করো ", "ফোন করো "                // Bengali
        )
        val trigger = triggers.firstOrNull { lower.startsWith(it) } ?: return null
        val contact = original.substring(trigger.length).trim()
        return CommandIntent(CommandType.CALL, original, mapOf("contact" to contact), lang)
    }

    // ─── WHATSAPP ───
    private fun parseWhatsApp(lower: String, original: String, lang: Language): CommandIntent? {
        val patterns = listOf(
            Regex("(?:send\\s+)?whatsapp\\s+(?:to\\s+)?(.+?)\\s*:\\s*(.+)"),
            Regex("(?:send\\s+)?message\\s+to\\s+(.+?)\\s*:\\s*(.+)"),
            Regex("whatsapp\\s+(.+?)\\s+(?:saying|message|say)\\s+(.+)"),
            // Hindi
            Regex("(?:व्हाट्सएप|whatsapp)\\s+(?:करो\\s+)?(.+?)\\s*:\\s*(.+)"),
            Regex("(.+?)\\s+को\\s+(?:message|मैसेज)\\s+(?:भेजो|भेज)\\s*:\\s*(.+)"),
            // Bengali
            Regex("(?:হোয়াটসঅ্যাপ|whatsapp)\\s+(.+?)\\s*:\\s*(.+)"),
            Regex("(.+?)\\s+কে\\s+(?:message|মেসেজ)\\s+পাঠাও\\s*:\\s*(.+)")
        )

        for (pattern in patterns) {
            val match = pattern.find(lower) ?: continue
            val contact = match.groupValues[1].trim()
            val message = original.substringAfter(":").trim()
            return CommandIntent(CommandType.SEND_WHATSAPP, original,
                mapOf("contact" to contact, "message" to message), lang)
        }
        return null
    }

    // ─── SMS ───
    private fun parseSMS(lower: String, original: String, lang: Language): CommandIntent? {
        val patterns = listOf(
            Regex("(?:send\\s+)?sms\\s+(?:to\\s+)?(.+?)\\s*:\\s*(.+)"),
            Regex("(?:text|SMS)\\s+to\\s+(.+?)\\s*:\\s*(.+)"),
            Regex("SMS\\s+(?:करो\\s+)?(.+?)\\s*:\\s*(.+)"),  // Hindi
            Regex("SMS\\s+(.+?)\\s*:\\s*(.+)")                // Bengali
        )
        for (pattern in patterns) {
            val match = pattern.find(lower) ?: continue
            val contact = match.groupValues[1].trim()
            val message = original.substringAfter(":").trim()
            return CommandIntent(CommandType.SEND_SMS, original,
                mapOf("contact" to contact, "message" to message), lang)
        }
        return null
    }

    // ─── OPEN APP ───
    private fun parseOpenApp(lower: String, original: String, lang: Language): CommandIntent? {
        val openWords = listOf("open ", "launch ", "start ", "go to ",
            "खोलो ", "चालू करो ", "খোলো ", "লঞ্চ করো ")
        val trigger = openWords.firstOrNull { lower.startsWith(it) } ?: return null
        val appName = original.substring(trigger.length).trim()
        return CommandIntent(CommandType.OPEN_APP, original, mapOf("app" to appName), lang)
    }

    // ─── MEDIA ───
    private fun parseMedia(lower: String, lang: Language): CommandIntent? {
        val playKw = listOf("play music", "play song", "play audio", "start music",
            "music chalao", "gana chalao", "गाना चलाओ", "संगीत चलाओ",
            "গান চালাও", "সঙ্গীত চালাও")
        val pauseKw = listOf("pause music", "pause song", "stop music",
            "music pause", "गाना बंद", "গান বন্ধ করো")
        val nextKw = listOf("next song", "next track", "skip song",
            "अगला गाना", "পরের গান", "next gana")

        return when {
            playKw.any { lower.contains(it) } -> CommandIntent(CommandType.PLAY_MUSIC, lower, lang = lang)
            pauseKw.any { lower.contains(it) } -> CommandIntent(CommandType.PAUSE_MUSIC, lower, lang = lang)
            nextKw.any { lower.contains(it) } -> CommandIntent(CommandType.NEXT_SONG, lower, lang = lang)
            else -> null
        }
    }

    // ─── YOUTUBE ───
    private fun parseYouTube(lower: String, original: String, lang: Language): CommandIntent? {
        val patterns = listOf(
            Regex("youtube.*?(?:search|find|play|show)\\s+(.+)"),
            Regex("(?:search|find|play)\\s+(.+?)\\s+on\\s+youtube"),
            Regex("youtube\\s+पर\\s+(.+?)\\s+(?:ढूंढो|चलाओ)"),   // Hindi
            Regex("youtube\\s+এ\\s+(.+?)\\s+(?:খোঁজো|চালাও)")    // Bengali
        )
        for (p in patterns) {
            val m = p.find(lower) ?: continue
            return CommandIntent(CommandType.YOUTUBE_SEARCH, original,
                mapOf("query" to m.groupValues[1].trim()), lang)
        }
        return null
    }

    // ─── VOLUME ───
    private fun parseVolume(lower: String, lang: Language): CommandIntent? {
        val upKw = listOf("volume up", "increase volume", "louder", "raise volume",
            "आवाज़ बढ़ाओ", "volume badhao", "আওয়াজ বাড়াও")
        val downKw = listOf("volume down", "decrease volume", "quieter", "lower volume",
            "आवाज़ कम करो", "volume kam", "আওয়াজ কমাও")
        val muteKw = listOf("mute", "silence", "quiet", "बंद करो", "চুপ করো")
        val maxKw = listOf("max volume", "full volume", "maximum volume",
            "पूरी आवाज़", "সর্বোচ্চ ভলিউম")

        return when {
            upKw.any { lower.contains(it) } -> CommandIntent(CommandType.VOLUME_UP, lower, lang = lang)
            downKw.any { lower.contains(it) } -> CommandIntent(CommandType.VOLUME_DOWN, lower, lang = lang)
            muteKw.any { lower.contains(it) } -> CommandIntent(CommandType.VOLUME_MUTE, lower, lang = lang)
            maxKw.any { lower.contains(it) } -> CommandIntent(CommandType.VOLUME_MAX, lower, lang = lang)
            else -> null
        }
    }

    // ─── FLASHLIGHT ───
    private fun parseFlashlight(lower: String, lang: Language): CommandIntent? {
        val onKw = listOf("flashlight on", "torch on", "turn on flash", "enable flashlight",
            "flash on", "टॉर्च चालू", "flashlight chalo", "টর্চ চালু")
        val offKw = listOf("flashlight off", "torch off", "turn off flash", "disable flashlight",
            "flash off", "टॉर्च बंद", "টর্চ বন্ধ")

        return when {
            onKw.any { lower.contains(it) } -> CommandIntent(CommandType.FLASHLIGHT_ON, lower, lang = lang)
            offKw.any { lower.contains(it) } -> CommandIntent(CommandType.FLASHLIGHT_OFF, lower, lang = lang)
            lower.contains("flashlight") || lower.contains("torch") || lower.contains("टॉर्च") || lower.contains("টর্চ") -> {
                if (lower.contains("on") || lower.contains("चालू") || lower.contains("চালু"))
                    CommandIntent(CommandType.FLASHLIGHT_ON, lower, lang = lang)
                else
                    CommandIntent(CommandType.FLASHLIGHT_OFF, lower, lang = lang)
            }
            else -> null
        }
    }

    // ─── LOCK ───
    private fun parseLock(lower: String, lang: Language): CommandIntent? {
        val lockKw = listOf("lock screen", "lock phone", "screen lock", "lock the phone",
            "स्क्रीन लॉक", "फोन लॉक करो", "স্ক্রিন লক", "ফোন লক করো")
        return if (lockKw.any { lower.contains(it) })
            CommandIntent(CommandType.LOCK_SCREEN, lower, lang = lang)
        else null
    }

    // ─── PDF ───
    private fun parsePDF(lower: String, original: String, lang: Language): CommandIntent? {
        val patterns = listOf(
            Regex("create\\s+(?:a\\s+)?pdf\\s+(?:with\\s+)?(?:title\\s+)?[\"']?(.+?)[\"']?\\s*(?:content|saying|with|:)?\\s*(.*)"),
            Regex("make\\s+(?:a\\s+)?pdf\\s+(.+)"),
            Regex("(?:pdf|पीडीएफ|পিডিএফ)\\s+(?:बनाओ|তৈরি করো)?\\s+(.+)")
        )
        for (p in patterns) {
            val m = p.find(lower) ?: continue
            val title = m.groupValues.getOrNull(1)?.trim() ?: "Document"
            val content = m.groupValues.getOrNull(2)?.trim() ?: ""
            return CommandIntent(CommandType.CREATE_PDF, original,
                mapOf("title" to title, "content" to content), lang)
        }
        if (lower.contains("pdf") && (lower.contains("create") || lower.contains("make") || lower.contains("generate") || lower.contains("बनाओ") || lower.contains("তৈরি"))) {
            return CommandIntent(CommandType.CREATE_PDF, original,
                mapOf("title" to "Nova Document", "content" to original), lang)
        }
        return null
    }

    // ─── PPT ───
    private fun parsePPT(lower: String, original: String, lang: Language): CommandIntent? {
        val hasPPT = lower.contains("ppt") || lower.contains("presentation") ||
                lower.contains("powerpoint") || lower.contains("slides") ||
                lower.contains("प्रेजेंटेशन") || lower.contains("উপস্থাপনা")
        val hasAction = lower.contains("create") || lower.contains("make") ||
                lower.contains("generate") || lower.contains("बनाओ") || lower.contains("তৈরি")
        if (!hasPPT || !hasAction) return null
        val title = Regex("(?:titled?|called?|named?)\\s+[\"']?([^\"']+)[\"']?").find(lower)
            ?.groupValues?.get(1) ?: "Presentation"
        return CommandIntent(CommandType.CREATE_PPT, original,
            mapOf("title" to title, "content" to original), lang)
    }

    // ─── TXT ───
    private fun parseTXT(lower: String, original: String, lang: Language): CommandIntent? {
        val patterns = listOf(
            Regex("(?:save|write|create)\\s+(?:a\\s+)?(?:note|text|txt)\\s*(?:file)?\\s*:?\\s*(.+)"),
            Regex("(?:नोट|note|নোট)\\s+(?:सेव|save|সেভ)\\s+(?:करो|করো)?\\s*:?\\s*(.+)")
        )
        for (p in patterns) {
            val m = p.find(lower) ?: continue
            return CommandIntent(CommandType.CREATE_TXT, original,
                mapOf("content" to m.groupValues[1].trim()), lang)
        }
        return null
    }

    // ─── IMAGE GEN ───
    private fun parseImageGen(lower: String, original: String, lang: Language): CommandIntent? {
        val triggers = listOf("generate image", "create image", "draw", "make image",
            "image of", "picture of", "make a picture", "तस्वीर बनाओ", "ছবি তৈরি করো")
        if (triggers.none { lower.contains(it) }) return null
        val prompt = original.replace(Regex("(?:generate|create|draw|make)\\s+(?:an?\\s+)?(?:image|picture)\\s+(?:of\\s+)?", RegexOption.IGNORE_CASE), "").trim()
        return CommandIntent(CommandType.GENERATE_IMAGE, original, mapOf("prompt" to prompt), lang)
    }

    // ─── TIMER ───
    private fun parseTimer(lower: String, original: String, lang: Language): CommandIntent? {
        val timerKw = listOf("timer", "countdown", "टाइमर", "টাইমার", "set timer")
        if (timerKw.none { lower.contains(it) }) return null
        val minutes = Regex("(\\d+)\\s*(?:min|minute|मिनट|মিনিট)").find(lower)?.groupValues?.get(1)
        val seconds = Regex("(\\d+)\\s*(?:sec|second|सेकंड|সেকেন্ড)").find(lower)?.groupValues?.get(1)
        val totalSecs = ((minutes?.toIntOrNull() ?: 0) * 60) + (seconds?.toIntOrNull() ?: 0)
        return CommandIntent(CommandType.SET_TIMER, original,
            mapOf("seconds" to totalSecs.toString()), lang)
    }

    // ─── ALARM ───
    private fun parseAlarm(lower: String, original: String, lang: Language): CommandIntent? {
        val alarmKw = listOf("alarm", "wake me", "remind me", "set alarm",
            "अलार्म", "অ্যালার্ম", "जगाओ", "জাগাও")
        if (alarmKw.none { lower.contains(it) }) return null
        val timeMatch = Regex("(\\d{1,2}):?(\\d{2})?\\s*(am|pm)?").find(lower)
        val timeStr = timeMatch?.value ?: ""
        return CommandIntent(CommandType.SET_ALARM, original, mapOf("time" to timeStr), lang)
    }

    // ─── TIME ───
    private fun parseTime(lower: String, lang: Language): CommandIntent? {
        val kw = listOf("what time", "current time", "time now", "time is it",
            "क्या समय", "कितने बजे", "কটা বাজে", "সময় কত")
        return if (kw.any { lower.contains(it) })
            CommandIntent(CommandType.CURRENT_TIME, lower, lang = lang)
        else null
    }

    // ─── BATTERY ───
    private fun parseBattery(lower: String, lang: Language): CommandIntent? {
        val kw = listOf("battery", "battery level", "charge", "battery percentage",
            "बैटरी", "ব্যাটারি")
        return if (kw.any { lower.contains(it) })
            CommandIntent(CommandType.BATTERY_STATUS, lower, lang = lang)
        else null
    }

    // ─── NOTES ───
    private fun parseNote(lower: String, original: String, lang: Language): CommandIntent? {
        val kw = listOf("save note", "note this", "remember", "write down",
            "नोट", "याद रखो", "নোট", "মনে রাখো")
        if (kw.none { lower.contains(it) }) return null
        val content = original.replace(Regex("(?:save\\s+note|note\\s+this|remember|write\\s+down|नोट|याद रखो|নোট|মনে রাখো)", RegexOption.IGNORE_CASE), "").trim()
        return CommandIntent(CommandType.SAVE_NOTE, original, mapOf("content" to content), lang)
    }

    // ─── CONVERSATION ───
    private fun parseConversation(lower: String, lang: Language): CommandIntent? {
        val helloKw = listOf("hello", "hi", "hey", "howdy",
            "नमस्ते", "हेलो", "নমস্কার", "হ্যালো", "সুপ্রভাত", "good morning")
        val helpKw = listOf("help", "what can you do", "commands",
            "मदद", "क्या कर सकते", "সাহায্য")
        val whoKw = listOf("who are you", "what are you", "your name",
            "तुम कौन हो", "তুমি কে")
        val jokeKw = listOf("joke", "funny", "laugh", "make me laugh",
            "मज़ाक", "जोक", "কৌতুক", "মজা")

        return when {
            helloKw.any { lower.contains(it) } -> CommandIntent(CommandType.GREETING, lower, lang = lang)
            helpKw.any { lower.contains(it) } -> CommandIntent(CommandType.HELP, lower, lang = lang)
            whoKw.any { lower.contains(it) } -> CommandIntent(CommandType.WHO_ARE_YOU, lower, lang = lang)
            jokeKw.any { lower.contains(it) } -> CommandIntent(CommandType.JOKE, lower, lang = lang)
            else -> null
        }
    }
}
