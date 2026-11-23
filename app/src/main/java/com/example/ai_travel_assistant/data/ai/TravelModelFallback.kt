package com.example.ai_travel_assistant.data.ai

import com.example.ai_travel_assistant.domain.trip.GenerationException
import com.example.ai_travel_assistant.domain.trip.PlanFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal const val ModelRequestTimeoutMillis = 90_000L
private const val GenerationTimeoutMillis = 180_000L
private const val QuotaCooldownMillis = 60_000L
private const val ModelUnavailableCooldownMillis = 600_000L

internal class TravelModelFallback(
    private val elapsedMillis: () -> Long,
    private val classify: (Exception) -> PlanFailure = { it.toPlanFailure() },
    private val reportFailure: (String, PlanFailure, Exception?) -> Unit,
) {
    private val models = listOf(
        "gemini-3.5-flash-lite",
        "gemini-4-hydrogen",
        "gemini-3.6-flash",
        "gemini-3.1-flash-lite",
    )
    private val cooldowns = mutableMapOf<String, ModelCooldown>()
    private var sharedQuotaUntil = 0L

    suspend fun generate(request: suspend (String) -> String?): String =
        withTimeoutOrNull(GenerationTimeoutMillis) { tryModels(request) }
            ?: throw GenerationException(PlanFailure.Timeout)

    private suspend fun tryModels(request: suspend (String) -> String?): String {
        if (sharedQuotaUntil > elapsedMillis()) throw GenerationException(PlanFailure.Quota)

        val failures = mutableSetOf<PlanFailure>()
        for (model in models) {
            val cooldown = cooldowns[model]
            if (cooldown != null && cooldown.until > elapsedMillis()) {
                failures += cooldown.failure
                continue
            }
            val text = try {
                request(model)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                val failure = classify(error)
                reportFailure(model, failure, error)
                when (failure) {
                    PlanFailure.Quota -> {
                        val until = elapsedMillis() + QuotaCooldownMillis
                        if (error.isSharedFirebaseRateLimit()) {
                            sharedQuotaUntil = until
                            throw GenerationException(failure)
                        }
                        cooldowns[model] = ModelCooldown(failure, until)
                    }
                    PlanFailure.ModelUnavailable -> {
                        cooldowns[model] = ModelCooldown(failure, elapsedMillis() + ModelUnavailableCooldownMillis)
                    }
                    PlanFailure.Timeout -> if (PlanFailure.Timeout in failures) throw GenerationException(failure)
                    PlanFailure.TokenLimit -> Unit
                    else -> throw GenerationException(failure)
                }
                failures += failure
                continue
            }
            if (!text.isNullOrBlank()) return text
            reportFailure(model, PlanFailure.EmptyResponse, null)
            throw GenerationException(PlanFailure.EmptyResponse)
        }
        val failure = when {
            PlanFailure.Timeout in failures -> PlanFailure.Timeout
            PlanFailure.TokenLimit in failures -> PlanFailure.TokenLimit
            PlanFailure.Quota in failures -> PlanFailure.Quota
            else -> PlanFailure.ModelUnavailable
        }
        throw GenerationException(failure)
    }

    private data class ModelCooldown(val failure: PlanFailure, val until: Long)
}

private fun Throwable.isSharedFirebaseRateLimit(): Boolean =
    generateSequence(this) { it.cause?.takeUnless { cause -> cause === it } }.take(10).any { cause ->
        val message = cause.message.orEmpty().lowercase()
        val firebaseService = "firebase ai logic" in message || "firebasevertexai.googleapis.com" in message
        firebaseService && ("per user" in message || "per-user" in message || "generatecontent requests" in message)
    }
