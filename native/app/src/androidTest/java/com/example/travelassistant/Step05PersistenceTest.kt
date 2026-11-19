package com.example.travelassistant
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelassistant.core.data.*
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class Step05PersistenceTest {
 private lateinit var db: LocalDatabase
 private lateinit var store: RoomPlanStore
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private val date=LocalDate.now().plusDays(5).toString()
 private val brief get()=PlannerDraft(destinationId="danang-vn",startDate=date,endDate=date,budget="100",transport="excluded",purpose="leisure")
 private fun plan(note: String="first")=PlanPresentation("fixture","Persistence test only","en","USD",BigDecimal("100"),1,0,listOf(PlanDay(date,"Day",listOf(PlanActivity("09:00","Test activity","Test area",60,note)))),listOf("stay","food","local","activities").map{PlanCost(it,null)},"Test only; no live data",false)
 @Before fun open(){ db=Room.inMemoryDatabaseBuilder(context,LocalDatabase::class.java).build();store=RoomPlanStore(db) }
 @After fun close(){ db.close() }
 private suspend fun receive(id: String="one",b: PlannerDraft=brief,p: PlanPresentation=plan()) { db.workspace().write(WorkspaceEntity.from(b));val base=db.plans().base();store.receive(id,b,p,System.currentTimeMillis(),base?.tripId,base?.version ?: 0) }
 @Test fun firstSaveIsIdempotentAndDraftIsNotASavedTrip()=runBlocking {
  receive();assertTrue(db.trips().observeSavedTrips().first().isEmpty())
  val a=store.save("one") as SaveResult.Saved;assertEquals(1,a.version)
  assertEquals(a,store.save("one"));assertEquals(1,store.history(a.tripId).size)
  assertNull(db.workspace().observe().first());assertNull(store.candidate())
 }
 @Test fun saveChangesAppendsImmutableHistoryAndKeepsCurrentUntilSave()=runBlocking {
  receive();val first=store.save("one") as SaveResult.Saved
  assertTrue(store.edit(first.tripId));val newer=brief.copy(notes="new")
  receive("two",newer,plan("second"));assertEquals("first",store.revision(first.tripId)!!.plan.days[0].activities[0].note)
  val second=store.save("two") as SaveResult.Saved;assertEquals(2,second.version)
  assertEquals("first",store.revision(first.tripId,1)!!.plan.days[0].activities[0].note)
  assertEquals("second",store.revision(first.tripId)!!.plan.days[0].activities[0].note)
 }
 @Test fun mismatchRequiresExplicitConfirmation()=runBlocking {
  receive();db.workspace().write(WorkspaceEntity.from(brief.copy(notes="newer unsaved edits")))
  assertEquals(SaveResult.Mismatch,store.save("one"));assertNotNull(store.candidate())
  assertTrue(db.trips().observeSavedTrips().first().isEmpty())
  assertTrue(store.save("one",true) is SaveResult.Saved)
 }
 @Test fun failedTransactionRollsBackCandidateRevisionAndDraft()=runBlocking {
  receive()
  db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_save BEFORE INSERT ON saved_trip_summaries BEGIN SELECT RAISE(ABORT, 'injected storage failure'); END")
  try {store.save("one");fail("Expected write failure")}catch(_: android.database.sqlite.SQLiteException){}
  assertNotNull(store.candidate());assertNotNull(db.workspace().observe().first());assertNull(db.plans().source("one"))
  db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_save")
  assertTrue(store.save("one") is SaveResult.Saved)
 }
 @Test fun expiredCandidateCannotSaveButDraftSurvives()=runBlocking {
  receive();val c=db.plans().candidate()!!;db.plans().candidate(c.copy(expiresAt=System.currentTimeMillis()-1))
  assertEquals(SaveResult.Expired,store.save("one"));assertNull(store.candidate());assertNotNull(db.workspace().observe().first())
 }
 @Test fun concurrentSaveCommitsOnce()=runBlocking {
  receive();val results=coroutineScope {listOf(async{store.save("one")},async{store.save("one")}).awaitAll()}
  assertEquals(results[0],results[1]);assertEquals(1,db.trips().observeSavedTrips().first().size)
 }
 @Test fun removeAllAlsoRemovesPayloads()=runBlocking {
  receive();store.save("one");LocalWorkspaceRepository(db).removeAllLocalTrips()
  assertTrue(db.trips().observeSavedTrips().first().isEmpty());assertNull(db.plans().source("one"))
 }
 @Test fun conflictRetainsCandidateAndLatestRevision()=runBlocking {
  receive();val first=store.save("one") as SaveResult.Saved
  store.edit(first.tripId);receive("two",brief,plan("second"))
  val old=db.plans().history(first.tripId).first()
  db.plans().revision(old.copy(version=2,candidateId="other-writer"))
  assertEquals(SaveResult.Conflict,store.save("two"));assertEquals("two",store.candidate()!!.candidateId)
  assertEquals(2,store.history(first.tripId).size)
 }
 @Test fun recoverySurvivesDatabaseReopenWithoutExtendingExpiry()=runBlocking {
  db.close();val name="step05-reopen.db";context.deleteDatabase(name)
  db=Room.databaseBuilder(context,LocalDatabase::class.java,name).build();store=RoomPlanStore(db)
  receive();val expiry=store.candidate()!!.expiresAt
  db.close();db=Room.databaseBuilder(context,LocalDatabase::class.java,name).build();store=RoomPlanStore(db)
  assertEquals(expiry,store.candidate()!!.expiresAt)
  val saved=store.save("one") as SaveResult.Saved
  db.close();db=Room.databaseBuilder(context,LocalDatabase::class.java,name).build();store=RoomPlanStore(db)
  assertEquals("first",store.revision(saved.tripId)!!.plan.days[0].activities[0].note)
  assertEquals(saved,store.save("one"));db.close();context.deleteDatabase(name);Unit
 }

 @Test fun actualUiSavesCandidateAndOpensReadOnlyHistory()=runBlocking {
  db.close();db=Room.databaseBuilder(context,LocalDatabase::class.java,"travel.db").addMigrations(LocalDatabase.MIGRATION_1_2,LocalDatabase.MIGRATION_2_3,LocalDatabase.MIGRATION_3_4).build();store=RoomPlanStore(db)
  LocalWorkspaceRepository(db).removeAllLocalTrips();receive("ui-fixture")
  androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   scenario.onActivity { activity ->
    val nav=(activity.supportFragmentManager.findFragmentById(R.id.nav_host) as androidx.navigation.fragment.NavHostFragment).navController
    nav.navigate(com.example.travelassistant.core.ui.R.id.planner_graph);nav.navigate(com.example.travelassistant.core.ui.R.id.itinerary_fragment)
   }
   fun waitFor(test: ()->Boolean) {val end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(test())return;Thread.sleep(75)};fail("Persistence UI timeout")}
   waitFor { var ready=false;scenario.onActivity { activity -> val host=activity.supportFragmentManager.findFragmentById(R.id.nav_host) as androidx.navigation.fragment.NavHostFragment;val root=host.childFragmentManager.primaryNavigationFragment?.view as? androidx.recyclerview.widget.RecyclerView;ready=(root?.adapter?.itemCount ?: 0)>5;if(ready)root!!.scrollToPosition(root.adapter!!.itemCount-2) };ready }
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(com.example.travelassistant.core.ui.R.string.save_plan)).perform(androidx.test.espresso.action.ViewActions.click())
   waitFor { runBlocking {db.plans().source("ui-fixture")!=null} }
   val saved=store.save("ui-fixture") as SaveResult.Saved
   assertEquals(1,saved.version)
   scenario.recreate()
   waitFor { var ready=false;scenario.onActivity { activity -> val host=activity.supportFragmentManager.findFragmentById(R.id.nav_host) as androidx.navigation.fragment.NavHostFragment;val root=host.childFragmentManager.primaryNavigationFragment?.view as? androidx.recyclerview.widget.RecyclerView;ready=(root?.adapter?.itemCount ?: 0)>5;if(ready)root!!.scrollToPosition(root.adapter!!.itemCount-2) };ready }
   var initialLanguage: LanguageRepository?=null
   scenario.onActivity {initialLanguage=it.languages}
   runBlocking {initialLanguage!!.requestLanguage(AppLanguage.ENGLISH)}
   waitFor {var en=false;scenario.onActivity {en=it.resources.configuration.locales[0].language=="en"};en}
   waitFor {var ready=false;scenario.onActivity {a->val host=a.supportFragmentManager.findFragmentById(R.id.nav_host) as androidx.navigation.fragment.NavHostFragment;val root=host.childFragmentManager.primaryNavigationFragment?.view as? androidx.recyclerview.widget.RecyclerView;ready=(root?.adapter?.itemCount ?: 0)>5;if(ready)root!!.scrollToPosition(root.adapter!!.itemCount-2)};ready}
   androidx.test.espresso.Espresso.onView(org.hamcrest.Matchers.allOf(androidx.test.espresso.matcher.ViewMatchers.withText(com.example.travelassistant.core.ui.R.string.history_action),androidx.test.espresso.matcher.ViewMatchers.withId(com.example.travelassistant.core.ui.R.id.row_primary))).perform(androidx.test.espresso.action.ViewActions.click())
   waitFor { var ready=false;scenario.onActivity { activity -> val matches=ArrayList<android.view.View>();activity.window.decorView.findViewsWithText(matches,activity.getString(com.example.travelassistant.core.ui.R.string.version_label,1),android.view.View.FIND_VIEWS_WITH_TEXT);ready=matches.any{it.isShown} };ready }
   assertEquals(1,store.history(saved.tripId).size)
   var language: LanguageRepository?=null
   scenario.onActivity {language=it.languages}
   runBlocking {language!!.requestLanguage(AppLanguage.VIETNAMESE)}
   waitFor {var vi=false;scenario.onActivity {vi=it.resources.configuration.locales[0].language=="vi"};vi}
   waitFor {var ready=false;scenario.onActivity {a->val found=ArrayList<android.view.View>();a.window.decorView.findViewsWithText(found,a.getString(com.example.travelassistant.core.ui.R.string.version_label,1),android.view.View.FIND_VIEWS_WITH_TEXT);ready=found.any{it.isShown}};ready}
   assertEquals("en",store.revision(saved.tripId)!!.plan.language)
   scenario.onActivity {a->(a.supportFragmentManager.findFragmentById(R.id.nav_host) as androidx.navigation.fragment.NavHostFragment).navController.navigate(com.example.travelassistant.core.ui.R.id.home_graph)}
   waitFor {var ready=false;scenario.onActivity {a->val found=ArrayList<android.view.View>();a.window.decorView.findViewsWithText(found,"Persistence test only",android.view.View.FIND_VIEWS_WITH_TEXT);ready=found.any{it.isShown}};ready}
  }
 }

 @Test fun invalidReplacementPreservesPreviousCandidate()=runBlocking {
  receive();val original=store.candidate()!!
  try {store.receive("bad",brief,plan().copy(days=emptyList()),System.currentTimeMillis());fail("Must reject invalid output")}catch(_: IllegalArgumentException){}
  assertEquals(original,store.candidate())
 }
 @Test fun separateTripsStartAtOneAndCommittedSourceCanRecover()=runBlocking {
  receive();val a=store.save("one") as SaveResult.Saved
  receive("other");val b=store.save("other") as SaveResult.Saved
  assertNotEquals(a.tripId,b.tripId);assertEquals(1,b.version)
  assertEquals(a.tripId,store.committed("one")!!.tripId)
 }

 @Test fun boundedClockSkewKeepsTheOriginalCandidateDeadline()=runBlocking {
  db.workspace().write(WorkspaceEntity.from(brief))
  val serverReady=System.currentTimeMillis()+30_000
  store.receive("clock",brief,plan(),serverReady)
  assertEquals(serverReady+86_400_000,store.candidate()!!.expiresAt)
  try {store.receive("future",brief,plan(),System.currentTimeMillis()+120_000);fail("Unbounded future timestamp must be rejected")}catch(_: IllegalArgumentException){}
  assertEquals("clock",store.candidate()!!.candidateId)
 }

}
