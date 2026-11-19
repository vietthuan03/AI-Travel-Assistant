package com.example.travelassistant.feature.settings
import androidx.lifecycle.ViewModelStore
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DisplaySettingsViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val store=ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear();dispatcher.scheduler.runCurrent();Dispatchers.resetMain() }
    @Test fun failedPreferenceWriteRetainsOldValuesAndExplicitRetryAppliesNewValues() = runTest(dispatcher) {
        var failed=true
        val values=MutableStateFlow(DisplayPreferences())
        val repo=object : DisplayPreferencesRepository {
            override val preferences=values
            override suspend fun update(currency: String,units: String) { if(failed) throw IOException();values.value=DisplayPreferences(currency,units) }
        }
        val vm=DisplaySettingsViewModel(repo).also { store.put("display",it) };runCurrent()
        vm.update(DisplayPreferences("EUR","fahrenheit"));runCurrent()
        assertEquals(DisplayPreferences(),vm.state.value.preferences)
        assertEquals(SettingsError.SAVE,vm.state.value.error)
        failed=false;vm.retry();runCurrent()
        assertEquals(DisplayPreferences("EUR","fahrenheit"),vm.state.value.preferences)
        assertNull(vm.state.value.error)
    }
}
