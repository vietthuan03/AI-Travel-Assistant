package com.example.travelassistant

import android.view.View
import androidx.navigation.fragment.NavHostFragment
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelassistant.core.data.*
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.ui.R as UiR
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

/** UI fixtures only; does not submit, rotate a credential or invoke a provider. */
@RunWith(AndroidJUnit4::class)
class RateLimitUiReviewTest {
 @Test fun cancelDuringSessionCreationCannotLeaveAnActiveRequest()=runBlocking {
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localApiPhase")=="true")
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val db=Room.inMemoryDatabaseBuilder(context,LocalDatabase::class.java).build()
  val day=java.time.LocalDate.now().plusDays(7).toString()
  db.workspace().write(WorkspaceEntity.from(PlannerDraft(destinationId="danang-vn",startDate=day,endDate=day,budget="100",transport="excluded",purpose="leisure")))
  val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
  val preferences=object: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
   private val value=kotlinx.coroutines.flow.MutableStateFlow(androidx.datastore.preferences.core.emptyPreferences())
   override val data: kotlinx.coroutines.flow.Flow<androidx.datastore.preferences.core.Preferences> = value
   override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences)->androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences {
    val updated=transform(value.value);entered.complete(Unit);release.await();value.value=updated;return updated
   }
  }
  val repo=LocalGenerationRepository(db,preferences,RoomPlanStore(db))
  val start=async {try {repo.begin(true);null}catch(e: GenerationFailure){e.reason}}
  withTimeout(10000){entered.await()}
  val cancel=async {repo.cancel()};delay(100);release.complete(Unit)
  assertEquals("REQUEST_BUSY",start.await());cancel.await();assertNull(db.generation().current());assertNotNull(db.workspace().observe().first());db.close()
 }
 @Test fun rateLimitQuotaAndLocalLimitHaveDistinctLocalizedRecovery()=runBlocking {
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localApiPhase")=="true")
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val db=dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,ReviewDataAccess::class.java).database();val plans=RoomPlanStore(db)
  val trip=db.trips().observeSavedTrips().first().single().id;val before=plans.history(trip)
  val originalOperation=db.generation().current()
  try {
  ActivityScenario.launch(MainActivity::class.java).use {s->
   fun waitFor(test: ()->Boolean){val end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(test())return;Thread.sleep(100)};fail("Localized failure UI timeout")}
   var languages: LanguageRepository?=null;s.onActivity{languages=it.languages}
   for(language in listOf(AppLanguage.ENGLISH,AppLanguage.VIETNAMESE)){
    languages!!.requestLanguage(language);waitFor{var ready=false;s.onActivity{ready=it.resources.configuration.locales[0].language==language.tag};ready}
    s.onActivity{val nav=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController;nav.navigate(UiR.id.planner_graph);nav.navigate(UiR.id.progress_fragment)}
    for((code,message) in listOf("AI_PROVIDER_HTTP_503" to UiR.string.generate_unavailable,"AI_RATE_LIMITED" to UiR.string.generate_rate_limit,"AI_QUOTA_EXHAUSTED" to UiR.string.generate_ai_quota,"DAILY_QUOTA" to UiR.string.generate_daily_quota)){
     val id=UUID.randomUUID().toString();db.generation().put(GenerationEntity(requestId=id,serverId=null,body="{}",ownerHash="UI-fixture-not-a-credential",state="FAILED",error=code,baseTrip=trip,baseVersion=2))
     waitFor{var shown=false;s.onActivity{a->val found=ArrayList<View>();a.window.decorView.findViewsWithText(found,a.getString(message),View.FIND_VIEWS_WITH_TEXT);shown=found.any{it.isShown}};shown}
     InstrumentationRegistry.getInstrumentation().waitForIdleSync();delay(250);val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
     try{java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null),"error-$code-${language.tag}.png")).use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
     delay(200);assertEquals(id,db.generation().current()!!.requestId);assertEquals("FAILED",db.generation().current()!!.state);assertEquals(before,plans.history(trip));assertNull(plans.candidate())
    }
   }
  }
  } finally {originalOperation?.let {db.generation().put(it)}}
 }
}
