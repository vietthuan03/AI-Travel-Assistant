package com.example.ai_travel_assistant.data.ai

import com.example.ai_travel_assistant.domain.trip.GenerationException
import com.example.ai_travel_assistant.domain.trip.PlanFailure
import kotlinx.coroutines.CancellationException

internal class TravelModelFallback(
    private val elapsedMillis: () -> Long,
    private val classify: (Exception) -> PlanFailure = { it.toPlanFailure() },
    private val reportFailure: (String, PlanFailure, Exception?) -> Unit,
) {
    private val models = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.6-flash")
    private val unavailableUntil = mutableMapOf<String, Long>()
    private var lastFallbackFailure = PlanFailure.Quota

    suspend fun generate(request: suspend (String) -> String?): String {
        for (model in models) {
            if ((unavailableUntil[model] ?: 0L) > elapsedMillis()) continue
            val text = try {
                request(model)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                val failure = classify(error)
                reportFailure(model, failure, error)
                val cooldown = when (failure) {
                    PlanFailure.Quota -> 60_000L
                    PlanFailure.ModelUnavailable -> 600_000L
                    else -> throw GenerationException(failure)
                }
                unavailableUntil[model] = elapsedMillis() + cooldown
                lastFallbackFailure = failure
                if (failure == PlanFailure.Quota && error.isSharedFirebaseRateLimit()) {
                    val limitUntil = elapsedMillis() + cooldown
                    models.forEach { candidate ->
                        val previous = unavailableUntil[candidate] ?: 0L
                        unavailableUntil[candidate] = maxOf(previous, limitUntil)
                    }
                    throw GenerationException(failure)
                }
                continue
            }
            if (!text.isNullOrBlank()) return text
            reportFailure(model, PlanFailure.EmptyResponse, null)
            throw GenerationException(PlanFailure.EmptyResponse)
        }
        throw GenerationException(lastFallbackFailure)
    }
}

private fun Throwable.isSharedFirebaseRateLimit(): Boolean {
    val message = this.message.orEmpty().lowercase()
    val firebaseService = "firebase ai logic" in message || "firebasevertexai.googleapis.com" in message
    return firebaseService && ("per user" in message || "per-user" in message || "generatecontent requests" in message)
}
