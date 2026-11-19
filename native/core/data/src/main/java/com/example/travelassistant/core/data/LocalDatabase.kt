package com.example.travelassistant.core.data

import androidx.room.*
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// Saved-trip summaries remain separate from the recoverable planner workspace.
@Entity(tableName = "saved_trip_summaries")
data class SavedTripEntity(@PrimaryKey val id: String, val title: String)
@Dao
interface TripDao {
    @Query("SELECT * FROM saved_trip_summaries ORDER BY title, id")
    fun observeSavedTrips(): Flow<List<SavedTripEntity>>
}
@Database(entities = [SavedTripEntity::class, WorkspaceEntity::class, CandidateEntity::class, RevisionEntity::class, WorkspaceBase::class, GenerationEntity::class, CancelEntity::class], version = 4, exportSchema = true)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun generation(): GenerationDao
    abstract fun plans(): PlanDao
    abstract fun trips(): TripDao
    abstract fun workspace(): WorkspaceDao
    companion object {
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3,4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS generation_operation (slot INTEGER NOT NULL PRIMARY KEY, requestId TEXT NOT NULL, serverId TEXT, body TEXT NOT NULL, ownerHash TEXT NOT NULL, state TEXT NOT NULL, error TEXT, baseTrip TEXT, baseVersion INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS generation_cancel_outbox (requestId TEXT NOT NULL PRIMARY KEY, ownerHash TEXT NOT NULL)")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2,3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS plan_candidates (slot INTEGER NOT NULL PRIMARY KEY, candidateId TEXT NOT NULL, tripId TEXT, baseVersion INTEGER NOT NULL, payload TEXT NOT NULL, readyAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_plan_candidates_candidateId ON plan_candidates(candidateId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS plan_revisions (tripId TEXT NOT NULL, version INTEGER NOT NULL, candidateId TEXT NOT NULL, payload TEXT NOT NULL, readyAt INTEGER NOT NULL, savedAt INTEGER NOT NULL, PRIMARY KEY(tripId,version))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_plan_revisions_candidateId ON plan_revisions(candidateId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS workspace_base (slot INTEGER NOT NULL PRIMARY KEY, tripId TEXT NOT NULL, version INTEGER NOT NULL)")
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `workspace_draft` (`id` INTEGER NOT NULL, `destinationId` TEXT NOT NULL, `destinationQuery` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT NOT NULL, `adults` TEXT NOT NULL, `children` TEXT NOT NULL, `childBands` TEXT NOT NULL, `budget` TEXT NOT NULL, `currency` TEXT NOT NULL, `transport` TEXT NOT NULL, `originId` TEXT NOT NULL, `originQuery` TEXT NOT NULL, `purpose` TEXT NOT NULL, `pace` TEXT NOT NULL, `interests` TEXT NOT NULL, `arrival` TEXT NOT NULL, `departure` TEXT NOT NULL, `notes` TEXT NOT NULL, `step` INTEGER NOT NULL, `outputLanguage` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}
class LocalTripRepository @Inject constructor(dao: TripDao) : TripRepository {
    override val savedTrips = dao.observeSavedTrips().map { rows -> rows.map { SavedTripSummary(it.id,it.title) } }
}
