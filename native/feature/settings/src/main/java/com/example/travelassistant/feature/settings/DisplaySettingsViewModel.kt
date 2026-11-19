package com.example.travelassistant.feature.settings

import androidx.lifecycle.*
import com.example.travelassistant.core.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DisplaySettingsState(val preferences: DisplayPreferences = DisplayPreferences(),val loaded: Boolean=false,val saving: Boolean=false,val error: SettingsError?=null)
@HiltViewModel
class DisplaySettingsViewModel @Inject constructor(private val repository: DisplayPreferencesRepository) : ViewModel() {
    private val mutable=MutableStateFlow(DisplaySettingsState())
    val state=mutable.asStateFlow()
    private var reading: Job?=null
    private var attempt: DisplayPreferences?=null
    init { read() }
    private fun read() {
        reading?.cancel()
        mutable.update { it.copy(error=null) }
        reading=viewModelScope.launch {
            repository.preferences.catch { mutable.update { it.copy(error=SettingsError.READ,loaded=false) } }
                .collect { p -> mutable.update { it.copy(preferences=p,loaded=true) } }
        }
    }
    fun update(value: DisplayPreferences) {
        if(!mutable.value.loaded || mutable.value.saving) return
        attempt=value;mutable.update { it.copy(saving=true,error=null) }
        viewModelScope.launch {
            try { repository.update(value.currency,value.units);attempt=null }
            catch(cancel: CancellationException) { throw cancel }
            catch(_: Exception) { mutable.update { it.copy(error=SettingsError.SAVE) } }
            finally { mutable.update { it.copy(saving=false) } }
        }
    }
    fun retry() { if(mutable.value.error==SettingsError.READ) read() else attempt?.let(::update) }
}
