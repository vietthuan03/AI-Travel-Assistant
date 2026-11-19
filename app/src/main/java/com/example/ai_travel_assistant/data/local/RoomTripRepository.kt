package com.example.ai_travel_assistant.data.local

import com.example.ai_travel_assistant.data.ai.readItinerary
import com.example.ai_travel_assistant.domain.trip.Itinerary
import com.example.ai_travel_assistant.domain.trip.StoredTrip
import com.example.ai_travel_assistant.domain.trip.TripRepository
import com.example.ai_travel_assistant.domain.trip.TripRequest
import com.example.ai_travel_assistant.domain.trip.TripSummary
import android.util.Log
import kotlinx.coroutines.CancellationException
import java.util.UUID

internal class RoomTripRepository(private val dao: TripHistory) : TripRepository {
    override fun summaries() = dao.summaries()

    override suspend fun open(id: String): StoredTrip? = dao.open(id)?.let { row ->
        StoredTrip(row.summary, readItinerary(row.itineraryJson))
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun save(request: TripRequest, itinerary: Itinerary) {
        val summary = TripSummary(
            UUID.randomUUID().toString(), request.origin, request.destination,
            requireNotNull(request.departure), requireNotNull(request.returnDate), request.purpose,
            requireNotNull(request.budgetAmount), request.budgetCurrency, System.currentTimeMillis(),
        )
        try {
            dao.save(SavedTrip(summary, request.pace, request.notes, itinerary.text))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("TripHistory", "Save failed: ${failure.javaClass.simpleName}")
            throw failure
        }
    }
}
