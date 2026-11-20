package com.example.ai_travel_assistant.data.ai

import com.example.ai_travel_assistant.domain.trip.GenerationException
import com.example.ai_travel_assistant.domain.trip.Itinerary
import com.example.ai_travel_assistant.domain.trip.PlanFailure
import com.example.ai_travel_assistant.domain.trip.TripGenerator
import com.example.ai_travel_assistant.domain.trip.TripRequest
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

private const val ResponsesUrl = "https://api.openai.com/v1/responses"
private const val ItineraryModel = "gpt-5-mini"
private const val ConnectTimeoutMillis = 15_000
private const val ReadTimeoutMillis = 90_000

internal class OpenAiTripGenerator(private val apiKey: String) : TripGenerator {
    override suspend fun generate(request: TripRequest, language: String): Itinerary =
        withContext(Dispatchers.IO) {
            try {
                readItinerary(readOpenAiText(createResponse(request.prompt(language))))
            } catch (_: SocketTimeoutException) {
                throw GenerationException(PlanFailure.Timeout)
            } catch (_: IOException) {
                throw GenerationException(PlanFailure.Network)
            } catch (_: JSONException) {
                throw GenerationException(PlanFailure.Unknown)
            }
        }

    private fun createResponse(prompt: String): JSONObject {
        val body = JSONObject()
            .put("model", ItineraryModel)
            .put("input", prompt)
            .put("store", false)
            .put("text", JSONObject().put("format", JSONObject().put("type", "json_object")))
            .toString().toByteArray(Charsets.UTF_8)

        val connection = URL(ResponsesUrl).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = ConnectTimeoutMillis
            connection.readTimeout = ReadTimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }

            val status = connection.responseCode
            if (status !in 200..299) throw GenerationException(openAiHttpFailure(status))
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }
}

private fun openAiHttpFailure(status: Int): PlanFailure = when (status) {
    401, 403 -> PlanFailure.Configuration
    404 -> PlanFailure.ModelUnavailable
    408, 504 -> PlanFailure.Timeout
    429 -> PlanFailure.Quota
    else -> PlanFailure.Unknown
}

internal fun readOpenAiText(response: JSONObject): String {
    if (response.optString("status") == "incomplete") {
        val failure = when (response.optJSONObject("incomplete_details")?.optString("reason")) {
            "max_output_tokens" -> PlanFailure.TokenLimit
            "content_filter" -> PlanFailure.Blocked
            else -> PlanFailure.Unknown
        }
        throw GenerationException(failure)
    }
    if (response.optString("status") != "completed") throw GenerationException(PlanFailure.Unknown)
    val output = response.optJSONArray("output") ?: throw GenerationException(PlanFailure.EmptyResponse)
    val text = buildString {
        for (index in 0 until output.length()) {
            val message = output.optJSONObject(index) ?: continue
            if (message.optString("type") != "message") continue
            val content = message.optJSONArray("content") ?: continue
            for (partIndex in 0 until content.length()) {
                val part = content.optJSONObject(partIndex) ?: continue
                when (part.optString("type")) {
                    "output_text" -> append(part.optString("text"))
                    "refusal" -> throw GenerationException(PlanFailure.Blocked)
                }
            }
        }
    }
    return text.takeIf { it.isNotBlank() } ?: throw GenerationException(PlanFailure.EmptyResponse)
}
