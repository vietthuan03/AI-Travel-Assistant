package com.example.travelassistant.core.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookingSearchTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val hanoi = requireNotNull(ExploreCatalog.destination("hanoi-vn"))
    private val danang = requireNotNull(ExploreCatalog.destination("danang-vn"))
    private val flight = BookingSearch(BookingKind.FLIGHT, danang, today, null, origin = hanoi)
    private val stay = BookingSearch(BookingKind.STAY, danang, today, today.plusDays(1))

    @Test fun flightDatesRespectTripTypeAndAllowSameDayReturn() {
        assertEquals(BookingIssue.END, flight.issue(today))
        assertNull(flight.copy(roundTrip = false).issue(today))
        assertNull(flight.copy(end = today).issue(today))
        assertEquals(BookingIssue.START, flight.copy(start = today.minusDays(1)).issue(today))
    }

    @Test fun flightRequiresDistinctAirports() {
        assertEquals(BookingIssue.ORIGIN, flight.copy(origin = null).issue(today))
        assertEquals(BookingIssue.SAME_AIRPORT, flight.copy(origin = danang).issue(today))
    }

    @Test fun stayRequiresAtLeastOneNightAndOneAdultPerRoom() {
        assertEquals(BookingIssue.END, stay.copy(end = today).issue(today))
        assertEquals(BookingIssue.PARTY, stay.copy(rooms = 2).issue(today))
        assertNull(stay.copy(adults = 2, rooms = 2).issue(today))
    }
}
