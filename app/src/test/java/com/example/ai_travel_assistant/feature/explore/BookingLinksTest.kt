package com.example.ai_travel_assistant.feature.explore

import org.junit.Assert.assertEquals
import org.junit.Test

class BookingLinksTest {
    @Test
    fun flightLinksCarryAirportsDatesAndPassengerCount() {
        val outbound = "https://www.skyscanner.com/transport/flights/han/dad/700101/"
        assertEquals(outbound + "?adultsv2=2&cabinclass=economy", flightSearchUrl("HAN", "DAD", 0L, null, 2))
        assertEquals(outbound + "700102/?adultsv2=3&cabinclass=economy", flightSearchUrl("HAN", "DAD", 0L, 86_400_000L, 3))
    }

    @Test
    fun flightLinkIncludesEachChildAge() {
        assertEquals(
            "https://www.skyscanner.com/transport/flights/han/dad/700101/?adultsv2=2&childrenv2=4%7C12&cabinclass=economy",
            flightSearchUrl("HAN", "DAD", 0L, null, 2, listOf(4, 12)),
        )
    }

    @Test
    fun flightLinkIncludesYoungestAndOldestChildrenAndSelectedCabin() {
        assertEquals(
            "https://www.skyscanner.com/transport/flights/han/dad/700101/?adultsv2=4&childrenv2=0%7C1%7C17&cabinclass=business",
            flightSearchUrl("HAN", "DAD", 0L, null, 4, listOf(0, 1, 17), CabinClass.Business),
        )
    }

    @Test
    fun stayLinkIncludesChildCountAndAges() {
        assertEquals(
            "https://www.booking.com/searchresults.html?ss=Hanoi" +
                "&checkin=1970-01-01&checkout=1970-01-02&group_adults=2&no_rooms=1&group_children=2&age=0&age=8",
            staySearchUrl("Hanoi", 0L, 86_400_000L, 2, 1, listOf(0, 8)),
        )
    }

    @Test
    fun stayLinkEncodesVietnameseDestinationAndRoomSelection() {
        assertEquals(
            "https://www.booking.com/searchresults.html?ss=%C4%90%C3%A0+N%E1%BA%B5ng" +
                "&checkin=1970-01-01&checkout=1970-01-02&group_adults=3&no_rooms=2",
            staySearchUrl("Đà Nẵng", 0L, 86_400_000L, 3, 2),
        )
    }
}
