package com.example.travelassistant

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import com.example.travelassistant.core.data.LocalDatabase
import com.example.travelassistant.core.data.WorkspaceEntity
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.ui.WorkspaceViewModel
import com.example.travelassistant.core.ui.R as UiR
import com.example.travelassistant.feature.planner.R as PlannerR
import com.example.travelassistant.feature.settings.R as SettingsR
import com.example.travelassistant.feature.tools.R as ToolsR
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class Step04UiTest {
    // Android 16 on the physical test device can omit IMM's result callback.
    // Hide through insets and wait a frame instead of timing out on that callback.
    private fun dismissKeyboard()=object : androidx.test.espresso.ViewAction {
        override fun getConstraints()=isDisplayed()
        override fun getDescription()="Hide IME through WindowInsets"
        override fun perform(ui: androidx.test.espresso.UiController,view: View) {
            androidx.core.view.ViewCompat.getWindowInsetsController(view)?.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
            view.clearFocus();ui.loopMainThreadForAtLeast(200)
        }
    }
    private fun await(condition: ()->Boolean) {
        val until=System.currentTimeMillis()+20_000
        while(System.currentTimeMillis()<until) { if(condition()) return;Thread.sleep(75) }
        fail("Step 04 UI did not reach the expected state")
    }
    private fun workspace(s: ActivityScenario<MainActivity>): WorkspaceViewModel {
        var vm: WorkspaceViewModel?=null
        await { s.onActivity { vm=ViewModelProvider(it)[WorkspaceViewModel::class.java] };vm!!.state.value.loaded }
        return vm!!
    }
    private fun preview(s: ActivityScenario<MainActivity>,route: Int,key: String,expiry: Long=System.currentTimeMillis()+86_400_000) {
        s.onActivity { (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(route,Bundle().apply { putString("previewCase",key);putString("entryDestination",key.substringBefore(":"));putLong("previewExpiry",expiry) }) }
        await {
            var ready=false
            s.onActivity {
                val nav=it.supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment
                val current=nav?.childFragmentManager?.primaryNavigationFragment
                val found=ArrayList<View>()
                it.window.decorView.findViewsWithText(found,it.getString(UiR.string.preview_banner),View.FIND_VIEWS_WITH_TEXT)
                ready=current?.arguments?.getString("previewCase")==key && current?.isResumed==true && found.any { v -> v.isShown }
            }
            ready
        }
    }
    @Test fun allSixtyPreviewStatesInflateWithoutChangingTheRealWorkspaceOrSavedTrips() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val vm=workspace(scenario)
            val before=vm.state.value.draft
            val routes=mapOf("H01" to R.id.preview_h01,"P01" to R.id.preview_p01,"P02" to R.id.preview_p02,"P03" to R.id.preview_p03,"P04" to R.id.preview_p04,
                "I01" to R.id.preview_i01,"I02" to R.id.preview_i02,"M01" to R.id.preview_m01,"M02" to R.id.preview_m02,"T01" to R.id.preview_t01,"T02" to R.id.preview_t02,"T03" to R.id.preview_t03,"T04" to R.id.preview_t04,"S01" to R.id.preview_s01)
            var cases=emptyList<PreviewScenario>()
            scenario.onActivity { cases=it.previewCatalog.scenarios }
            assertEquals(60,cases.size)
            for(case in cases) preview(scenario,routes.getValue(case.screen),case.key)
            assertEquals(before,vm.state.value.draft)
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("travel.db").path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM saved_trip_summaries",null).use { c -> c.moveToFirst();assertEquals(0,c.getInt(0)) }
            }
        }
    }
    @Test fun invalidDatesAreBlockedAndValidDraftSurvivesLanguageChangeAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val vm=workspace(scenario)
            runBlocking { vm.discard(true);vm.startNew() }
            scenario.onActivity { (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(UiR.id.planner_graph) }
            onView(withId(PlannerR.id.continue_plan)).perform(click())
            assertTrue(BriefIssue.DESTINATION in vm.issues.value)
            assertTrue(BriefIssue.DATES in vm.issues.value)
            scenario.onActivity {
                val start=LocalDate.now().plusDays(4)
                vm.edit { it.copy(destinationId="danang-vn",destinationQuery="Da Nang",startDate=start.toString(),endDate=start.plusDays(2).toString(),adults="2",budget="1100.50",currency="USD",transport="excluded",purpose="leisure",notes="Keep this note") }
            }
            onView(withId(PlannerR.id.continue_plan)).perform(click())
            onView(withId(PlannerR.id.adults)).perform(scrollTo(),replaceText("-1"),dismissKeyboard())
            onView(withId(PlannerR.id.continue_plan)).perform(click())
            assertTrue(BriefIssue.GROUP in vm.issues.value)
            assertEquals("-1",vm.state.value.draft!!.adults)
            onView(withId(PlannerR.id.adults)).perform(scrollTo(),replaceText("2"),dismissKeyboard())
            onView(withId(PlannerR.id.budget)).perform(scrollTo(),replaceText("1100,50"),dismissKeyboard())
            onView(withId(PlannerR.id.continue_plan)).perform(click())
            onView(withId(PlannerR.id.notes)).perform(scrollTo(),replaceText("Updated note"),dismissKeyboard())
            runBlocking { vm.flush() }
            onView(withId(R.id.open_settings)).perform(click())
            onView(withId(SettingsR.id.language_vietnamese)).perform(click())
            await { var vi=false;scenario.onActivity { vi=it.resources.configuration.locales[0].language=="vi" };vi }
            scenario.recreate()
            await { var ready=false;scenario.onActivity { ready=it.findViewById<View>(R.id.toolbar)?.isShown==true };ready }
            scenario.onActivity { assertTrue(it.onSupportNavigateUp()) }
            onView(withId(PlannerR.id.notes)).perform(scrollTo()).check(matches(withText("Updated note")))
            assertEquals("1100,50",vm.state.value.draft!!.budget)
            assertEquals("USD",vm.state.value.draft!!.currency)
            // Generate now makes a real API operation. Keep ordinary UI regression free of paid calls;
            // the opt-in LocalApiJourneyTest covers Generate against a guarded simulation server.
            onView(withId(PlannerR.id.availability)).check(matches(withText(UiR.string.generate_local_intro)))
            onView(withId(PlannerR.id.continue_plan)).check(matches(isEnabled()))
            assertEquals("Updated note",vm.state.value.draft!!.notes)
        }
    }
    @Test fun expiredPreviewHasNoSaveOrShareAndUnknownBudgetShowsNoCompleteTotal() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            workspace(scenario)
            preview(scenario,R.id.preview_i01,"I01:expired")
            onView(withText(UiR.string.save_plan)).check(doesNotExist())
            onView(withText(UiR.string.share_plan)).check(doesNotExist())
            preview(scenario,R.id.preview_i02,"I02:unknown")
            scenario.onActivity { assertEquals(UiR.id.budget_fragment,(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.currentDestination?.id) }
        }
    }
    @Test fun worldTimeWorksWithoutNetworkAndCurrencyInputDoesNotEditTheDraft() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val vm=workspace(scenario);val before=vm.state.value.draft
            scenario.onActivity { (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.apply { navigate(UiR.id.tools_graph);navigate(UiR.id.time_fragment) } }
            onView(withId(ToolsR.id.destination_clock)).check(matches(isDisplayed()))
            scenario.onActivity { (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(UiR.id.currency_fragment) }
            onView(withId(ToolsR.id.amount)).perform(replaceText("25.50"),dismissKeyboard())
            var currencyModel: com.example.travelassistant.feature.tools.ToolViewModel?=null
            scenario.onActivity {
                val host=it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
                currencyModel=ViewModelProvider(host.childFragmentManager.primaryNavigationFragment!!)[com.example.travelassistant.feature.tools.ToolViewModel::class.java]
                currencyModel!!.input("from","USD");currencyModel!!.input("to","USD")
            }
            onView(withId(ToolsR.id.convert)).perform(scrollTo(),click())
            assertEquals("identity",currencyModel!!.inputs.value.status)
            scenario.recreate()
            onView(withId(ToolsR.id.currency_status)).perform(scrollTo()).check(matches(withSubstring("25.50")))
            assertEquals("identity",currencyModel!!.inputs.value.status)
            var weatherModel: com.example.travelassistant.feature.tools.ToolViewModel?=null
            scenario.onActivity {
                val host=it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
                host.navController.navigate(UiR.id.weather_fragment)
            }
            onView(withId(ToolsR.id.weather_city)).check(matches(isDisplayed()))
            scenario.onActivity {
                val host=it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
                weatherModel=ViewModelProvider(host.childFragmentManager.primaryNavigationFragment!!)[com.example.travelassistant.feature.tools.ToolViewModel::class.java]
                weatherModel!!.selectCity("paris-fr","Paris")
            }
            onView(withId(ToolsR.id.weather_selected_place)).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withId(ToolsR.id.weather_selected_place)).check(matches(isDisplayed()))
            assertEquals("paris-fr",weatherModel!!.inputs.value.city)
            assertEquals(before,vm.state.value.draft)
        }
    }
    @Test fun resolvedPlacesAndInputsSurviveViewStateRestorationAndNightMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val vm=workspace(scenario)
            runBlocking { vm.discard(true);vm.startNew() }
            scenario.onActivity {
                val start=LocalDate.now().plusDays(6)
                vm.edit { it.copy(destinationId="paris-fr",destinationQuery="Paris",originId="hanoi-vn",originQuery="Hanoi",startDate=start.toString(),endDate=start.plusDays(2).toString(),adults="2",children="1",childBands="3_11",budget="1450,50",currency="EUR",transport="included",purpose="family",notes="Keep these inputs") }
                (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(UiR.id.planner_graph)
            }
            onView(withId(PlannerR.id.selected_place)).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withId(PlannerR.id.destination)).check(matches(isDisplayed()))
            assertEquals("paris-fr",vm.state.value.draft!!.destinationId)
            onView(withId(PlannerR.id.continue_plan)).perform(click())
            onView(withId(PlannerR.id.origin)).perform(scrollTo()).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withId(PlannerR.id.adults)).check(matches(withText("2")))
            assertEquals("hanoi-vn",vm.state.value.draft!!.originId)
            try {
                scenario.onActivity { it.delegate.localNightMode=androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES }
                await { var dark=false;scenario.onActivity { dark=it.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES };dark }
                onView(withId(PlannerR.id.adults)).check(matches(withText("2")))
                assertEquals("paris-fr",vm.state.value.draft!!.destinationId)
                assertEquals("hanoi-vn",vm.state.value.draft!!.originId)
                assertEquals("1450,50",vm.state.value.draft!!.budget)
                assertEquals("3_11",vm.state.value.draft!!.childBands)
                assertEquals("EUR",vm.state.value.draft!!.currency)
                assertEquals("Keep these inputs",vm.state.value.draft!!.notes)
            } finally { scenario.onActivity { it.delegate.localNightMode=androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO } }
        }
    }
    @Test fun sampleSaveAndSaveChangesNeverCommitATripOrMutateTheRealDraft() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val vm=workspace(scenario);val before=vm.state.value.draft
            for((key,label) in listOf("I01:candidate" to UiR.string.save_plan,"I01:changes" to UiR.string.save_changes)) {
                preview(scenario,R.id.preview_i01,key)
                scenario.onActivity {
                    val host=it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
                    val list=host.childFragmentManager.primaryNavigationFragment!!.requireView() as androidx.recyclerview.widget.RecyclerView
                    list.scrollToPosition(list.adapter!!.itemCount-1)
                }
                onView(withText(label)).perform(click())
                onView(withText(UiR.string.preview_action)).check(matches(isDisplayed()))
                onView(withText(UiR.string.close)).perform(click())
                assertEquals(before,vm.state.value.draft)
            }
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("travel.db").path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM saved_trip_summaries",null).use { c -> c.moveToFirst();assertEquals(0,c.getInt(0)) }
            }
            preview(scenario,R.id.preview_i01,"I01:historical")
            onView(withText(UiR.string.save_plan)).check(doesNotExist())
            onView(withText(UiR.string.save_changes)).check(doesNotExist())
        }
    }
    @Test fun returningToEarlierStepsPersistsTheCorrectResumeStep() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val vm=workspace(scenario)
            runBlocking { vm.discard(true);vm.startNew() }
            scenario.onActivity {
                val start=LocalDate.now().plusDays(5)
                vm.edit { it.copy(destinationId="danang-vn",startDate=start.toString(),endDate=start.plusDays(2).toString(),adults="2",budget="900",transport="excluded",purpose="family",children="1",childBands="3_11",arrival="09:30",interests="food",notes="Keep rest breaks") }
                (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(UiR.id.planner_graph)
            }
            onView(withId(PlannerR.id.continue_plan)).perform(click())
            onView(withId(PlannerR.id.continue_plan)).perform(click())
            await { vm.state.value.draft?.step==3 }
            onView(withId(PlannerR.id.review_summary)).perform(scrollTo()).check(matches(withSubstring("Keep rest breaks")))
            onView(withId(PlannerR.id.review_summary)).check(matches(withSubstring("09:30")))
            scenario.onActivity { assertTrue(it.onSupportNavigateUp()) }
            await { vm.state.value.draft?.step==2 }
            scenario.onActivity { assertTrue(it.onSupportNavigateUp()) }
            await { vm.state.value.draft?.step==1 }
            runBlocking { assertTrue(vm.flush()) }
        }
        // A new Activity/ViewModel must read the earlier step from Room, not memory.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val vm=workspace(scenario)
            assertEquals(1,vm.state.value.draft!!.step)
            assertEquals("Keep rest breaks",vm.state.value.draft!!.notes)
        }
    }
    @Test fun staleAndReversedDraftDatesCanBeCorrectedInTheNativePicker() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val vm=workspace(scenario)
            runBlocking { vm.discard(true);vm.startNew() }
            scenario.onActivity { (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(UiR.id.planner_graph) }
            val today=LocalDate.now()
            for((start,end) in listOf(today.plusDays(8) to today.plusDays(2),today.minusDays(2) to today.minusDays(1))) {
                scenario.onActivity { vm.edit { it.copy(destinationId="danang-vn",startDate=start.toString(),endDate=end.toString()) } }
                onView(withId(PlannerR.id.start_date)).perform(scrollTo(),click())
                onView(withId(com.google.android.material.R.id.confirm_button)).inRoot(isDialog()).check(matches(not(isEnabled())))
                onView(withId(com.google.android.material.R.id.cancel_button)).inRoot(isDialog()).perform(click())
                assertEquals(start.toString(),vm.state.value.draft!!.startDate)
                onView(withId(PlannerR.id.continue_plan)).perform(click())
                assertTrue(BriefIssue.DATES in vm.issues.value)
            }
        }
    }
    @Test fun budgetExpiresWhileVisibleAndDoesNotRestartAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            workspace(scenario)
            val expiry=System.currentTimeMillis()+8_000
            preview(scenario,R.id.preview_i02,"I02:unknown",expiry)
            onView(withText(UiR.string.budget_requested)).check(matches(isDisplayed()))
            await {
                var expired=false
                scenario.onActivity { activity ->
                    val found=ArrayList<View>()
                    activity.window.decorView.findViewsWithText(found,activity.getString(UiR.string.expired_title),View.FIND_VIEWS_WITH_TEXT)
                    expired=found.any { it.isShown }
                }
                expired
            }
            onView(withText(UiR.string.budget_requested)).check(doesNotExist())
            scenario.recreate()
            onView(withText(UiR.string.expired_title)).check(matches(isDisplayed()))
            scenario.onActivity {
                val nav=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
                assertEquals(expiry,nav.currentBackStackEntry!!.arguments!!.getLong("previewExpiry"))
            }
        }
    }
    @Test fun roomMigrationPreservesSavedRowsAndWorkspaceIsStoredSeparately() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="step04-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name,0,null).use { db ->
            db.execSQL("CREATE TABLE saved_trip_summaries (id TEXT NOT NULL, title TEXT NOT NULL, PRIMARY KEY(id))")
            db.execSQL("INSERT INTO saved_trip_summaries VALUES ('existing', 'Keep existing trip')")
            db.version=1
        }
        val db=Room.databaseBuilder(context,LocalDatabase::class.java,name).addMigrations(LocalDatabase.MIGRATION_1_2,LocalDatabase.MIGRATION_2_3,LocalDatabase.MIGRATION_3_4).build()
        try {
            runBlocking {
                assertEquals("Keep existing trip",db.trips().observeSavedTrips().first().single().title)
                db.workspace().write(WorkspaceEntity.from(PlannerDraft(notes="Durable draft",step=2)))
                assertEquals("Durable draft",db.workspace().observe().first()!!.notes)
                assertEquals(1,db.trips().observeSavedTrips().first().size)
                db.workspace().discard()
                assertNull(db.workspace().observe().first())
                assertEquals(1,db.trips().observeSavedTrips().first().size)
            }
        } finally { db.close();context.deleteDatabase(name) }
    }
}
