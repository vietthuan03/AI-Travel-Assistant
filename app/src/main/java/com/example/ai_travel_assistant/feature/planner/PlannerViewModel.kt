package com.example.ai_travel_assistant.feature.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_travel_assistant.domain.trip.GenerateTrip
import com.example.ai_travel_assistant.domain.trip.GenerationException
import com.example.ai_travel_assistant.domain.trip.TripRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class PlannerViewModel(
    private val generateTrip: GenerateTrip,
    private val openAiGenerateTrip: (String) -> GenerateTrip,
) : ViewModel() {
    private var openAiApiKey: String? = null
    private val openAiEnabledState = MutableStateFlow(false)
    val openAiEnabled = openAiEnabledState.asStateFlow()

    fun setOpenAiApiKey(key: String?) {
        openAiApiKey = key
        openAiEnabledState.value = key != null
    }

    private val state = MutableStateFlow<PlannerUiState>(PlannerUiState.Initial)
    val uiState = state.asStateFlow()

    fun planTrip(request: TripRequest, language: String) {
        if (!request.hasRequiredDetails || state.value == PlannerUiState.Loading) return
        val generate = openAiApiKey?.let(openAiGenerateTrip) ?: generateTrip
        state.value = PlannerUiState.Loading
        viewModelScope.launch {
            state.value = try {
                val result = generate(request, language)
                PlannerUiState.Success(result.itinerary, request, saveFailed = !result.saved)
            } catch (failure: GenerationException) {
                PlannerUiState.Error(failure.failure)
            }
        }
    }
}
