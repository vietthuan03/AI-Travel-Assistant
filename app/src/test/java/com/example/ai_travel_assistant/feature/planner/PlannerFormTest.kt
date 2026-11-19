package com.example.ai_travel_assistant.feature.planner

import com.example.ai_travel_assistant.domain.trip.TripRequest
import com.example.ai_travel_assistant.ui.format.formatTripBudget
import com.example.ai_travel_assistant.ui.format.normalizeTripBudget
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class PlannerFormTest {
    @Test fun todayUsesDeviceCalendarDayAcrossNewYear() {
        val utc = TimeZone.getTimeZone("UTC")
        val instant = GregorianCalendar(utc).apply {
            clear()
            set(2026, Calendar.DECEMBER, 31, 18, 30)
        }.timeInMillis
        fun pickerDay(zone: String) = todayForPicker(GregorianCalendar(TimeZone.getTimeZone(zone)).apply {
            timeInMillis = instant
        })
        assertEquals(86_400_000L, pickerDay("Asia/Ho_Chi_Minh") - pickerDay("America/Los_Angeles"))
    }

    @Test fun budgetFormattingPreservesTheAmount() {
        assertEquals("3.000.000", formatTripBudget("3000000"))
        assertEquals("3000000", normalizeTripBudget("3.000.000"))
        assertNull(normalizeTripBudget("3.00.000"))
    }

    @Test fun requiresRouteDatesPurposeAndPositiveBudget() {
        val trip = TripRequest("A", "B", 1, 1, "Vacation", "", "", 500)
        assertTrue(trip.hasRequiredDetails)
        val invalid = listOf(trip.copy(origin = ""), trip.copy(destination = ""), trip.copy(purpose = ""),
            trip.copy(departure = null), trip.copy(returnDate = null), trip.copy(returnDate = 0),
            trip.copy(budgetAmount = null), trip.copy(budgetAmount = 0))
        assertTrue(invalid.none { it.hasRequiredDetails })
    }
}
