package com.example.ai_travel_assistant.domain.trip

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal const val TripDayMillis = 86_400_000L
private const val LateTravelCutoff = "14:30"
private val clockTime = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")

internal data class TripTiming(
    val departureDate: String,
    val returnDate: String,
    val originNow: String,
    val destinationNow: String,
) {
    val destinationToday get() = destinationNow.substringBefore(' ')
    private val destinationTime get() = destinationNow.substringAfter(' ')

    val lateDeparture: Boolean
        get() = listOf(originNow, destinationNow).any { local ->
            local.substringBefore(' ') == departureDate &&
                local.substringAfter(' ') >= LateTravelCutoff
        }

    fun canInclude(date: String, stop: ItineraryStop): Boolean {
        val logistics = stop.category.lowercase(Locale.ROOT) in setOf("transport", "rest", "accommodation")
        if (date == departureDate && lateDeparture && !logistics) return false
        val time = stop.time
        if (time.isEmpty()) return date != destinationToday || logistics
        return clockTime.matches(time) && (date != destinationToday || time > destinationTime)
    }
}

internal fun TripRequest.timingAt(nowMillis: Long): TripTiming = TripTiming(
    departureDate = departure?.let(::tripDate).orEmpty(),
    returnDate = returnDate?.let(::tripDate).orEmpty(),
    originNow = localTripClock(nowMillis, originTimeZone),
    destinationNow = localTripClock(nowMillis, destinationTimeZone),
)

internal fun tripDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).formatUtc(millis)

private fun localTripClock(nowMillis: Long, zone: String?): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = zone?.let(TimeZone::getTimeZone) ?: TimeZone.getDefault()
    }.format(Date(nowMillis))
