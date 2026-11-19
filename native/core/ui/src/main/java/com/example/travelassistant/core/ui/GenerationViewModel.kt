package com.example.travelassistant.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.domain.GenerationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Durable request identity lives in Room, never in a view or navigation argument. */
@HiltViewModel
class GenerationViewModel @Inject constructor(private val repository: GenerationRepository): ViewModel() {
    val state=repository.state.stateIn(viewModelScope,SharingStarted.Eagerly,GenerationState())
    val mockAvailable get()=repository.mockAvailable
    val actionFailed=MutableStateFlow(false)
    val actionError=MutableStateFlow<String?>(null)
    val busy=MutableStateFlow(false)
    private val starts=Mutex()
    private var polling: Job?=null
    suspend fun begin(mock: Boolean=false): Boolean = viewModelScope.async {
        if(busy.value){actionError.value="REQUEST_BUSY";return@async false}
        starts.withLock {
            busy.value=true;actionFailed.value=false;actionError.value=null
            try {repository.begin(mock);resume();true}
            catch(cancel: CancellationException){throw cancel}
            catch(error: Exception){actionError.value=(error as? GenerationException)?.reason;actionFailed.value=true;false}
            finally {busy.value=false}
        }
    }.await()
    fun resume() {
        if(polling?.isActive==true)return
        polling=viewModelScope.launch {
            val deadline=System.currentTimeMillis()+120_000
            do {
                try {repository.refresh()} catch(cancel: CancellationException){throw cancel} catch(_: Exception){actionFailed.value=true;return@launch}
                val current=repository.state.first()
                if(current.status !in setOf("PENDING","QUEUED","RUNNING"))return@launch
                delay(2000)
            } while(System.currentTimeMillis()<deadline)
        }
    }
    fun cancel() {
        if(busy.value)return
        busy.value=true;actionError.value=null;actionFailed.value=false
        polling?.cancel();polling=null
        viewModelScope.launch {
            starts.withLock {
                try {repository.cancel()}
                catch(cancel: CancellationException){throw cancel}
                catch(error: Exception){actionError.value=(error as? GenerationException)?.reason;actionFailed.value=true}
                finally {busy.value=false}
            }
        }
    }
}
