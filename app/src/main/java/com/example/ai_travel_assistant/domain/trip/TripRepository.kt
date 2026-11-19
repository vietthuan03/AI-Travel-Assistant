package com.example.ai_travel_assistant.domain.trip

import kotlinx.coroutines.flow.Flow

internal data class TripSummary(
    val id: String,
    val origin: String,
    val destination: String,
    val departure: Long,
    val returnDate: Long,
    val purpose: String,
    val budgetAmount: Long,
    val budgetCurrency: String,
    val createdAt: Long,
)

internal data class StoredTrip(val summary: TripSummary, val itinerary: Itinerary)

internal interface TripRepository {
    suspend fun save(request: TripRequest, itinerary: Itinerary)
    fun summaries(): Flow<List<TripSummary>>
    suspend fun open(id: String): StoredTrip?
    suspend fun delete(id: String)
}
