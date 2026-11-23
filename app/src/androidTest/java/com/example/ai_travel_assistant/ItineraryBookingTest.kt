package com.example.ai_travel_assistant

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ai_travel_assistant.domain.trip.Itinerary
import com.example.ai_travel_assistant.domain.trip.TripRequest
import com.example.ai_travel_assistant.domain.trip.formatUtc
import com.example.ai_travel_assistant.feature.planner.PlannerForm
import com.example.ai_travel_assistant.feature.planner.PlannerUiState
import com.example.ai_travel_assistant.ui.theme.AiTravelAssistantTheme
import java.text.DateFormat
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

class ItineraryBookingTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bookingUsesTheGeneratedTripAfterPlannerIsEdited() {
        val day = TimeUnit.DAYS.toMillis(1)
        val departure = System.currentTimeMillis() / day * day + 2 * day
        val returnDate = departure + 3 * day
        val trip = TripRequest(
            origin = context.getString(R.string.city_hanoi),
            destination = context.getString(R.string.city_danang),
            departure = departure,
            returnDate = returnDate,
            purpose = "Vacation",
            pace = "",
            notes = "",
            budgetAmount = 500_000L,
        )
        val success = PlannerUiState.Success(Itinerary("Da Nang itinerary", null), trip)
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val expectedDates = context.getString(
            R.string.trip_route, dateFormat.formatUtc(departure), dateFormat.formatUtc(returnDate),
        )

        compose.setContent {
            AiTravelAssistantTheme {
                PlannerForm(success, generate = { _, _ -> }, onExit = {}, openHistory = {})
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.planner_open_menu)).performClick()
        control(R.string.planner_menu_trip_details).performClick()
        control(R.string.destination).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.city_hcmc)).performClick()

        control(R.string.explore_flights).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.trip_route, "HAN", "DAD")).assertExists()
        compose.onNodeWithText(expectedDates).assertExists()
        control(R.string.history_close).performClick()
        pressBack()

        control(R.string.explore_stays).performScrollTo().performClick()
        compose.onNodeWithText(expectedDates).assertExists()
        control(R.string.history_close).performClick()
        compose.onNodeWithText(trip.destination.substringBefore(',')).assertExists()
    }

    private fun control(stringId: Int) = compose.onNode(
        hasText(context.getString(stringId)) and hasClickAction(),
    )
}
