package com.example.ai_travel_assistant.domain.trip

internal data class Itinerary(val text: String, val days: List<ItineraryDay>?)

internal data class ItineraryDay(val title: String, val stops: List<ItineraryStop>, val date: String = "")

internal data class ItineraryStop(
    val time: String,
    val title: String,
    val category: String,
    val place: String,
    val area: String,
    val address: String,
    val from: String,
    val to: String,
    val duration: String,
    val notes: String,
)
