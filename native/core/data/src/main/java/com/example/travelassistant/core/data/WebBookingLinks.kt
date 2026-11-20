package com.example.travelassistant.core.data

import com.example.travelassistant.core.domain.BookingKind
import com.example.travelassistant.core.domain.BookingLink
import com.example.travelassistant.core.domain.BookingLinkRepository
import com.example.travelassistant.core.domain.BookingSearch
import com.example.travelassistant.core.domain.TravelCatalog
import java.net.URI
import java.net.URLEncoder
import javax.inject.Inject

class WebBookingLinks @Inject constructor() : BookingLinkRepository {
    override fun publicSiteWithoutTemplate(kind: BookingKind): String? =
        publicSite(kind).takeIf { template(kind).isBlank() }

    override fun link(search: BookingSearch): BookingLink {
        val destination = requireNotNull(search.destination)
        val start = requireNotNull(search.start).toString()
        val end = if (search.needsEndDate) requireNotNull(search.end).toString() else ""
        val adults = search.adults.toString()
        return when (search.kind) {
            BookingKind.FLIGHT -> buildLink(
                template(search.kind),
                mapOf(
                    "origin" to requireNotNull(search.origin).airport,
                    "destination" to destination.airport,
                    "start" to start,
                    "end" to end,
                    "adults" to adults,
                ),
                publicSite(search.kind),
            )
            BookingKind.STAY -> buildLink(
                template(search.kind),
                mapOf(
                    "destination" to requireNotNull(TravelCatalog.place(destination.placeId)).name,
                    "start" to start,
                    "end" to end,
                    "adults" to adults,
                    "rooms" to search.rooms.toString(),
                ),
                publicSite(search.kind),
            )
        }
    }

    private fun template(kind: BookingKind) = when (kind) {
        BookingKind.FLIGHT -> BuildConfig.FLIGHT_SEARCH_URL
        BookingKind.STAY -> BuildConfig.STAY_SEARCH_URL
    }

    private fun publicSite(kind: BookingKind) = when (kind) {
        BookingKind.FLIGHT -> "https://www.skyscanner.com/"
        BookingKind.STAY -> "https://www.booking.com/"
    }
}

internal fun buildLink(template: String, fields: Map<String, String>, fallback: String): BookingLink {
    val tokens = Regex("\\{([a-z]+)\\}")
    val fieldsInTemplate = tokens.findAll(template).map { it.groupValues[1] }.toSet()
    if (fieldsInTemplate != fields.keys) return BookingLink(fallback, carriesSearch = false)

    val original = parseHttps(tokens.replace(template, "field"))
    val expanded = parseHttps(tokens.replace(template) { token ->
        URLEncoder.encode(fields.getValue(token.groupValues[1]), "UTF-8").replace("+", "%20")
    })
    if (original == null || expanded == null || expanded.rawAuthority != original.rawAuthority) {
        return BookingLink(fallback, carriesSearch = false)
    }
    return BookingLink(expanded.toASCIIString(), carriesSearch = true)
}

private fun parseHttps(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
    it.scheme == "https" && it.host != null && it.userInfo == null && it.rawFragment == null
}
