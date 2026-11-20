package com.example.travelassistant.core.domain

import java.time.LocalDate

enum class BookingKind { FLIGHT, STAY }
enum class BookingIssue { ORIGIN, DESTINATION, SAME_AIRPORT, START, END, PARTY }

data class BookingSearch(
    val kind: BookingKind,
    val destination: ExploreDestination?,
    val start: LocalDate?,
    val end: LocalDate?,
    val adults: Int = 1,
    val origin: ExploreDestination? = null,
    val roundTrip: Boolean = true,
    val rooms: Int = 1,
) {
    val needsEndDate get() = kind == BookingKind.STAY || roundTrip

    fun issue(today: LocalDate): BookingIssue? = when {
        kind == BookingKind.FLIGHT && origin == null -> BookingIssue.ORIGIN
        destination == null -> BookingIssue.DESTINATION
        kind == BookingKind.FLIGHT && origin?.airport == destination.airport -> BookingIssue.SAME_AIRPORT
        start == null || start.isBefore(today) -> BookingIssue.START
        needsEndDate && (end == null || end.isBefore(start) || (kind == BookingKind.STAY && end == start)) -> BookingIssue.END
        adults !in 1..8 || (kind == BookingKind.STAY && rooms !in 1..adults) -> BookingIssue.PARTY
        else -> null
    }
}

data class BookingLink(val url: String, val carriesSearch: Boolean)

interface BookingLinkRepository {
    fun publicSiteWithoutTemplate(kind: BookingKind): String?
    fun link(search: BookingSearch): BookingLink
}
