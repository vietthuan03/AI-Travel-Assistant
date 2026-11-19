package com.example.travelassistant.feature.tools

import androidx.lifecycle.*
import com.example.travelassistant.core.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ToolInputs(val amount: String = "", val from: String = "USD", val to: String = "VND",
    val city: String = "", val query: String = "", val units: String = "celsius", val status: String = "",
    val touched: Boolean = false, val initialized: Boolean = false) : java.io.Serializable

@HiltViewModel
class ToolViewModel @Inject constructor(private val handle: SavedStateHandle,
    private val preferences: DisplayPreferencesRepository) : ViewModel() {
    val inputs=handle.getStateFlow("inputs",ToolInputs())
    val preferenceFailed=MutableStateFlow(false)
    val preferenceSaving=MutableStateFlow(false)
    private var pendingUnit: String?=null
    val preferenceWriteFailed: Boolean get() = pendingUnit!=null
    private fun edit(update: (ToolInputs)->ToolInputs) { handle["inputs"]=update(inputs.value) }
    init {
        if(!inputs.value.initialized) viewModelScope.launch {
            try {
                val p=preferences.preferences.first()
                edit { if(!it.touched) it.copy(from=p.currency,units=p.units,initialized=true) else it.copy(initialized=true) }
            } catch(cancel: CancellationException) { throw cancel }
            catch(_: Exception) { preferenceFailed.value=true }
        }
    }
    fun input(key: String,value: String) = edit { d -> when(key) {
        "amount" -> d.copy(amount=value,status="",touched=true)
        "from" -> d.copy(from=value,status="",touched=true)
        "to" -> d.copy(to=value,status="",touched=true)
        "query" -> d.copy(query=value,city="",status="",touched=true)
        "city" -> d.copy(city=value,status="",touched=true)
        else -> d
    } }
    fun selectCity(id: String,label: String) = edit { it.copy(city=id,query=label,status="",touched=true) }
    fun swap() = edit { it.copy(from=it.to,to=it.from,status="",touched=true) }
    fun setStatus(value: String) = edit { it.copy(status=value) }
    fun saveUnits(value: String,preview: Boolean) {
        if(preview) { edit { it.copy(units=value,touched=true) }; return }
        if(preferenceSaving.value) return
        pendingUnit=value
        preferenceSaving.value=true
        viewModelScope.launch {
            try {
                val old=preferences.preferences.first()
                preferences.update(old.currency,value)
                edit { it.copy(units=value,touched=true) };pendingUnit=null;preferenceFailed.value=false
            } catch(cancel: CancellationException) { throw cancel }
            catch(_: Exception) { preferenceFailed.value=true }
            finally { preferenceSaving.value=false }
        }
    }
    fun retryPreferences() {
        pendingUnit?.let { saveUnits(it,false); return }
        viewModelScope.launch {
            try {
                val p=preferences.preferences.first()
                edit { if(!it.initialized && !it.touched) it.copy(from=p.currency,units=p.units,initialized=true) else it.copy(initialized=true) };preferenceFailed.value=false
            } catch(cancel: CancellationException) { throw cancel }
            catch(_: Exception) { preferenceFailed.value=true }
        }
    }
}

class WorldTimeViewModel(private val handle: SavedStateHandle) : ViewModel() {
    val zone=handle.getStateFlow("zone","Asia/Ho_Chi_Minh")
    fun select(zone: String) { handle["zone"]=zone }
    val now=flow { while(true) { emit(Instant.now());delay(1_000) } }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),Instant.now())
}
