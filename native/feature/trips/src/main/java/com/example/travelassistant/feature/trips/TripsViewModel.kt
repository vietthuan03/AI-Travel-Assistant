package com.example.travelassistant.feature.trips
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelassistant.core.domain.TripRepository
import com.example.travelassistant.core.domain.SavedTripSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi

enum class TripsReadState { LOADING, READY, FAILED }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripsViewModel @Inject constructor(repository: TripRepository) : ViewModel() {
    private val attempts = MutableStateFlow(0)
    private val content = MutableStateFlow<List<SavedTripSummary>>(emptyList())
    val savedTrips = content.asStateFlow()
    val state = attempts.flatMapLatest {
        repository.savedTrips.map { rows -> content.value=rows; TripsReadState.READY }
            .onStart { emit(TripsReadState.LOADING) }
            .catch { emit(TripsReadState.FAILED) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripsReadState.LOADING)

    fun retry() {
        if (state.value == TripsReadState.FAILED) attempts.update { it + 1 }
    }
}
