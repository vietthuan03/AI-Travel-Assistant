package com.example.travelassistant.core.data

import com.example.travelassistant.core.domain.BookingLink
import org.junit.Assert.assertEquals
import org.junit.Test

class WebBookingLinksTest {
    private val fallback = "https://example.test/"
    private val input = mapOf("destination" to "Hà Nội & Hồ Tây", "rooms" to "2")

    @Test fun configuredLinkKeepsHostAndEncodesSearchValues() {
        val link = buildLink("https://partner.test/stays?city={destination}&rooms={rooms}", input, fallback)
        assertEquals(
            BookingLink("https://partner.test/stays?city=H%C3%A0%20N%E1%BB%99i%20%26%20H%E1%BB%93%20T%C3%A2y&rooms=2", true),
            link,
        )
    }

    @Test fun missingOrUnsafeTemplateUsesPublicLink() {
        val expected = BookingLink(fallback, false)
        assertEquals(expected, buildLink("", input, fallback))
        assertEquals(expected, buildLink("https://partner.test/?city={destination}", input, fallback))
        assertEquals(expected, buildLink("http://partner.test/?city={destination}&rooms={rooms}", input, fallback))
        assertEquals(expected, buildLink("https://{destination}.test/?rooms={rooms}", input, fallback))
    }
}
