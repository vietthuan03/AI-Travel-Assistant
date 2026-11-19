package com.example.travelassistant.feature.trips

import androidx.lifecycle.ViewModelStore
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModelTest {
    @Test fun readFailureRemainsRecoverableAndSuccessClearsTheError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val models = ViewModelStore()
        var collector: Job? = null
        try {
            var failRead = true
            var reads = 0
            val repository = object : TripRepository {
                override val savedTrips = flow<List<SavedTripSummary>> {
                    reads++
                    if (failRead) throw IOException("local read failure")
                    emit(emptyList())
                }
            }
            val model = TripsViewModel(repository).also { models.put("trips", it) }
            collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
            runCurrent()
            assertEquals(TripsReadState.FAILED, model.state.value)
            assertEquals(1, reads)
            failRead = false
            model.retry()
            runCurrent()
            assertEquals(TripsReadState.READY, model.state.value)
            assertEquals(2, reads)
            model.retry()
            runCurrent()
            assertEquals(2, reads)
        } finally {
            collector?.cancelAndJoin()
            models.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
