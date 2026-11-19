package com.example.ai_travel_assistant.data.ai

import com.example.ai_travel_assistant.domain.trip.TripRequest
import com.example.ai_travel_assistant.domain.trip.formatUtc
import java.text.SimpleDateFormat
import java.util.Locale

internal fun TripRequest.prompt(language: String): String = buildString {
    append("Suggest a daily itinerary in language $language. Destination: ${destination.trim()}. ")
    if (origin.isNotBlank()) append("Origin: ${origin.trim()}. ")
    departure?.let { append("Travel date: ${it.asPromptDate()}. ") }
    returnDate?.let { append("Return date: ${it.asPromptDate()}. ") }
    if (purpose.isNotBlank()) append("Purpose: ${purpose.trim()}. ")
    budgetAmount?.let { append("Total trip budget: $it $budgetCurrency. ") }
    append("Pace: $pace. Preferences: ${notes.trim()}. ")
    append(
        """
        Return one JSON object only, with this structure:
        {"days":[{"title":"localized day title","stops":[{"time":"HH:mm","title":"activity name","category":"transport|food|sightseeing|rest|other","place":"venue or landmark name","area":"city or district","address":"street address, if known","from":"starting point","to":"arrival point","duration":"localized duration","notes":"short practical description"}]}]}
        List each day and stop in chronological order. Use empty strings for unknown fields and choose just one category per stop.
        If travel and return dates are provided, cover each day in that range, including the departure and return days.
        Include from/to only for travel legs. Leave addresses blank when uncertain and never invent addresses or links.
        Flag unverified costs, hours or travel times in the relevant notes. Use local times and keep the text concise.
        """.trimIndent()
    )
}

private fun Long.asPromptDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).formatUtc(this)
