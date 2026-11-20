package com.example.ai_travel_assistant.feature.planner

import com.example.ai_travel_assistant.domain.trip.Itinerary
import com.example.ai_travel_assistant.domain.trip.PlanFailure
import com.example.ai_travel_assistant.domain.trip.TripRequest

internal sealed interface PlannerUiState {

    data object Initial : PlannerUiState
    data object Loading : PlannerUiState
    data class Success(
        val itinerary: Itinerary,
        val request: TripRequest,
        val saveFailed: Boolean = false,
    ) : PlannerUiState
    data class Error(val failure: PlanFailure) : PlannerUiState
}
