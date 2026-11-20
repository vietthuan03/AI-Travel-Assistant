package com.example.ai_travel_assistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.view.WindowManager
import org.junit.Rule
import org.junit.Test

class ExploreTabTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun label(id: Int) = context.getString(id)
    private fun control(id: Int) = compose.onNode(hasText(label(id)) and hasClickAction())

    @Test
    fun exploreRestoresRoomFormAndPutsOnlyDestinationIntoPlanner() {
        val scenario = compose.activityRule.scenario
        fun showTestWindow() = scenario.onActivity {
            @Suppress("DEPRECATION")
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        showTestWindow()
        control(R.string.trip_origin).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.city_hanoi)).performClick()
        control(R.string.tab_explore).performClick()
        control(R.string.city_danang).performClick()
        control(R.string.explore_flights).performClick()
        control(R.string.explore_sky).assertIsNotEnabled()
        control(R.string.explore_round).performClick()
        compose.onNodeWithText(label(R.string.explore_return)).assertExists()
        control(R.string.explore_stays).performClick()
        compose.onNode(hasText(label(R.string.explore_rooms)) and hasSetTextAction())
            .performScrollTo().performTextReplacement("2")
        scenario.recreate()
        showTestWindow()
        compose.onNode(hasText(label(R.string.explore_rooms)) and hasText("2")).assertExists()
        control(R.string.explore_plan).performScrollTo().performClick()
        compose.onNode(hasText(label(R.string.trip_origin)) and hasText(label(R.string.city_hanoi))).assertExists()
        compose.onNode(hasText(label(R.string.destination)) and hasText(label(R.string.city_danang))).assertExists()
        control(R.string.tab_explore).performClick()
        compose.onNode(hasText(label(R.string.explore_rooms)) and hasText("2")).assertExists()
    }
}
