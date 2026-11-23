package com.example.ai_travel_assistant.data.ai

import com.example.ai_travel_assistant.domain.trip.GenerationException
import com.example.ai_travel_assistant.domain.trip.Itinerary
import com.example.ai_travel_assistant.domain.trip.ItineraryDay
import com.example.ai_travel_assistant.domain.trip.ItineraryStop
import com.example.ai_travel_assistant.domain.trip.PlanFailure
import com.example.ai_travel_assistant.domain.trip.TripRequest
import com.example.ai_travel_assistant.domain.trip.timingAt
import java.text.SimpleDateFormat
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal fun readItinerary(output: String): Itinerary {
    val content = output.itineraryContent()
    val days = parseItinerary(content)
    return Itinerary(if (days == null) output else content, days)
}

private fun String.itineraryContent(): String =
    trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

private val scheduleDate = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

internal fun readGeneratedItinerary(
    output: String,
    request: TripRequest,
    nowMillis: Long = System.currentTimeMillis(),
): Itinerary {
    val timing = request.timingAt(nowMillis)
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    try {
        val json = JSONObject(output.itineraryContent())
        val days = parseItinerary(json) ?: throw GenerationException(PlanFailure.InvalidSchedule)
        val sourceDays = json.getJSONArray("days")
        val schedule = JSONArray()
        days.withIndex()
            .filter { (_, day) ->
                day.date in timing.departureDate..timing.returnDate && day.date >= timing.destinationToday &&
                    scheduleDate.matches(day.date) && runCatching { dateFormat.parse(day.date) }.isSuccess
            }
            .distinctBy { it.value.date }
            .sortedBy { it.value.date }
            .forEach { (dayIndex, day) ->
                val sourceDay = sourceDays.getJSONObject(dayIndex)
                val sourceStops = sourceDay.getJSONArray("stops")
                val validStops = JSONArray()
                day.stops.forEachIndexed { index, stop ->
                    if (timing.canInclude(day.date, stop)) {
                        val source = sourceStops.getJSONObject(index)
                        if (stop.category.lowercase(Locale.ROOT) in setOf("transport", "accommodation")) {
                            source.put("time", "")
                        }
                        validStops.put(source)
                    }
                }
                schedule.put(sourceDay.put("stops", validStops))
            }
        if (schedule.length() == 0) throw GenerationException(PlanFailure.InvalidSchedule)
        val cleaned = json.put("days", schedule)
        return Itinerary(cleaned.toString(), parseItinerary(cleaned))
    } catch (_: JSONException) {
        throw GenerationException(PlanFailure.InvalidSchedule)
    }
}

private fun parseItinerary(content: String): List<ItineraryDay>? {
    if (!content.startsWith("{") || !content.endsWith("}")) return null
    return try {
        parseItinerary(JSONObject(content))
    } catch (_: JSONException) {
        null
    }
}

private fun parseItinerary(json: JSONObject): List<ItineraryDay>? {
    val days = json.getJSONArray("days")
    if (days.length() == 0) return null
    return List(days.length()) { dayIndex ->
        val day = days.getJSONObject(dayIndex)
        val date = day.text("date")
        val stops = day.getJSONArray("stops")
        if (stops.length() == 0 && date.isBlank()) return null
        ItineraryDay(day.text("title"), List(stops.length()) { stopIndex ->
            val stop = stops.getJSONObject(stopIndex)
            val title = stop.text("title")
            if (title.isBlank()) return null
            ItineraryStop(
                stop.text("time"), title, stop.text("category"),
                stop.text("place"), stop.text("area"), stop.text("address"),
                stop.text("from"), stop.text("to"), stop.text("duration"), stop.text("notes"),
            )
        }, date)
    }
}

private fun JSONObject.text(name: String): String = (opt(name) as? String).orEmpty().trim()
