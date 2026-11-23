package com.example.ai_travel_assistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import android.view.WindowManager
import org.junit.Rule
import org.junit.Test

class ExploreTabTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun label(id: Int) = context.getString(id)
    private fun control(id: Int) = compose.onNode(hasText(label(id)) and hasClickAction())

    private fun stepper(action: Int, field: Int) =
        compose.onNodeWithContentDescription(context.getString(action, label(field)))

    private fun openExplore() {
        compose.onNodeWithContentDescription(label(R.string.planner_open_menu)).performClick()
        control(R.string.tab_explore).performClick()
    }

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
        openExplore()
        control(R.string.explore_choose_city).performClick()
        control(R.string.city_danang).performClick()
        control(R.string.explore_flights).performClick()
        control(R.string.explore_sky).assertIsNotEnabled()
        control(R.string.explore_depart).performClick()
        control(R.string.explore_round).performClick()
        compose.onNodeWithText(label(R.string.explore_return)).assertExists()
        control(R.string.explore_cabin_guests).performScrollTo().performClick()
        control(R.string.explore_cabin).performScrollTo().performClick()
        control(R.string.explore_cabin_business).performClick()
        val addChild = stepper(R.string.explore_increase_count, R.string.explore_children)
        repeat(8) { addChild.performScrollTo().performClick() }
        addChild.assertIsNotEnabled()
        val addAdult = stepper(R.string.explore_increase_count, R.string.explore_adults)
        repeat(7) { addAdult.performScrollTo().performClick() }
        addAdult.assertIsNotEnabled()
        control(R.string.history_close).performScrollTo().performClick()
        control(R.string.explore_stays).performClick()
        control(R.string.explore_guests).performClick()
        val addRoom = stepper(R.string.explore_increase_count, R.string.explore_rooms)
        addRoom.performScrollTo().assertIsNotEnabled()
        addAdult.performScrollTo().performClick()
        addRoom.performScrollTo().performClick()
        addRoom.assertIsNotEnabled()
        stepper(R.string.explore_decrease_count, R.string.explore_adults).assertIsNotEnabled()
        stepper(R.string.explore_decrease_count, R.string.explore_children).assertIsNotEnabled()
        repeat(10) { addChild.performScrollTo().performClick() }
        addChild.assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.explore_child_age, 1)).assertExists()
        control(R.string.explore_booking).assertIsNotEnabled()
        scenario.recreate()
        showTestWindow()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.explore_room_count, 2, 2),
            substring = true).assertExists()
        control(R.string.history_close).performScrollTo().performClick()
        pressBack()
        compose.onNode(hasText(label(R.string.trip_origin)) and hasText(label(R.string.city_hanoi))).assertExists()
        pressBack()
        compose.onNodeWithText(label(R.string.app_exit_title)).assertIsDisplayed()
        control(R.string.app_exit_stay).performClick()
        compose.onNodeWithText(label(R.string.app_exit_title)).assertDoesNotExist()

        openExplore()
        control(R.string.explore_stays).performClick()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.explore_room_count, 2, 2),
            substring = true).assertExists()
        control(R.string.history_close).performScrollTo().performClick()
        control(R.string.explore_plan).performScrollTo().performClick()
        compose.onNode(hasText(label(R.string.trip_origin)) and hasText(label(R.string.city_hanoi))).assertExists()
        compose.onNode(hasText(label(R.string.destination)) and hasText(label(R.string.city_danang))).assertExists()
        openExplore()
        control(R.string.explore_stays).performClick()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.explore_room_count, 2, 2),
            substring = true).assertExists()
    }
}
