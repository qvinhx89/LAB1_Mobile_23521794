package com.example.homework2sentiment.repository

import com.example.homework2sentiment.network.Content
import com.example.homework2sentiment.network.GeminiApi
import com.example.homework2sentiment.network.GeminiRequest
import com.example.homework2sentiment.network.Part
import kotlinx.coroutines.delay
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SentimentRepository {

    private val api: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApi::class.java)
    }

    suspend fun classify(apiKey: String, text: String): SentimentResult {
        val prompt = """
            You are a sentiment classifier.
            Classify the input sentence into one label: positive, neutral, or negative.
            Return ONLY valid JSON with this schema:
            {
              "label": "positive|neutral|negative",
              "confidence": 0.0,
              "reason": "short explanation"
            }
            Input: "$text"
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            )
        )

        val response = executeWithRetry(apiKey, request)
        val rawText = response.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?.trim()
            ?: throw IllegalStateException("API did not return a valid response")

        val normalized = extractJson(rawText)
        val json = JSONObject(normalized)

        return SentimentResult(
            label = json.optString("label", "unknown"),
            confidence = json.optDouble("confidence", 0.0),
            reason = json.optString("reason", "No explanation")
        )
    }

    private suspend fun executeWithRetry(apiKey: String, request: GeminiRequest) =
        run {
            val maxAttempts = 2
            var lastError: Exception? = null

            repeat(maxAttempts) { index ->
                try {
                    return@run api.generateContent(apiKey = apiKey, request = request)
                } catch (e: HttpException) {
                    val code = e.code()
                    val isLastAttempt = index == maxAttempts - 1

                    if (code == 429 && !isLastAttempt) {
                        // Small backoff helps when free-tier rate limits are briefly exceeded.
                        delay(1200L)
                    } else {
                        throw SentimentApiException(buildHttpErrorMessage(e))
                    }
                    lastError = e
                } catch (e: Exception) {
                    lastError = e
                    throw SentimentApiException("Khong the ket noi den API. Vui long thu lai.")
                }
            }

            throw SentimentApiException(lastError?.message ?: "Loi khong xac dinh")
        }

    private fun buildHttpErrorMessage(e: HttpException): String {
        val code = e.code()
        val body = e.response()?.errorBody()?.string().orEmpty()

        return when (code) {
            400 -> "Request khong hop le. Kiem tra API key va noi dung dau vao."
            401, 403 -> "API key khong hop le hoac chua duoc cap quyen."
            429 -> {
                val serverMessage = extractServerMessage(body)
                if (serverMessage.isNotBlank()) {
                    "Vuot gioi han su dung (HTTP 429): $serverMessage"
                } else {
                    "Vuot gioi han su dung (HTTP 429). Cho 1-2 phut roi thu lai hoac tao API key moi."
                }
            }
            500, 503 -> "Server AI tam thoi ban. Vui long thu lai sau."
            else -> "Loi HTTP $code khi goi API."
        }
    }

    private fun extractServerMessage(rawBody: String): String {
        if (rawBody.isBlank()) return ""
        return try {
            JSONObject(rawBody)
                .optJSONObject("error")
                ?.optString("message", "")
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractJson(raw: String): String {
        if (raw.startsWith("{") && raw.endsWith("}")) return raw

        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1)
        }
        throw IllegalStateException("Cannot parse JSON from model output: $raw")
    }
}

data class SentimentResult(
    val label: String,
    val confidence: Double,
    val reason: String
)

class SentimentApiException(message: String) : Exception(message)
