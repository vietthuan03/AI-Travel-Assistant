package com.example.travelassistant.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelassistant.core.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

enum class DraftStorage { IDLE, WRITING, KEPT, FAILED }
data class WorkspaceUiState(val loaded: Boolean = false, val draft: PlannerDraft? = null,
    val storage: DraftStorage = DraftStorage.IDLE, val readFailed: Boolean = false)

@HiltViewModel
class WorkspaceViewModel @Inject constructor(private val repository: WorkspaceRepository,
    private val preferences: DisplayPreferencesRepository) : ViewModel() {
    private val mutable = MutableStateFlow(WorkspaceUiState())
    val state = mutable.asStateFlow()
    private val validation = MutableStateFlow<Set<BriefIssue>>(emptySet())
    val issues = validation.asStateFlow()
    private val commits=Mutex()
    val failedCandidate=MutableStateFlow<String?>(null)
    suspend fun commitPlan(store: PlanStore,id: String,confirmed: Boolean): SaveResult = viewModelScope.async {
        commits.withLock {
            try {
                check(flush())
                store.save(id,confirmed).also { if(it is SaveResult.Saved) {failedCandidate.value=null;reloadFromDisk()} }
            } catch(cancel: CancellationException) {throw cancel}
            catch(error: Exception) {failedCandidate.value=id;throw error}
        }
    }.await()
    private var pending: Job? = null
    private var reading: Job? = null
    private var revision = 0L
    private val writes = Mutex()

    init { retryRead() }
    suspend fun reloadFromDisk() {
        pending?.cancelAndJoin();pending=null;reading?.cancelAndJoin()
        revision++;validation.value=emptySet();mutable.value=WorkspaceUiState();retryRead()
    }
    fun retryRead() {
        reading?.cancel()
        reading = viewModelScope.launch {
            repository.draft.catch {
                mutable.update { it.copy(readFailed = true) }
            }.collect { stored ->
                if (!mutable.value.loaded) mutable.value = WorkspaceUiState(true, stored,
                    if (stored == null) DraftStorage.IDLE else DraftStorage.KEPT)
                else mutable.update { it.copy(readFailed = false) }
            }
        }
    }

    suspend fun startNew() {
        if (mutable.value.draft != null) return
        val currency = try { preferences.preferences.first().currency }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { "USD" }
        edit { PlannerDraft(currency = currency) }
        flush()
    }

    fun edit(clearValidation: Boolean = true, change: (PlannerDraft) -> PlannerDraft) {
        if (!mutable.value.loaded || mutable.value.readFailed) return
        if (clearValidation) validation.value = emptySet()
        revision++
        mutable.update { it.copy(draft = change(it.draft ?: PlannerDraft()), storage = DraftStorage.WRITING) }
        pending?.cancel()
        pending = viewModelScope.launch { delay(350); persistSnapshot() }
    }

    private suspend fun persistSnapshot(): Boolean = writes.withLock {
        if (mutable.value.storage !in setOf(DraftStorage.WRITING,DraftStorage.FAILED)) return@withLock true
        val snapshot = mutable.value.draft ?: return@withLock true
        val version = revision
        try {
            repository.write(snapshot)
            if (version == revision) mutable.update { it.copy(storage = DraftStorage.KEPT) }
            true
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) {
            if (version == revision) mutable.update { it.copy(storage = DraftStorage.FAILED) }
            false
        }
    }

    suspend fun flush(): Boolean {
        pending?.cancelAndJoin()
        pending = null
        return persistSnapshot()
    }
    fun flushSoon() { viewModelScope.launch { flush() } }
    fun validateStep(step: Int): Set<BriefIssue> {
        val draft=mutable.value.draft ?: PlannerDraft()
        return BriefValidator.validate(draft,BriefValidator.today(draft),step).also { validation.value=it }
    }

    suspend fun discard(removeAll: Boolean = false): Boolean {
        pending?.cancelAndJoin()
        pending = null
        return writes.withLock {
            try {
                if (removeAll) repository.removeAllLocalTrips() else repository.discard()
                revision++
                mutable.value = WorkspaceUiState(loaded = true)
                true
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.update { it.copy(storage = DraftStorage.FAILED) }; false }
        }
    }
}
