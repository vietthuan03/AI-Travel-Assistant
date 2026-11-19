package com.example.travelassistant

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelassistant.core.data.*
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.ui.R as UiR
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Deliberately separate opt-in: exactly one real Generate, never an automatic paid retry. */
@RunWith(AndroidJUnit4::class)
class LiveAiJourneyTest {
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private fun db()=Room.databaseBuilder(context,LocalDatabase::class.java,"travel.db").addMigrations(LocalDatabase.MIGRATION_1_2,LocalDatabase.MIGRATION_2_3,LocalDatabase.MIGRATION_3_4).build()
 private fun host(a: MainActivity)=a.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
 private fun waitFor(message: String,timeout: Long=15000,condition: ()->Boolean){val end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end){if(condition())return;Thread.sleep(100)};fail(message)}
 private fun click(s: ActivityScenario<MainActivity>,id: Int){var clicked=false;for(i in 0..40){s.onActivity{a->val list=host(a).childFragmentManager.primaryNavigationFragment?.view as? RecyclerView;if(i<(list?.adapter?.itemCount ?: 0))list!!.scrollToPosition(i)};InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity{a->val views=ArrayList<View>();a.window.decorView.findViewsWithText(views,a.getString(id),View.FIND_VIEWS_WITH_TEXT);views.filterIsInstance<Button>().firstOrNull{it.text.toString()==a.getString(id)&&it.isShown&&it.isEnabled}?.let{it.performClick();clicked=true}};if(clicked)return};fail("Missing live review action $id")}
 private fun shot(name: String){InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(200);val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!;try{java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null),"$name.png")).use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}}
 @Test fun oneRealGenerateThenSaveOrExplicitLabeledMock()=runBlocking {
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveAiPhase")=="true")
  val connection=URL("http://127.0.0.1:8787/health").openConnection() as HttpURLConnection
  val health=try{JSONObject(connection.inputStream.bufferedReader().use{it.readText()})}finally{connection.disconnect()}
  assertEquals("This historical test is OpenAI-only", "openai",health.optString("provider","openai"));assertFalse("Live test refuses simulated rate limit",health.getBoolean("rateLimitSimulation"));assertTrue(health.getBoolean("aiConfigured"));assertTrue(health.getBoolean("mockAllowed"))
  val db=db();val store=RoomPlanStore(db);val trip=db.trips().observeSavedTrips().first().single().id
  val old=store.history(trip);assertEquals(2,old.size);assertTrue(store.edit(trip,true))
  val before=db.workspace().observe().first()!!.domain()
  LocalWorkspaceRepository(db).write(before.copy(budget="70",notes="Prefer publicly visitable cultural places, short walks and a rest break. Explain any uncertainty.",outputLanguage="en",step=3))
  val oldRequest=db.generation().current()?.requestId
  ActivityScenario.launch(MainActivity::class.java).use{s->
   var languages: LanguageRepository?=null;s.onActivity{languages=it.languages};languages!!.requestLanguage(AppLanguage.ENGLISH)
   waitFor("English UI"){var ready=false;s.onActivity{ready=it.resources.configuration.locales[0].language=="en"};ready}
   s.onActivity{host(it).navController.navigate(UiR.id.planner_graph);host(it).navController.navigate(UiR.id.review_fragment)}
   InstrumentationRegistry.getInstrumentation().waitForIdleSync();click(s,UiR.string.generate_plan)
   waitFor("Real operation did not terminate",130000){runBlocking{db.generation().current()?.let{it.requestId!=oldRequest&&it.state in setOf("FAILED","RECEIVED")}==true}}
   val live=db.generation().current()!!;assertEquals(2,store.history(trip).size);assertEquals(old,store.history(trip))
   // Re-send exactly the captured body after a terminal response: backend must return the same job without another provider call.
   val dependencies=dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,ReviewDataAccess::class.java)
   val guest=dependencies.preferences().data.first()[androidx.datastore.preferences.core.stringPreferencesKey("backend_guest_token")]!!
   val retry=URL("http://127.0.0.1:8787/v1/operations").openConnection() as HttpURLConnection
   val replay=try{retry.requestMethod="POST";retry.doOutput=true;retry.connectTimeout=5000;retry.readTimeout=5000;retry.setRequestProperty("Content-Type","application/json");retry.setRequestProperty("Authorization","Bearer $guest");retry.outputStream.use{it.write(live.body.toByteArray())};assertEquals(202,retry.responseCode);JSONObject(retry.inputStream.bufferedReader().use{it.readText()})}finally{retry.disconnect()}
   assertEquals(live.serverId,replay.getString("id"));assertEquals(if(live.state=="RECEIVED")"READY" else "FAILED",replay.getString("state"))
   val evidence=JSONObject().put("requestId",live.requestId).put("operationId",live.serverId).put("state",live.state).put("error",live.error ?: JSONObject.NULL).put("simulation",false).put("explicitGenerateCount",1).put("idempotentReplayVerified",true).put("failureDetails",replay.opt("failureDetails") ?: JSONObject.NULL)
   java.io.File(context.getExternalFilesDir(null),"live-ai-review.json").writeText(evidence.toString(2))
   if(live.state=="FAILED"){
    assertTrue("Expected actual provider rate/quota failure, got ${live.error}",live.error in setOf("AI_RATE_LIMITED","AI_QUOTA_EXHAUSTED"))
    val message=if(live.error=="AI_RATE_LIMITED")UiR.string.generate_rate_limit else UiR.string.generate_ai_quota
    waitFor("Real rate/quota error rendered"){var ready=false;s.onActivity{a->val found=ArrayList<View>();a.window.decorView.findViewsWithText(found,a.getString(message),View.FIND_VIEWS_WITH_TEXT);ready=found.any{it.isShown}};ready};shot("05-real-ai-rate-limit")
    delay(3000);assertEquals(live.requestId,db.generation().current()!!.requestId);assertEquals("FAILED",db.generation().current()!!.state);assertNull(store.candidate())
    click(s,UiR.string.use_mock)
    waitFor("Explicit mock fallback"){runBlocking{db.generation().current()?.state=="RECEIVED"}}
    assertNotEquals(live.requestId,db.generation().current()!!.requestId);assertEquals("mock",store.candidate()!!.plan.sourceMode)
   } else {assertEquals("openai",store.candidate()!!.plan.sourceMode);assertEquals("en",store.candidate()!!.plan.language);assertTrue(store.candidate()!!.plan.days.flatMap{it.activities}.all{it.sourceUrl?.startsWith("https://")==true})}
   val candidate=store.candidate()!!;assertFalse(ItineraryAssessment.assess(candidate.brief,candidate.plan).hasConflicts)
   click(s,UiR.string.open_plan);click(s,UiR.string.save_changes)
   waitFor("Third saved version"){runBlocking{store.history(trip).size==3}}
   assertEquals(old,store.history(trip).drop(1));assertEquals("70",store.revision(trip)!!.brief.budget)
   evidence.put("savedVersion",3).put("savedSourceMode",store.revision(trip)!!.plan.sourceMode).put("previousVersionsPreserved",true)
   java.io.File(context.getExternalFilesDir(null),"live-ai-review.json").writeText(evidence.toString(2));s.recreate();InstrumentationRegistry.getInstrumentation().waitForIdleSync();shot("06-live-review-saved-v3")
  };db.close()
 }
 @Test fun coldReopenLatestThreeVersionsWithApiUnreachable()=runBlocking {
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("reviewOfflinePhase")=="true")
  var unreachable=false;try{val c=URL("http://127.0.0.1:8787/health").openConnection() as HttpURLConnection;try{c.connectTimeout=1000;c.readTimeout=1000;c.inputStream.close()}finally{c.disconnect()}}catch(_: java.io.IOException){unreachable=true};assertTrue(unreachable)
  val db=db();val store=RoomPlanStore(db);val trip=db.trips().observeSavedTrips().first().single().id;val history=store.history(trip)
  assertEquals(3,history.size);assertEquals(listOf("70","80","100"),history.map{it.brief.budget});assertEquals(listOf("en","vi","en"),history.map{it.plan.language});assertTrue(history.first().plan.sourceMode in setOf("openai","mock"))
  ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{host(it).navController.navigate(UiR.id.planner_graph);host(it).navController.navigate(UiR.id.itinerary_fragment,Bundle().apply{putString("tripId",trip)})};waitFor("Offline saved v3 rendered"){var ready=false;s.onActivity{a->val views=ArrayList<View>();a.window.decorView.findViewsWithText(views,a.getString(UiR.string.version_label,3),View.FIND_VIEWS_WITH_TEXT);ready=views.any{it.isShown}};ready};shot("07-offline-v3")};assertEquals(history,store.history(trip));db.close()
 }
}
