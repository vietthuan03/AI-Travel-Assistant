package com.example.travelassistant.core.ui

import androidx.lifecycle.ViewModelStore
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val store=ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear();dispatcher.scheduler.runCurrent();Dispatchers.resetMain() }
    private class Local : WorkspaceRepository {
        val disk=MutableStateFlow<PlannerDraft?>(null)
        var fail=false
        var failRead=false
        var writes=0
        override val draft: Flow<PlannerDraft?> = flow { if(failRead) throw IOException();emitAll(disk) }
        override suspend fun write(draft: PlannerDraft) { if(fail) throw IOException();writes++;disk.value=draft }
        override suspend fun discard() { disk.value=null }
        override suspend fun removeAllLocalTrips() = discard()
    }
    private val prefs=object : DisplayPreferencesRepository {
        override val preferences=flowOf(DisplayPreferences(currency="EUR"))
        override suspend fun update(currency: String,units: String) = Unit
    }
    private fun model(local: Local)=WorkspaceViewModel(local,prefs).also { store.put("workspace",it) }
    @Test fun autosaveAcknowledgesOnlyAfterPersistenceAndDoesNotRewriteCleanDrafts() = runTest(dispatcher) {
        val repo=Local();val vm=model(repo);runCurrent();vm.startNew()
        assertEquals("EUR",vm.state.value.draft!!.currency)
        vm.edit { it.copy(notes="Keep this note",step=2) }
        assertEquals(DraftStorage.WRITING,vm.state.value.storage)
        advanceTimeBy(351);runCurrent()
        assertEquals("Keep this note",repo.disk.value!!.notes)
        assertEquals(DraftStorage.KEPT,vm.state.value.storage)
        val count=repo.writes;vm.flush();assertEquals(count,repo.writes)
    }
    @Test fun failedWriteKeepsNewInputsInMemoryAndOlderDraftOnDiskUntilRetry() = runTest(dispatcher) {
        val repo=Local();val vm=model(repo);runCurrent();vm.startNew()
        repo.fail=true;vm.edit { it.copy(notes="Newer note") };vm.flush()
        assertEquals(DraftStorage.FAILED,vm.state.value.storage)
        assertEquals("Newer note",vm.state.value.draft!!.notes)
        assertEquals("",repo.disk.value!!.notes)
        repo.fail=false;assertTrue(vm.flush())
        assertEquals("Newer note",repo.disk.value!!.notes)
    }
    @Test fun removingDataCancelsPendingAutosaveAndCannotResurrectTheDraft() = runTest(dispatcher) {
        val repo=Local();val vm=model(repo);runCurrent();vm.startNew()
        vm.edit { it.copy(notes="Must be removed") }
        assertTrue(vm.discard(removeAll=true))
        advanceTimeBy(1000);runCurrent();vm.flush()
        assertNull(repo.disk.value);assertNull(vm.state.value.draft)
    }
    @Test fun failedReadCannotOverwriteTheExistingDraftAndExplicitRetryRestoresIt() = runTest(dispatcher) {
        val repo=Local().apply { disk.value=PlannerDraft(notes="Stored before read failure",step=2);failRead=true }
        val vm=model(repo);runCurrent()
        assertTrue(vm.state.value.readFailed);assertFalse(vm.state.value.loaded)
        vm.edit { it.copy(notes="Must not replace an unread draft") }
        assertEquals(0,repo.writes)
        repo.failRead=false;vm.retryRead();runCurrent()
        assertTrue(vm.state.value.loaded);assertFalse(vm.state.value.readFailed)
        assertEquals("Stored before read failure",vm.state.value.draft!!.notes)
        assertEquals(2,vm.state.value.draft!!.step)
        assertEquals(DraftStorage.KEPT,vm.state.value.storage)
    }

}
