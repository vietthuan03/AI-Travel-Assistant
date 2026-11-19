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
                    throw IllegalStateException("Firebase AI Logic per-user quota")
                }
            }.exceptionOrNull() as GenerationException
            assertEquals(PlanFailure.Quota, failure.failure)
        }
        assertEquals(1, calls)
    }

    @Test fun blankResponseKeepsItsFailureReason() = runBlocking {
        val fallback = TravelModelFallback({ 0L }, { PlanFailure.Quota }) { _, _, _ -> }
        val failure = runCatching { fallback.generate { " " } }.exceptionOrNull() as GenerationException
        assertEquals(PlanFailure.EmptyResponse, failure.failure)
    }
}
