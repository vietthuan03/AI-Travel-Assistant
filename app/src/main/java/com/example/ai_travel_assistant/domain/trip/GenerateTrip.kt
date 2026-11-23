package com.example.ai_travel_assistant.domain.trip

import kotlinx.coroutines.CancellationException

internal fun interface TripGenerator {
    suspend fun generate(request: TripRequest, language: String): Itinerary
}

internal class GenerationException(val failure: PlanFailure) : Exception(failure.name)

internal enum class PlanFailure {
    Network, Timeout, Quota, ModelUnavailable, TokenLimit, Configuration, Blocked, EmptyResponse, InvalidSchedule, Unknown
}

internal data class GeneratedTrip(val itinerary: Itinerary, val saved: Boolean)

internal class GenerateTrip(
    private val generator: TripGenerator,
    private val save: suspend (TripRequest, Itinerary) -> Unit,
) {
    suspend operator fun invoke(request: TripRequest, language: String): GeneratedTrip {
        require(request.hasRequiredDetails)
        val itinerary = generator.generate(request, language)
        if (itinerary.days == null) return GeneratedTrip(itinerary, saved = false)
        val saved = try {
            save(request, itinerary)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        return GeneratedTrip(itinerary, saved)
    }
}
