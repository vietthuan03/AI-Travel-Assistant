package com.example.ai_travel_assistant.feature.explore

import com.example.ai_travel_assistant.domain.trip.formatUtc
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

private fun Long.partnerDate(pattern: String) = SimpleDateFormat(pattern, Locale.US).formatUtc(this)

internal fun flightSearchUrl(from: String, to: String, departure: Long, returnDate: Long?, adults: Int) =
    buildString {
        append("https://www.skyscanner.com/transport/flights/")
        append(from.lowercase(Locale.ROOT)).append('/').append(to.lowercase(Locale.ROOT)).append('/')
        append(departure.partnerDate("yyMMdd")).append('/')
        returnDate?.let { append(it.partnerDate("yyMMdd")).append('/') }
        append("?adultsv2=").append(adults)
    }

internal fun staySearchUrl(city: String, checkIn: Long, checkOut: Long, adults: Int, rooms: Int) =
    buildString {
        append("https://www.booking.com/searchresults.html?ss=").append(URLEncoder.encode(city, "UTF-8"))
        append("&checkin=").append(checkIn.partnerDate("yyyy-MM-dd"))
        append("&checkout=").append(checkOut.partnerDate("yyyy-MM-dd"))
        append("&group_adults=").append(adults).append("&no_rooms=").append(rooms)
    }
