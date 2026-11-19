package com.example.ai_travel_assistant.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_travel_assistant.domain.trip.StoredTrip
import com.example.ai_travel_assistant.domain.trip.TripRepository
import com.example.ai_travel_assistant.domain.trip.TripSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface HistoryState {
    data object Loading : HistoryState
    data object ReadFailed : HistoryState
    data class ListReady(val trips: List<TripSummary>) : HistoryState
    data class Detail(val trip: StoredTrip, val deleting: Boolean = false, val deleteFailed: Boolean = false) : HistoryState
}

internal class HistoryViewModel(
    private val repository: TripRepository,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val state = MutableStateFlow<HistoryState>(HistoryState.Loading)
    val uiState = state.asStateFlow()
    val selectedId = handle.getStateFlow<String?>("selectedTrip", null)
    private var reader: Job? = null

    fun start() {
        if (reader == null) load(selectedId.value)
    }

    fun load(id: String?) {
        reader?.cancel()
        handle["selectedTrip"] = id
        state.value = HistoryState.Loading
        reader = viewModelScope.launch {
            try {
                if (id == null) {
                    repository.summaries().collect { state.value = HistoryState.ListReady(it) }
                } else {
                    state.value = repository.open(id)?.let { HistoryState.Detail(it) } ?: HistoryState.ReadFailed
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = HistoryState.ReadFailed
            }
        }
    }

    fun close() {
        reader?.cancel()
        reader = null
        handle["selectedTrip"] = null
        state.value = HistoryState.Loading
    }

    fun deleteSelected() {
        val detail = state.value as? HistoryState.Detail ?: return
        if (detail.deleting) return
        state.value = detail.copy(deleting = true, deleteFailed = false)
        viewModelScope.launch {
            try {
                repository.delete(detail.trip.summary.id)
                load(null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = detail.copy(deleteFailed = true)
            }
        }
    }
}
