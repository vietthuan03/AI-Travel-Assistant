package com.example.travelassistant.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelassistant.core.domain.ExploreDestination
import com.example.travelassistant.core.domain.SavedDestinationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedDestinationsUiState(
    val ids: Set<String>? = null,
    val readFailed: Boolean = false,
    val writing: Boolean = false,
    val writeFailed: Boolean = false,
)

@HiltViewModel
class SavedDestinationsViewModel @Inject constructor(
    private val repository: SavedDestinationsRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(SavedDestinationsUiState())
    val state = mutable.asStateFlow()
    private var reader: Job? = null

    init { retry() }

    fun retry() {
        reader?.cancel()
        mutable.update { it.copy(readFailed = false) }
        reader = viewModelScope.launch {
            repository.saved.catch {
                mutable.update { current -> current.copy(readFailed = true) }
            }.collect { ids ->
                mutable.update { it.copy(ids = ids, readFailed = false) }
            }
        }
    }

    fun toggle(destination: ExploreDestination) {
        val current = mutable.value
        val saved = current.ids ?: return
        if (current.writing || current.readFailed) return
        mutable.update { it.copy(writing = true, writeFailed = false) }
        viewModelScope.launch {
            try {
                repository.setSaved(destination, destination.placeId !in saved)
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (_: Exception) {
                mutable.update { it.copy(writeFailed = true) }
            } finally {
                mutable.update { it.copy(writing = false) }
            }
        }
    }
}
