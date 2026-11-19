package com.example.travelassistant.feature.tools

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ToolViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val store=ViewModelStore()
    private var failRead=true
    private val prefs=object : DisplayPreferencesRepository {
        override val preferences=flow { if(failRead) throw IOException();emit(DisplayPreferences("EUR","fahrenheit")) }
        override suspend fun update(currency: String,units: String) = Unit
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear();dispatcher.scheduler.runCurrent();Dispatchers.resetMain() }
    private fun model()=ToolViewModel(SavedStateHandle(),prefs).also { store.put("tool",it) }
    @Test fun explicitReadRetryRestoresBothDefaultsWhenInputsWereUntouched() = runTest(dispatcher) {
        val vm=model();runCurrent();assertTrue(vm.preferenceFailed.value)
        failRead=false;vm.retryPreferences();runCurrent()
        assertFalse(vm.preferenceFailed.value)
        assertEquals("EUR",vm.inputs.value.from)
        assertEquals("fahrenheit",vm.inputs.value.units)
        assertTrue(vm.inputs.value.initialized)
    }
    @Test fun retryDoesNotReplaceAUsersPairOrAmountAfterAReadFailure() = runTest(dispatcher) {
        val vm=model();runCurrent()
        vm.input("amount","42,50");vm.input("from","JPY");vm.input("to","GBP")
        failRead=false;vm.retryPreferences();runCurrent()
        assertEquals("42,50",vm.inputs.value.amount)
        assertEquals("JPY",vm.inputs.value.from);assertEquals("GBP",vm.inputs.value.to)
        assertFalse(vm.preferenceFailed.value)
    }
}
