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

/** Run only with the explicitly enabled local mock/rate-limit simulation server. */
@RunWith(AndroidJUnit4::class)
class LocalApiJourneyTest {
 @org.junit.Before fun safeTestServer(){LocalApiTestGate.requireSimulation()}
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private fun db()=Room.databaseBuilder(context,LocalDatabase::class.java,"travel.db").addMigrations(LocalDatabase.MIGRATION_1_2,LocalDatabase.MIGRATION_2_3,LocalDatabase.MIGRATION_3_4).build()
 private fun waitFor(label: String,condition: ()->Boolean){val end=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<end){if(condition())return;Thread.sleep(100)};fail(label)}
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
 @Test fun generateRateLimitMockReviewSaveEditSaveChanges()=runBlocking {
  val db=db();val store=RoomPlanStore(db)
  LocalWorkspaceRepository(db).removeAllLocalTrips()
  val date=LocalDate.now().plusDays(7).toString()
  db.workspace().write(WorkspaceEntity.from(PlannerDraft(destinationId="danang-vn",startDate=date,endDate=date,adults="2",budget="100",transport="excluded",purpose="leisure",pace="relaxed",interests="culture,food",arrival="10:00",departure="18:00",notes="Prefer short walks",step=3)))
  ActivityScenario.launch(MainActivity::class.java).use { s ->
   language(s,AppLanguage.ENGLISH)
   waitFor("Draft loaded"){var ready=false;s.onActivity{ready=ViewModelProvider(it)[WorkspaceViewModel::class.java].state.value.loaded};ready}
   route(s,UiR.id.review_fragment)
   click(s,UiR.string.generate_plan)
   waitFor("AI rate limit"){runBlocking{db.generation().current()?.error in setOf("AI_RATE_LIMITED","DAILY_QUOTA","AI_QUOTA_EXHAUSTED")}}
   val failed=db.generation().current()!!;assertEquals("FAILED",failed.state)
   waitFor("Rate limit rendered"){var ready=false;s.onActivity{a->val found=ArrayList<View>();a.window.decorView.findViewsWithText(found,a.getString(when(failed.error){"DAILY_QUOTA"->UiR.string.generate_daily_quota;"AI_QUOTA_EXHAUSTED"->UiR.string.generate_ai_quota;else->UiR.string.generate_rate_limit}),View.FIND_VIEWS_WITH_TEXT);ready=found.any{it.isShown}};ready};shot("01-rate-limit-en")
   click(s,UiR.string.use_mock)
   waitFor("Mock received"){runBlocking{db.generation().current()?.state=="RECEIVED"}}
   val firstOp=db.generation().current()!!;assertNotEquals(failed.requestId,firstOp.requestId)
   val candidate=store.candidate()!!;assertEquals("mock",candidate.plan.sourceMode);assertEquals("en",candidate.plan.language);assertFalse(ItineraryAssessment.assess(candidate.brief,candidate.plan).hasConflicts)
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
   click(s,UiR.string.generate_plan)
   waitFor("Second rate limit or quota"){runBlocking{db.generation().current()?.state=="FAILED"}}
   assertEquals(1,store.revision(first.tripId!!)!!.version)
   click(s,UiR.string.open_plan)
   waitFor("Previous saved plan opened after failed edit"){var ready=false;s.onActivity{a->val fragment=host(a).childFragmentManager.primaryNavigationFragment;ready=fragment?.arguments?.getString("tripId")==first.tripId};ready}
   s.onActivity {host(it).navController.popBackStack()};InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   click(s,UiR.string.use_mock)
   waitFor("Adjusted candidate"){runBlocking{store.candidate()?.brief?.budget=="80"}}
   val second=store.candidate()!!;assertEquals(first.tripId,second.tripId);assertEquals(1,second.version);assertEquals("vi",second.plan.language);assertEquals(1,store.history(first.tripId!!).size)
   click(s,UiR.string.open_plan);click(s,UiR.string.save_changes)
   waitFor("Save changes"){runBlocking{store.history(first.tripId!!).size==2}}
   assertEquals("100",store.revision(first.tripId!!,1)!!.brief.budget);assertEquals("80",store.revision(first.tripId!!)!!.brief.budget)
   assertEquals("en",store.revision(first.tripId!!,1)!!.plan.language);assertEquals("vi",store.revision(first.tripId!!)!!.plan.language)
   assertEquals("mock",store.revision(first.tripId!!)!!.plan.sourceMode)
   s.recreate();InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   click(s,UiR.string.history_action)
   waitFor("History rows"){var ready=false;s.onActivity{ready=(host(it).childFragmentManager.primaryNavigationFragment?.view as? RecyclerView)?.adapter?.itemCount==2};ready};shot("03-history-vi")
  }
  db.close()
 }
}
