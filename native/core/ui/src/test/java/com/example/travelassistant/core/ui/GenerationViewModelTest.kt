package com.example.travelassistant.core.ui

import androidx.lifecycle.ViewModelStore
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationViewModelTest {
 private val dispatcher=StandardTestDispatcher()
 private val store=ViewModelStore()
 @Before fun before(){Dispatchers.setMain(dispatcher)}
 @After fun after(){store.clear();dispatcher.scheduler.runCurrent();Dispatchers.resetMain()}
 private class Fake : GenerationRepository {
  override val state=MutableStateFlow(GenerationState(status="UNKNOWN"))
  override val mockAvailable=true
  var starts=0;var refreshes=0;var startError: String?=null
  var cancelGate: CompletableDeferred<Unit>?=null
  override suspend fun begin(mock: Boolean){startError?.let{throw GenerationException(it)};starts++;state.value=GenerationState(status="FAILED",error="AI_RATE_LIMITED")}
  override suspend fun refresh(){refreshes++}
  override suspend fun cancel(){cancelGate?.await();state.value=GenerationState(status="CANCELED")}
 }
 private fun vm(fake: Fake)=GenerationViewModel(fake).also{store.put("generation",it)}
 @Test fun cancelMustFinishBeforeAnotherGenerationCanStart()=runTest(dispatcher){
  val fake=Fake().apply{cancelGate=CompletableDeferred()};val vm=vm(fake);vm.cancel();runCurrent()
  assertTrue(vm.busy.value);assertFalse(vm.begin());assertEquals("REQUEST_BUSY",vm.actionError.value);assertEquals(0,fake.starts)
  fake.cancelGate!!.complete(Unit);runCurrent();assertFalse(vm.busy.value)
  assertTrue(vm.begin());runCurrent();assertEquals(1,fake.starts)
 }
 @Test fun repeatedStatusChecksNeverStartAPaidRequest()=runTest(dispatcher){
  val fake=Fake();val vm=vm(fake);vm.resume();vm.resume();runCurrent();advanceTimeBy(10000);runCurrent()
  assertEquals(0,fake.starts);assertEquals(1,fake.refreshes)
 }
 @Test fun sessionRateLimitIsPreservedAsTypedActionError()=runTest(dispatcher){
  val fake=Fake().apply{startError="SESSION_RATE_LIMIT"};val vm=vm(fake)
  assertFalse(vm.begin());assertEquals("SESSION_RATE_LIMIT",vm.actionError.value);assertTrue(vm.actionFailed.value);assertFalse(vm.busy.value);assertEquals(0,fake.starts)
 }
}
