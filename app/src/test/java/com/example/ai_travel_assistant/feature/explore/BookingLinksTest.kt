package com.example.ai_travel_assistant.feature.explore

import org.junit.Assert.assertEquals
import org.junit.Test

class BookingLinksTest {
    @Test
    fun flightLinksCarryAirportsDatesAndPassengerCount() {
        val outbound = "https://www.skyscanner.com/transport/flights/han/dad/700101/"
        assertEquals(outbound + "?adultsv2=2", flightSearchUrl("HAN", "DAD", 0L, null, 2))
        assertEquals(outbound + "700102/?adultsv2=3", flightSearchUrl("HAN", "DAD", 0L, 86_400_000L, 3))
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
