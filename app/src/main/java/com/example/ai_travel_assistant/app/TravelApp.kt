package com.example.ai_travel_assistant.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai_travel_assistant.data.ai.FirebaseTripGenerator
import com.example.ai_travel_assistant.data.ai.OpenAiTripGenerator
import com.example.ai_travel_assistant.data.local.RoomTripRepository
import com.example.ai_travel_assistant.data.local.TripsDatabase
import com.example.ai_travel_assistant.domain.trip.GenerateTrip
import com.example.ai_travel_assistant.domain.trip.TripGenerator
import com.example.ai_travel_assistant.feature.history.HistoryViewModel
import com.example.ai_travel_assistant.feature.history.TripHistoryDialog
import com.example.ai_travel_assistant.feature.planner.OpenAiSettingsDialog
import com.example.ai_travel_assistant.feature.planner.PlannerForm
import com.example.ai_travel_assistant.feature.planner.PlannerViewModel

internal class AppContainer(context: Context) {
    val repository by lazy { RoomTripRepository(TripsDatabase.get(context).trips()) }
    fun createPlanner() = PlannerViewModel(
        tripUseCase(FirebaseTripGenerator()),
        openAiGenerateTrip = { key -> tripUseCase(OpenAiTripGenerator(key)) },
    )

    private fun tripUseCase(generator: TripGenerator) = GenerateTrip(generator) { request, itinerary ->
        repository.save(request, itinerary)
    }
}

@Composable
internal fun TravelApp(container: AppContainer, onExit: () -> Unit) {
    val planner = viewModel { container.createPlanner() }
    val state by planner.uiState.collectAsState()
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val openAiEnabled by planner.openAiEnabled.collectAsState()
    var showOpenAiSettings by rememberSaveable { mutableStateOf(false) }
    PlannerForm(
        state = state,
        generate = planner::planTrip,
        onExit = onExit,
        openAiSettings = { showOpenAiSettings = true },
        openHistory = { showHistory = true },
    )
    if (showOpenAiSettings) {
        OpenAiSettingsDialog(
            enabled = openAiEnabled,
            onDismiss = { showOpenAiSettings = false },
            onSave = { key ->
                planner.setOpenAiApiKey(key)
                showOpenAiSettings = false
            },
        )
    }
    if (showHistory) {
        val history = viewModel { HistoryViewModel(container.repository, createSavedStateHandle()) }
        TripHistoryDialog(history) { showHistory = false }
    }
}
