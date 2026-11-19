package com.example.travelassistant

import android.view.View
import android.widget.Button
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelassistant.core.data.*
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.ui.WorkspaceViewModel
import com.example.travelassistant.core.ui.R as UiR
import com.example.travelassistant.feature.planner.R as PlannerR
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Opt-in physical-device Gemini check, maximum two live operations; separate review package only. */
@RunWith(AndroidJUnit4::class)
class GeminiJourneyTest {
 @org.junit.Before fun safeTestServer(){
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("geminiPhase")=="true")
  assertEquals("com.example.travelassistant.geminireview",context.packageName)
 }
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private fun db()=Room.databaseBuilder(context,LocalDatabase::class.java,"travel.db").addMigrations(LocalDatabase.MIGRATION_1_2,LocalDatabase.MIGRATION_2_3,LocalDatabase.MIGRATION_3_4).build()
 private fun waitFor(label: String,condition: ()->Boolean){val end=System.currentTimeMillis()+125000;while(System.currentTimeMillis()<end){if(condition())return;Thread.sleep(100)};fail(label)}
 private fun host(a: MainActivity)=a.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
 private fun click(s: ActivityScenario<MainActivity>,string: Int){
  var clicked=false
  for(position in 0..45){
   s.onActivity { a ->
    val root=host(a).childFragmentManager.primaryNavigationFragment?.view
    if(root is RecyclerView && position<(root.adapter?.itemCount ?: 0))root.scrollToPosition(position)
   }
   InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   s.onActivity {a -> val matches=ArrayList<View>();a.window.decorView.findViewsWithText(matches,a.getString(string),View.FIND_VIEWS_WITH_TEXT);val button=matches.filterIsInstance<Button>().firstOrNull{it.text.toString()==a.getString(string)&&it.isShown&&it.isEnabled};if(button!=null){button.performClick();clicked=true}}
   if(clicked)return
  };fail("Missing action resource $string")
 }
 private fun language(s: ActivityScenario<MainActivity>,value: AppLanguage){var repo: LanguageRepository?=null;s.onActivity{repo=it.languages};runBlocking{repo!!.requestLanguage(value)};waitFor("Language switch"){var ready=false;s.onActivity{ready=it.resources.configuration.locales[0].language==value.tag};ready}}
 private fun route(s: ActivityScenario<MainActivity>,destination: Int){s.onActivity{host(it).navController.navigate(UiR.id.planner_graph);host(it).navController.navigate(destination)};InstrumentationRegistry.getInstrumentation().waitForIdleSync()}
 private fun shot(name: String){val instrument=InstrumentationRegistry.getInstrumentation();val file=java.io.File(context.getExternalFilesDir(null),"$name.png");val bitmap=instrument.uiAutomation.takeScreenshot()!!;try {java.io.FileOutputStream(file).use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}}
 private suspend fun completeLiveOrExplicitMock(s: ActivityScenario<MainActivity>,db: LocalDatabase,store: RoomPlanStore,oldCount: Int,previousRequest: String?=null){
  waitFor("Live terminal result"){runBlocking{db.generation().current()?.let{it.requestId!=previousRequest && it.state in setOf("FAILED","RECEIVED")}==true}}
  val op=db.generation().current()!!
  val dependencies=dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,ReviewDataAccess::class.java)
  val token=dependencies.preferences().data.first()[androidx.datastore.preferences.core.stringPreferencesKey("backend_guest_token")]!!
  val c=java.net.URL("http://127.0.0.1:8787/v1/operations").openConnection() as java.net.HttpURLConnection
  val replay=try{c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer $token");c.outputStream.use{it.write(op.body.toByteArray())};if(op.serverId==null){assertEquals(429,c.responseCode);org.json.JSONObject(c.errorStream.bufferedReader().use{it.readText()})}else{assertEquals(202,c.responseCode);org.json.JSONObject(c.inputStream.bufferedReader().use{it.readText()})}}finally{c.disconnect()}
  if(op.serverId!=null)assertEquals(op.serverId,replay.getString("id"))
  java.io.File(context.getExternalFilesDir(null),"gemini-attempt-$oldCount.json").writeText(org.json.JSONObject().put("state",op.state).put("error",op.error).put("sameOperationOnReplay",op.serverId!=null).put("failureDetails",replay.opt("failureDetails")).toString(2))
  if(oldCount>0){val trip=db.trips().observeSavedTrips().first().single().id;assertEquals(oldCount,store.history(trip).size);assertEquals("100",store.revision(trip,1)!!.brief.budget)}
  if(op.state=="FAILED"){
   assertTrue("Unexpected Gemini error: ${op.error}",op.error in setOf("AI_PROVIDER_HTTP_503","AI_TIMEOUT","DEADLINE_EXCEEDED","AI_RATE_LIMITED","AI_QUOTA_EXHAUSTED","DAILY_QUOTA"))
   delay(2500);assertEquals(op,db.generation().current());shot("gemini-failure-$oldCount")
   click(s,UiR.string.use_mock)
   waitFor("Explicit mock result"){runBlocking{db.generation().current()?.state=="RECEIVED"}}
   assertNotEquals(op.requestId,db.generation().current()!!.requestId);assertEquals("mock",store.candidate()!!.plan.sourceMode)
  }else assertEquals("gemini",store.candidate()!!.plan.sourceMode)
 }
 @Test fun explicit503MockSaveChanges()=runBlocking {
  val db=dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,ReviewDataAccess::class.java).database();val store=RoomPlanStore(db)
  val trip=db.trips().observeSavedTrips().first().single().id;val before=store.history(trip);assertEquals(2,before.size);assertTrue(store.edit(trip,true))
  db.generation().put(db.generation().current()!!.copy(requestId=java.util.UUID.randomUUID().toString(),serverId=null,state="FAILED",error="AI_PROVIDER_HTTP_503",baseTrip=trip,baseVersion=2))
  ActivityScenario.launch(MainActivity::class.java).use{s->route(s,UiR.id.progress_fragment)
   waitFor("503 recovery message"){var visible=false;s.onActivity{a->val found=ArrayList<View>();a.window.decorView.findViewsWithText(found,a.getString(UiR.string.generate_unavailable),View.FIND_VIEWS_WITH_TEXT);visible=found.any{it.isShown}};visible}
   delay(2000);assertEquals(before,store.history(trip));assertNull(store.candidate());shot("gemini-503-preserved")
   click(s,UiR.string.use_mock);waitFor("Explicit mock ready"){runBlocking{store.candidate()?.plan?.sourceMode=="mock"}}
   click(s,UiR.string.open_plan);shot("gemini-explicit-mock-candidate");click(s,UiR.string.save_changes)
   waitFor("Mock saved as v3"){runBlocking{store.history(trip).size==3}}
   assertEquals("mock",store.revision(trip)!!.plan.sourceMode);assertEquals(before,store.history(trip).drop(1))
  }
 }
 @Test fun offlineReopenBothVersions()=runBlocking {
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("offlinePhase")=="true")
  var unreachable=false;try{val c=java.net.URL("http://127.0.0.1:8787/health").openConnection() as java.net.HttpURLConnection;try{c.connectTimeout=1000;c.readTimeout=1000;c.inputStream.close()}finally{c.disconnect()}}catch(_:java.io.IOException){unreachable=true};assertTrue(unreachable)
  val db=db();val store=RoomPlanStore(db);val trip=db.trips().observeSavedTrips().first().single().id;val versions=store.history(trip)
  assertEquals(listOf("80","100"),versions.takeLast(2).map{it.brief.budget});assertEquals(listOf("vi","en"),versions.takeLast(2).map{it.plan.language});assertTrue(versions.size in 2..3);if(versions.size==3)assertEquals("mock",versions.first().plan.sourceMode)
  ActivityScenario.launch(MainActivity::class.java).use{s->
   for(v in versions){s.onActivity{host(it).navController.navigate(UiR.id.planner_graph);host(it).navController.navigate(UiR.id.itinerary_fragment,android.os.Bundle().apply{putString("tripId",trip);putInt("selectedVersion",v.version)})}
    waitFor("Offline version ${v.version}"){var shown=false;s.onActivity{a->val found=ArrayList<View>();a.window.decorView.findViewsWithText(found,a.getString(UiR.string.version_label,v.version),View.FIND_VIEWS_WITH_TEXT);shown=found.any{it.isShown}};shown}
    if(v.plan.sourceMode=="mock")waitFor("Offline mock label"){var shown=false;s.onActivity{a->val list=host(a).childFragmentManager.primaryNavigationFragment?.view as? RecyclerView;list?.scrollToPosition(1);val found=ArrayList<View>();a.window.decorView.findViewsWithText(found,a.getString(UiR.string.mock_label),View.FIND_VIEWS_WITH_TEXT);shown=found.any{it.isShown}};shown}
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();delay(250);shot("gemini-offline-v${v.version}")
   }
  };assertEquals(versions,store.history(trip));db.close()
 }
 @Test fun generateSaveEditSaveChanges()=runBlocking {
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("offlinePhase")!="true")
  val db=db();val store=RoomPlanStore(db)
  assertTrue("Use a fresh isolated review package; never delete user data",db.trips().observeSavedTrips().first().isEmpty())
  val date=LocalDate.now().plusDays(7).toString()
  db.workspace().write(WorkspaceEntity.from(PlannerDraft(destinationId="danang-vn",startDate=date,endDate=date,adults="2",budget="100",transport="excluded",purpose="leisure",pace="relaxed",interests="culture,food",arrival="10:00",departure="18:00",notes="Prefer short walks",step=3)))
  ActivityScenario.launch(MainActivity::class.java).use { s ->
   language(s,AppLanguage.ENGLISH)
   waitFor("Draft loaded"){var ready=false;s.onActivity{ready=ViewModelProvider(it)[WorkspaceViewModel::class.java].state.value.loaded};ready}
   val recover=java.io.File(context.getExternalFilesDir(null),"recover-request.json")
   if(InstrumentationRegistry.getArguments().getString("recoverCapturedRequest")=="true"){
    val capture=org.json.JSONObject(recover.readText());val body=capture.getString("body");val request=org.json.JSONObject(body)
    db.generation().put(db.generation().current()!!.copy(requestId=request.getString("requestId"),serverId=capture.getString("id"),body=body,state="QUEUED",error=null))
    route(s,UiR.id.progress_fragment);completeLiveOrExplicitMock(s,db,store,0)
   }else{val previous=db.generation().current()?.requestId;route(s,UiR.id.review_fragment);click(s,UiR.string.generate_plan);completeLiveOrExplicitMock(s,db,store,0,previous)}
   val candidate=store.candidate()!!;assertTrue(candidate.plan.sourceMode in setOf("gemini","mock"));assertEquals("en",candidate.plan.language);assertFalse(ItineraryAssessment.assess(candidate.brief,candidate.plan).hasConflicts)
   click(s,UiR.string.open_plan)
   click(s,UiR.string.open_budget)
   waitFor("Budget opened"){var ready=false;s.onActivity{ready=host(it).navController.currentDestination?.id==UiR.id.budget_fragment};ready}
   assertTrue(candidate.plan.costs.all{it.amount==null})
   waitFor("Unknown costs rendered"){var ready=false;s.onActivity{a->val list=host(a).childFragmentManager.primaryNavigationFragment?.view as? RecyclerView;if((list?.adapter?.itemCount ?: 0)>8)(list!!.layoutManager as androidx.recyclerview.widget.LinearLayoutManager).scrollToPositionWithOffset(6,0);val found=ArrayList<View>();a.window.decorView.findViewsWithText(found,a.getString(UiR.string.unknown_cost),View.FIND_VIEWS_WITH_TEXT);ready=found.any{it.isShown}};ready};shot("02-budget-unknown-en")
   s.onActivity {host(it).navController.popBackStack()};InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   click(s,UiR.string.save_plan)
   waitFor("First save"){runBlocking{store.committed(candidate.candidateId)!=null}}
   val first=store.committed(candidate.candidateId)!!;assertEquals(1,first.version);assertNull(store.candidate())
   assertEquals(SaveResult.Saved(first.tripId!!,1),store.save(candidate.candidateId))
   language(s,AppLanguage.VIETNAMESE)
   click(s,UiR.string.edit_brief);androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(UiR.string.discard_continue)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click())
   waitFor("Edit restored"){var ready=false;s.onActivity{ready=ViewModelProvider(it)[WorkspaceViewModel::class.java].state.value.draft!=null};ready}
   click(s,UiR.string.action_continue)
   s.onActivity {it.findViewById<android.widget.EditText>(PlannerR.id.budget).setText("80")}
   click(s,UiR.string.action_continue)
   s.onActivity {it.findViewById<android.widget.EditText>(PlannerR.id.notes).setText("Đi chậm, ưu tiên nghỉ chân")}
   val previousRequest=db.generation().current()?.requestId
   click(s,UiR.string.generate_plan)
   completeLiveOrExplicitMock(s,db,store,1,previousRequest)
   waitFor("Adjusted candidate"){runBlocking{store.candidate()?.brief?.budget=="80"}}
   val second=store.candidate()!!;assertEquals(first.tripId,second.tripId);assertEquals(1,second.version);assertEquals("vi",second.plan.language);assertEquals(1,store.history(first.tripId!!).size)
   click(s,UiR.string.open_plan);click(s,UiR.string.save_changes)
   waitFor("Save changes"){runBlocking{store.history(first.tripId!!).size==2}}
   assertEquals("100",store.revision(first.tripId!!,1)!!.brief.budget);assertEquals("80",store.revision(first.tripId!!)!!.brief.budget)
   assertEquals("en",store.revision(first.tripId!!,1)!!.plan.language);assertEquals("vi",store.revision(first.tripId!!)!!.plan.language)
   assertTrue(store.revision(first.tripId!!)!!.plan.sourceMode in setOf("gemini","mock"))
   s.recreate();InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   click(s,UiR.string.history_action)
   waitFor("History rows"){var ready=false;s.onActivity{ready=(host(it).childFragmentManager.primaryNavigationFragment?.view as? RecyclerView)?.adapter?.itemCount==2};ready};shot("03-history-vi")
  }
  db.close()
 }
}
