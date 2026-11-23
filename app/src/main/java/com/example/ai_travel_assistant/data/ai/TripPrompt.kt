package com.example.ai_travel_assistant.data.ai

import com.example.ai_travel_assistant.domain.trip.TripRequest
import com.example.ai_travel_assistant.domain.trip.timingAt
import java.util.TimeZone

internal fun TripRequest.prompt(language: String, nowMillis: Long = System.currentTimeMillis()): String = buildString {
    val timing = timingAt(nowMillis)
    append("Suggest a daily itinerary in language $language. Destination: ${destination.trim()}. ")
    if (origin.isNotBlank()) append("Origin: ${origin.trim()}. ")
    append("Travel dates (inclusive): ${timing.departureDate} through ${timing.returnDate}. ")
    append("Current local time at origin: ${timing.originNow} (${originTimeZone ?: TimeZone.getDefault().id}). ")
    append("Current local time at destination: ${timing.destinationNow} (${destinationTimeZone ?: TimeZone.getDefault().id}). ")
    if (timing.lateDeparture) {
        append("On the departure date include only feasible logistics: airport transfer or flight search, check-in, and rest. ")
    }
    if (purpose.isNotBlank()) append("Purpose: ${purpose.trim()}. ")
    budgetAmount?.let { append("Total trip budget: $it $budgetCurrency. ") }
    append("Pace: $pace. Preferences: ${notes.trim()}. ")
    append(
        """
        Return one JSON object only, with this structure:
        {"days":[{"date":"YYYY-MM-DD","title":"localized day title","stops":[{"time":"HH:mm","title":"activity name","category":"transport|food|sightseeing|rest|accommodation|other","place":"venue or landmark name","area":"city or district","address":"street address, if known","from":"starting point","to":"arrival point","duration":"localized duration","notes":"short practical description"}]}]}
        Cover only the selected date range and list days and stops in chronological order. Include the exact ISO date in each day.
        All suggested clock times are destination-local, in 24-hour HH:mm. Never schedule before or at the current destination clock.
        Put sightseeing on the next available day within the selected range when today is too short. If there is no next day in range, omit sightseeing.
        Flight schedules, actual arrival times, hotel availability, and venue hours are not verified. Never invent them.
        Treat unconfirmed transport, accommodation and rest as flexible steps with an empty time. Do not suggest an overnight stay past a same-day return.
        Keep all notes concise. Use empty strings for unknown fields, choose one category per stop, and include from/to only for travel legs.
        """.trimIndent()
    )
}
