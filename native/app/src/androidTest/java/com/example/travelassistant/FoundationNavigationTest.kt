package com.example.travelassistant

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import com.example.travelassistant.core.domain.LanguageRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.navigation.NavigationBarView
import com.example.travelassistant.core.ui.R as UiR
import com.example.travelassistant.feature.settings.R as SettingsR
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.allOf
import androidx.lifecycle.ViewModelProvider
import com.example.travelassistant.core.ui.WorkspaceViewModel
import com.example.travelassistant.core.domain.PlannerDraft
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FoundationNavigationTest {
    private fun awaitShell(scenario: ActivityScenario<MainActivity>) {
        await {
            var ready=false
            scenario.onActivity { ready=it.supportFragmentManager.findFragmentById(R.id.nav_host) is NavHostFragment && it.findViewById<android.view.View>(R.id.toolbar)?.isShown==true }
            ready
        }
    }
    private fun await(condition: () -> Boolean) {
        val deadline=System.currentTimeMillis()+15_000
        while(System.currentTimeMillis()<deadline) {
            if(condition()) return
            Thread.sleep(75)
        }
        fail("Foundation did not reach the expected state")
    }
    private fun prepareWorkspace(scenario: ActivityScenario<MainActivity>, valid: Boolean=false) {
        var model: WorkspaceViewModel?=null
        await { var loaded=false;scenario.onActivity { model=ViewModelProvider(it)[WorkspaceViewModel::class.java];loaded=model!!.state.value.loaded };loaded }
        runBlocking { model!!.discard(removeAll=true) }
        if(valid) {
            runBlocking { model!!.startNew() }
            scenario.onActivity {
                val start=LocalDate.now().plusDays(3)
                model!!.edit { PlannerDraft(destinationId="danang-vn",destinationQuery="Da Nang",startDate=start.toString(),endDate=start.plusDays(2).toString(),adults="2",budget="1000",currency="USD",transport="excluded",purpose="leisure") }
            }
            runBlocking { model!!.flush() }
        }
        await { var ready=false;scenario.onActivity { ready=it.findViewById<android.view.View>(if(valid) UiR.id.resume_draft_action else UiR.id.plan_trip_action)?.let { v -> v.isShown && v.isEnabled }==true };ready }
    }
    @Test fun allFourteenDestinationsInflateAndSurviveRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitShell(scenario)
            val graphs=listOf(
                UiR.id.home_graph to listOf(UiR.id.home_fragment),
                UiR.id.planner_graph to listOf(UiR.id.where_fragment,UiR.id.travelers_fragment,UiR.id.review_fragment,UiR.id.progress_fragment,UiR.id.itinerary_fragment,UiR.id.budget_fragment),
                UiR.id.trips_graph to listOf(UiR.id.trips_fragment,UiR.id.history_fragment),
                UiR.id.tools_graph to listOf(UiR.id.tools_fragment,UiR.id.currency_fragment,UiR.id.time_fragment,UiR.id.weather_fragment),
                UiR.id.settings_graph to listOf(UiR.id.settings_fragment)
            )
            for((graph, destinations) in graphs) {
                scenario.onActivity { (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(graph) }
                for(destination in destinations) {
                    scenario.onActivity {
                        val nav=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
                        if(nav.currentDestination?.id!=destination) nav.navigate(destination)
                        assertEquals(destination,nav.currentDestination?.id)
                    }
                    onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
                }
            }
            scenario.recreate()
            awaitShell(scenario)
            scenario.onActivity {
                val nav=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
                assertEquals(UiR.id.settings_fragment,nav.currentDestination?.id)
            }
        }
    }
    @Test fun languagePickerPersistsAcrossRecreationAndRelaunchWithoutLosingSettingsRoute() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitShell(scenario)
            scenario.onActivity { (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(UiR.id.settings_graph) }
            onView(withId(SettingsR.id.language_vietnamese)).perform(click())
            await {
                var matches=false
                scenario.onActivity { matches=it.resources.configuration.locales[0].language=="vi" }
                matches
            }
            awaitShell(scenario)
            scenario.recreate()
            awaitShell(scenario)
            scenario.onActivity {
                assertEquals("vi",it.resources.configuration.locales[0].language)
                assertEquals(UiR.id.settings_fragment,(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.currentDestination?.id)
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitShell(scenario)
            scenario.onActivity {
                assertEquals("vi",it.resources.configuration.locales[0].language)
                (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(UiR.id.settings_graph)
            }
            onView(withId(SettingsR.id.language_english)).perform(click())
            await { var en=false;scenario.onActivity { en=it.resources.configuration.locales[0].language=="en" };en }
        }
    }
    @Test fun platformResetUsesEnglishInsteadOfResurrectingAnOldVietnameseChoice() {
        if (Build.VERSION.SDK_INT < 33) return
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitShell(scenario)
            scenario.onActivity { (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(UiR.id.settings_graph) }
            onView(withId(SettingsR.id.language_vietnamese)).perform(click())
            await { var vi=false;scenario.onActivity { vi=it.resources.configuration.locales[0].language=="vi" };vi }
            var repository: LanguageRepository?=null
            scenario.onActivity { repository=it.languages }
            runBlocking { withTimeout(15_000) { repository!!.settings.first { it.pendingTag.isEmpty() && it.lastPlatformTag=="vi" } } }
            scenario.onActivity { it.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.getEmptyLocaleList() }
            awaitShell(scenario)
            await { var en=false;scenario.onActivity { en=it.resources.configuration.locales[0].language=="en" };en }
        }
    }

    @Test fun homeShortcutRestoresTheSameTripsStackAsBottomNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitShell(scenario)
            onView(withId(UiR.id.trips_graph)).perform(click())
            scenario.onActivity {
                val nav=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
                nav.currentBackStackEntry!!.savedStateHandle["review_marker"]="kept"
            }
            onView(withId(UiR.id.home_graph)).perform(click())
            scenario.onActivity { it.findViewById<androidx.recyclerview.widget.RecyclerView>(UiR.id.content_list).scrollToPosition(2) }
            onView(allOf(withId(UiR.id.row_secondary), androidx.test.espresso.matcher.ViewMatchers.withText(UiR.string.trips_title))).perform(click())
            scenario.onActivity {
                val nav=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
                assertEquals(UiR.id.trips_fragment,nav.currentDestination?.id)
                assertEquals("kept",nav.currentBackStackEntry!!.savedStateHandle.get<String>("review_marker"))
            }
        }
    }

    @Test fun plannerBackUpAndContextualSettingsMatchTheApprovedUi() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitShell(scenario)
            prepareWorkspace(scenario, valid=true)
            onView(withId(UiR.id.resume_draft_action)).perform(click())
            scenario.onActivity {
                val toolbar=it.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
                assertTrue(toolbar.menu.findItem(R.id.open_settings).isVisible)
                assertFalse(it.findViewById<NavigationBarView>(R.id.bottom_nav).isShown)
            }
            onView(withId(com.example.travelassistant.feature.planner.R.id.continue_plan)).perform(click())
            onView(withId(com.example.travelassistant.feature.planner.R.id.continue_plan)).perform(click())
            scenario.onActivity {
                val nav=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
                assertEquals(UiR.id.review_fragment,nav.currentDestination?.id)
                assertTrue(it.onSupportNavigateUp())
                assertEquals(UiR.id.travelers_fragment,nav.currentDestination?.id)
            }
            androidx.test.espresso.Espresso.pressBack()
            scenario.onActivity {
                val nav=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
                assertEquals(UiR.id.where_fragment,nav.currentDestination?.id)
                assertTrue(it.onSupportNavigateUp())
                assertEquals(UiR.id.home_fragment,nav.currentDestination?.id)
            }
        }
    }

    @Test fun aSecondClickFromTheOldHomeViewCannotDuplicatePlannerEntries() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitShell(scenario)
            prepareWorkspace(scenario)
            onView(withId(UiR.id.plan_trip_action)).check(matches(isDisplayed()))
            scenario.onActivity {
                val button=it.findViewById<android.view.View>(UiR.id.plan_trip_action)
                button.performClick();button.performClick()
            }
            await { var ready=false;scenario.onActivity { ready=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.currentDestination?.id==UiR.id.where_fragment };ready }
            scenario.onActivity {
                val nav=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
                assertTrue(nav.popBackStack())
                assertEquals(UiR.id.home_fragment,nav.currentDestination?.id)
            }
        }
    }

    @Test fun resourcesProvideVietnameseAndDefaultEnglishForAnUnsupportedLocale() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        fun localized(tag: String): android.content.Context {
            val config=android.content.res.Configuration(context.resources.configuration)
            config.setLocales(LocaleList.forLanguageTags(tag))
            return context.createConfigurationContext(config)
        }
        assertEquals("Trang chủ",localized("vi").getString(UiR.string.home_title))
        assertEquals("Home",localized("fr").getString(UiR.string.home_title))
        assertEquals("Retry",localized("fr").getString(UiR.string.retry))
    }

    @Test fun switchingLanguagePreservesTheToolsReturnRouteAndRelabelsTheToolbar() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitShell(scenario)
            onView(withId(UiR.id.tools_graph)).perform(click())
            scenario.onActivity {
                val nav=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
                nav.currentBackStackEntry!!.savedStateHandle["language_marker"]="unchanged"
            }
            onView(withId(R.id.open_settings)).perform(click())
            onView(withId(SettingsR.id.language_vietnamese)).perform(click())
            await { var vi=false;scenario.onActivity { vi=it.resources.configuration.locales[0].language=="vi" };vi }
            awaitShell(scenario)
            scenario.onActivity {
                assertTrue(it.onSupportNavigateUp())
                val nav=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
                assertEquals(UiR.id.tools_fragment,nav.currentDestination?.id)
                assertEquals("unchanged",nav.currentBackStackEntry!!.savedStateHandle.get<String>("language_marker"))
                assertEquals("Tiện ích",it.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title.toString())
            }
            onView(withId(R.id.open_settings)).perform(click())
            onView(withId(SettingsR.id.language_english)).perform(click())
            await { var en=false;scenario.onActivity { en=it.resources.configuration.locales[0].language=="en" };en }
        }
    }
}
