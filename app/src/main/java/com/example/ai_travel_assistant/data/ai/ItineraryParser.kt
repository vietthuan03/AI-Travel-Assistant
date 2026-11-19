package com.example.ai_travel_assistant.data.ai

import com.example.ai_travel_assistant.domain.trip.Itinerary
import com.example.ai_travel_assistant.domain.trip.ItineraryDay
import com.example.ai_travel_assistant.domain.trip.ItineraryStop
import org.json.JSONException
import org.json.JSONObject

internal fun readItinerary(output: String): Itinerary {
    val content = output.itineraryContent()
    val days = parseItinerary(content)
    return Itinerary(if (days == null) output else content, days)
}

private fun String.itineraryContent(): String =
    trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

private fun parseItinerary(content: String): List<ItineraryDay>? {
    if (!content.startsWith("{") || !content.endsWith("}")) return null
    return try {
        val days = JSONObject(content).getJSONArray("days")
        if (days.length() == 0) return null
        List(days.length()) { dayIndex ->
            val day = days.getJSONObject(dayIndex)
            val stops = day.getJSONArray("stops")
            if (stops.length() == 0) return null
            ItineraryDay(day.text("title"), List(stops.length()) { stopIndex ->
                val stop = stops.getJSONObject(stopIndex)
                val title = stop.text("title")
                if (title.isBlank()) return null
                ItineraryStop(
                    stop.text("time"), title, stop.text("category"),
                    stop.text("place"), stop.text("area"), stop.text("address"),
                    stop.text("from"), stop.text("to"), stop.text("duration"), stop.text("notes"),
                )
            })
        }
    } catch (_: JSONException) {
        null
    }
}

private fun JSONObject.text(name: String): String = (opt(name) as? String).orEmpty().trim()
