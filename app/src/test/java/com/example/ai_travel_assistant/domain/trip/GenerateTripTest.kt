package com.example.ai_travel_assistant.domain.trip

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GenerateTripTest {
    private val request = TripRequest("A", "B", 0, 0, "Vacation", "", "", 500)
    private val plan = Itinerary("{}", listOf(ItineraryDay("Day 1", emptyList())))

    @Test fun savesOnceAfterGeneration() = runBlocking {
        val writes = mutableListOf<Itinerary>()
        val generate = GenerateTrip({ _, _ -> plan }) { _, itinerary -> writes += itinerary }
        assertTrue(generate(request, "vi").saved)
        assertEquals(listOf(plan), writes)
    }

    @Test fun writeFailurePreservesItinerary() = runBlocking {
        val generate = GenerateTrip({ _, _ -> plan }) { _, _ -> error("disk full") }
        assertEquals(GeneratedTrip(plan, saved = false), generate(request, "vi"))
    }

    @Test fun unstructuredResponseIsDisplayedWithoutWriting() = runBlocking {
        val text = Itinerary("## Day 1", null)
        val generate = GenerateTrip({ _, _ -> text }) { _, _ -> throw AssertionError("Unexpected write") }
        assertEquals(GeneratedTrip(text, saved = false), generate(request, "vi"))
    }

    @Test(expected = CancellationException::class)
    fun cancellationPropagates() {
        runBlocking {
            GenerateTrip({ _, _ -> plan }) { _, _ -> throw CancellationException() }(request, "vi")
        }
    }
}
