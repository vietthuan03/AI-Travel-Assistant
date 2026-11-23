package com.example.ai_travel_assistant.data.ai

import com.example.ai_travel_assistant.domain.trip.GenerationException
import com.example.ai_travel_assistant.domain.trip.PlanFailure
import com.example.ai_travel_assistant.domain.trip.TripDayMillis
import com.example.ai_travel_assistant.domain.trip.TripRequest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedItineraryTest {
    private val day = utc("2026-11-23 00:00")
    private val lateVietnam = utc("2026-11-23 07:56")
    private val trip = TripRequest(
        "Hà Nội", "Đà Nẵng", day, day, "Vacation", "Active", "",
        originTimeZone = "Asia/Ho_Chi_Minh",
        destinationTimeZone = "Asia/Ho_Chi_Minh",
    )
    private val response = """
        {"days":[
          {"date":"2026-11-23","title":"Đến Đà Nẵng","stops":[
            {"title":"Ăn sáng","time":"08:00","category":"food"},
            {"title":"Khởi hành lúc đã qua","time":"14:55","category":"transport"},
            {"title":"Chuyến bay chưa xác nhận","time":"17:00","category":"transport"},
            {"title":"Nghỉ ngơi","time":"","category":"rest"},
            {"title":"Tham quan buổi tối","time":"19:00","category":"sightseeing"}
          ]},
          {"date":"2026-11-24","title":"Khám phá","stops":[
            {"title":"Đi dạo ven biển","time":"08:00","category":"sightseeing"}
          ]}
        ]}
    """.trimIndent()

    @Test
    fun lateSameDayTripKeepsOnlyFeasibleLogisticsAndDoesNotExtendTheTrip() {
        val result = readGeneratedItinerary(response, trip, lateVietnam)
        val today = result.days!!.single()
        assertEquals("2026-11-23", today.date)
        assertEquals(listOf("Chuyến bay chưa xác nhận", "Nghỉ ngơi"), today.stops.map { it.title })
        assertTrue(today.stops.all { it.time.isBlank() })
        assertFalse(result.text.contains("Ăn sáng"))
        assertEquals(result.days, readItinerary(result.text).days)
    }

    @Test
    fun sightseeingCanMoveToTomorrowWhenTheUserIncludesIt() {
        val extended = trip.copy(returnDate = day + TripDayMillis)
        val days = readGeneratedItinerary(response, extended, lateVietnam).days!!
        assertEquals(listOf("2026-11-23", "2026-11-24"), days.map { it.date })
        assertEquals("Đi dạo ven biển", days.last().stops.single().title)
    }

    @Test
    fun clockUsesEachCityTimeZoneAndKeepsRemainingStopsBeforeLateCutoff() {
        val international = trip.copy(destination = "Tokyo", destinationTimeZone = "Asia/Tokyo")
        val time = utc("2026-11-23 05:00")
        val prompt = international.prompt("vi", time)
        assertTrue(prompt.contains("2026-11-23 12:00 (Asia/Ho_Chi_Minh)"))
        assertTrue(prompt.contains("2026-11-23 14:00 (Asia/Tokyo)"))
        val stops = readGeneratedItinerary(response, international, time).days!!.single().stops
        assertTrue(stops.any { it.title == "Tham quan buổi tối" })
        assertFalse(stops.any { it.title == "Ăn sáng" })
    }

    @Test
    fun unverifiedDateInNewOutputIsRejectedButOldHistoryRemainsReadable() {
        val legacy = """{"days":[{"title":"Old plan","stops":[{"title":"Stop"}]}]}"""
        assertEquals("Old plan", readItinerary(legacy).days!!.single().title)
        assertEquals(PlanFailure.InvalidSchedule, assertThrows(GenerationException::class.java) {
            readGeneratedItinerary(legacy, trip, lateVietnam)
        }.failure)
    }

    private fun utc(value: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(value)!!.time
}
