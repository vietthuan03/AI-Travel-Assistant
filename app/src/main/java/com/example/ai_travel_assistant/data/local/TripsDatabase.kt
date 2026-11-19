package com.example.ai_travel_assistant.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.ai_travel_assistant.domain.trip.TripSummary
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trips", primaryKeys = ["id"])
internal data class SavedTrip(
    @Embedded val summary: TripSummary,
    val pace: String,
    val notes: String,
    val itineraryJson: String,
)

@Dao
internal interface TripHistory {
    @Insert
    suspend fun save(trip: SavedTrip)

    @Query("""SELECT id, origin, destination, departure, returnDate, purpose, budgetAmount,
        budgetCurrency, createdAt FROM trips ORDER BY createdAt DESC, rowid DESC""")
    fun summaries(): Flow<List<TripSummary>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun open(id: String): SavedTrip?

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [SavedTrip::class], version = 1)
internal abstract class TripsDatabase : RoomDatabase() {
    abstract fun trips(): TripHistory

    companion object {
        @Volatile private var instance: TripsDatabase? = null

        fun get(context: Context): TripsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TripsDatabase::class.java, "trips.db")
                .build().also { instance = it }
        }
    }
}
