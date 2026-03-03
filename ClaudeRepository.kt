package com.nova.ai.ai

import android.content.Context
import com.google.gson.Gson
import com.nova.ai.models.*
import com.nova.ai.utils.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClaudeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: SecureStorage
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Conversation memory — last N turns
    private val conversationHistory = mutableListOf<ClaudeMessage>()
    private val MAX_HISTORY = 10

    suspend fun askClaude(
        question: String,
        language: Language = Language.ENGLISH
    ): CommandResult = withContext(Dispatchers.IO) {
        val apiKey = storage.getClaudeApiKey()
        if (apiKey.isBlank()) {
            return@withContext CommandResult.Error(
                noKeyMessage(language)
            )
        }

        try {
            val assistantName = storage.getString(SecureStorage.KEY_ASSISTANT_NAME, "NOVA")
            val langInstruction = when (language) {
                Language.HINDI -> "Always reply in Hindi (हिंदी). Be concise, 2-4 sentences max."
                Language.BENGALI -> "Always reply in Bengali (বাংলা). Be concise, 2-4 sentences max."
                else -> "Reply in English. Be concise, 2-4 sentences max."
            }

            val systemPrompt = """
                You are $assistantName, an AI voice assistant on someone's Android phone.
                $langInstruction
                You help with phone tasks, answer questions, and have friendly conversations.
                Keep responses SHORT — this will be read aloud by text-to-speech.
                Do NOT use markdown formatting, bullet points, or special characters.
                Use simple, natural speech-friendly language.
            """.trimIndent()

            // Add to history
            conversationHistory.add(ClaudeMessage("user", question))
            if (conversationHistory.size > MAX_HISTORY * 2) {
                conversationHistory.removeAt(0)
                conversationHistory.removeAt(0)
            }

            val requestBody = ClaudeRequest(
                model = "claude-haiku-4-5-20251001",
                max_tokens = 400,
                system = systemPrompt,
                messages = conversationHistory.toList()
            )

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return@withContext CommandResult.Error(
                        "AI error ${response.code}: ${parseErrorMessage(errorBody, language)}"
                    )
                }

                val body = response.body?.string() ?: return@withContext CommandResult.Error("Empty response")
                val claude = gson.fromJson(body, ClaudeResponse::class.java)
                val reply = claude.content.firstOrNull()?.text ?: "No response"

                // Add assistant reply to history
                conversationHistory.add(ClaudeMessage("assistant", reply))

                CommandResult.Success(reply)
            }
        } catch (e: java.net.UnknownHostException) {
            CommandResult.Error(noNetworkMessage(language))
        } catch (e: Exception) {
            CommandResult.Error("AI Error: ${e.message}")
        }
    }

    suspend fun generateImage(prompt: String): CommandResult = withContext(Dispatchers.IO) {
        val apiKey = storage.getStabilityApiKey()
        if (apiKey.isBlank()) {
            // Fallback — open web image search
            return@withContext CommandResult.Success("OPEN_URL:https://www.bing.com/images/search?q=${java.net.URLEncoder.encode(prompt, "UTF-8")}")
        }

        try {
            val requestJson = """
                {
                    "text_prompts": [{"text": "$prompt"}],
                    "cfg_scale": 7,
                    "height": 512,
                    "width": 512,
                    "steps": 30,
                    "samples": 1
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://api.stability.ai/v1/generation/stable-diffusion-v1-6/text-to-image")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext CommandResult.Error("Image gen failed")

                val body = response.body?.string() ?: return@withContext CommandResult.Error("Empty response")
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val base64 = json.getAsJsonArray("artifacts")
                    ?.firstOrNull()?.asJsonObject
                    ?.get("base64")?.asString ?: return@withContext CommandResult.Error("No image data")

                // Save to Downloads
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val file = File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    "nova_image_${System.currentTimeMillis()}.png"
                )
                FileOutputStream(file).use { it.write(bytes) }

                // Notify gallery
                android.media.MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)

                CommandResult.Success("IMAGE_SAVED:${file.absolutePath}")
            }
        } catch (e: Exception) {
            CommandResult.Error("Image generation failed: ${e.message}")
        }
    }

    fun clearHistory() = conversationHistory.clear()

    private fun noKeyMessage(lang: Language) = when (lang) {
        Language.HINDI -> "AI Key नहीं मिली। Settings में Claude API Key डालें।"
        Language.BENGALI -> "AI Key পাওয়া যায়নি। Settings-এ Claude API Key দিন।"
        else -> "No AI key found. Go to Settings and add your Claude API key."
    }

    private fun noNetworkMessage(lang: Language) = when (lang) {
        Language.HINDI -> "इंटरनेट नहीं है। कृपया WiFi या Data चालू करें।"
        Language.BENGALI -> "ইন্টারনেট নেই। WiFi বা Data চালু করুন।"
        else -> "No internet connection. Please check your WiFi or data."
    }

    private fun parseErrorMessage(errorBody: String, lang: Language): String {
        return try {
            val json = com.google.gson.JsonParser.parseString(errorBody).asJsonObject
            json.get("error")?.asJsonObject?.get("message")?.asString ?: "Unknown error"
        } catch (e: Exception) {
            "Request failed"
        }
    }
}
