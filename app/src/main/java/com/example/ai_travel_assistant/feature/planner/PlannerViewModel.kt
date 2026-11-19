package com.example.ai_travel_assistant.feature.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_travel_assistant.domain.trip.GenerateTrip
import com.example.ai_travel_assistant.domain.trip.GenerationException
import com.example.ai_travel_assistant.domain.trip.TripRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class PlannerViewModel(private val generateTrip: GenerateTrip) : ViewModel() {
    private val state = MutableStateFlow<PlannerUiState>(PlannerUiState.Initial)
    val uiState = state.asStateFlow()

    fun planTrip(request: TripRequest, language: String) {
        if (!request.hasRequiredDetails || state.value == PlannerUiState.Loading) return
        state.value = PlannerUiState.Loading
        viewModelScope.launch {
            state.value = try {
                val result = generateTrip(request, language)
                PlannerUiState.Success(result.itinerary, saveFailed = !result.saved)
            } catch (failure: GenerationException) {
                PlannerUiState.Error(failure.failure)
            }
        }
    }
}
