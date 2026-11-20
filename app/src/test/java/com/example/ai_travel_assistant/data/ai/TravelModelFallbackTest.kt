package com.example.ai_travel_assistant.data.ai

import com.example.ai_travel_assistant.domain.trip.GenerationException
import com.example.ai_travel_assistant.domain.trip.PlanFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TravelModelFallbackTest {
    @Test fun perModelQuotaSkipsModelUntilCooldownExpires() = runBlocking {
        var now = 0L
        val fallback = TravelModelFallback({ now }, { PlanFailure.Quota }) { _, _, _ -> }
        val calls = mutableListOf<String>()
        fallback.generate { model ->
            calls += model
            if (calls.size == 1) throw IllegalStateException("quota")
            "ok"
        }
        fallback.generate { model -> calls += model; "ok" }
        now = 60_000
        fallback.generate { model -> calls += model; "ok" }
        assertEquals(listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.5-flash-lite", "gemini-3.8-flash"), calls)
    }

    @Test fun sharedQuotaStopsFurtherRequests() = runBlocking {
        val fallback = TravelModelFallback({ 0L }, { PlanFailure.Quota }) { _, _, _ -> }
        var calls = 0
        repeat(2) {
            val failure = runCatching {
                fallback.generate {
                    calls++
                    throw IllegalStateException("Request failed", IllegalStateException("Firebase AI Logic per-user quota"))
                }
            }.exceptionOrNull() as GenerationException
            assertEquals(PlanFailure.Quota, failure.failure)
        }
        assertEquals(1, calls)
    }

    @Test fun quotaTriesAllConfiguredModelsInOrder() = runBlocking {
        val fallback = TravelModelFallback({ 0L }, { PlanFailure.Quota }) { _, _, _ -> }
        val calls = mutableListOf<String>()
        val itinerary = fallback.generate { model ->
            calls += model
            if (model != "gemini-3.1-flash-lite") throw IllegalStateException("quota")
            "itinerary"
        }
        assertEquals("itinerary", itinerary)
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-hydrogen",
                "gemini-3.6-flash", "gemini-3.1-flash-lite"),
            calls,
        )
    }

    @Test fun tokenLimitTriesAnotherModelWithoutPuttingFirstModelOnCooldown() = runBlocking {
        val fallback = TravelModelFallback({ 0L }, { PlanFailure.TokenLimit }) { _, _, _ -> }
        val calls = mutableListOf<String>()
        assertEquals("itinerary", fallback.generate { model ->
            calls += model
            if (calls.size == 1) throw IllegalStateException("MAX_TOKENS")
            "itinerary"
        })
        fallback.generate { model -> calls += model; "shorter itinerary" }
        assertEquals(listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.8-flash"), calls)
    }

    @Test fun unavailableModelsDoNotHideQuotaFromOtherModels() = runBlocking {
        val fallback = TravelModelFallback({ 0L }, { error ->
            if (error.message == "quota") PlanFailure.Quota else PlanFailure.ModelUnavailable
        }) { _, _, _ -> }
        val failure = runCatching {
            fallback.generate { model ->
                throw IllegalStateException(if (model == "gemini-3.8-flash") "quota" else "not found")
            }
        }.exceptionOrNull() as GenerationException
        assertEquals(PlanFailure.Quota, failure.failure)

        val cooldownFailure = runCatching {
            fallback.generate { fail("Cooling down models should not be requested"); null }
        }.exceptionOrNull() as GenerationException
        assertEquals(PlanFailure.Quota, cooldownFailure.failure)
    }

    @Test fun blankResponseKeepsItsFailureReason() = runBlocking {
        val fallback = TravelModelFallback({ 0L }, { PlanFailure.Quota }) { _, _, _ -> }
        val failure = runCatching { fallback.generate { " " } }.exceptionOrNull() as GenerationException
        assertEquals(PlanFailure.EmptyResponse, failure.failure)
    }
}
