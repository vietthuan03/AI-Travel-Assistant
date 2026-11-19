package com.example.ai_travel_assistant

import com.example.ai_travel_assistant.data.ai.readItinerary
import com.example.ai_travel_assistant.data.local.TripsDatabase
import com.example.ai_travel_assistant.data.local.RoomTripRepository
import com.example.ai_travel_assistant.domain.trip.GenerateTrip
import com.example.ai_travel_assistant.domain.trip.TripRequest
import android.content.Context
import org.json.JSONObject
import java.util.UUID
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripHistoryTest {
    @Test
    fun rejectedWriteKeepsGeneratedResultAndPreviouslySavedHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TripsDatabase::class.java).build()
        try {
            val history = RoomTripRepository(database.trips())
            val request = TripRequest("Hà Nội", "Đà Nẵng", 0L, 0L, "Du lịch", "", "", 500_000)
            val itinerary = readItinerary("""{"days":[{"title":"Ngày 1","stops":[{"title":"Mỹ Khê"}]}]}""")
            val generate = GenerateTrip({ _, _ -> itinerary }, history::save)
            assertTrue(generate(request, "vi").saved)
            val committed = history.summaries().first().single()
            assertEquals(itinerary, history.open(committed.id)?.itinerary)

            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_trip BEFORE INSERT ON trips BEGIN SELECT RAISE(ABORT, 'write failed'); END"
            )
            val failed = generate(request, "vi")
            assertFalse(failed.saved)
            assertEquals(itinerary, failed.itinerary)
            assertEquals(listOf(committed), history.summaries().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun reopensAndDeletesTripFromVersionOne() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fileName = "history-upgrade-${UUID.randomUUID()}.db"
        val schemaPath = "${TripsDatabase::class.java.name}/1.json"
        val schema = instrumentation.context.assets.open(schemaPath).bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        val timeline = """{"days":[{"title":"Ngày 1","stops":[{"title":"Hội An"}]}]}"""
        try {
            context.openOrCreateDatabase(fileName, Context.MODE_PRIVATE, null).use { db ->
                val entity = schema.getJSONArray("entities").getJSONObject(0)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", "trips"))
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
                db.version = 1
                db.execSQL(
                    """INSERT INTO trips (id, origin, destination, departure, returnDate, purpose,
                        pace, notes, budgetAmount, budgetCurrency, createdAt, itineraryJson)
                        VALUES ('old', 'A', 'B', 0, 0, 'Vacation', '', '', 500, 'VND', 1, ?)""",
                    arrayOf(timeline),
                )
            }
            val database = Room.databaseBuilder(context, TripsDatabase::class.java, fileName).build()
            try {
                val history = RoomTripRepository(database.trips())
                val summary = history.summaries().first().single()
                assertEquals("old", summary.id)
                assertEquals("Hội An", history.open(summary.id)?.itinerary?.days?.single()?.stops?.single()?.title)
                history.delete(summary.id)
                assertTrue(history.summaries().first().isEmpty())
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(fileName)
        }
    }
}
