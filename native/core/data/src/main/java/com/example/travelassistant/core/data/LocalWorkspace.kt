package com.example.travelassistant.core.data
import androidx.room.withTransaction

import androidx.room.*
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@Entity(tableName = "workspace_draft")
data class WorkspaceEntity(
    @PrimaryKey val id: Int = 1,
    val destinationId: String = "",
    val destinationQuery: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val adults: String = "1",
    val children: String = "0",
    val childBands: String = "",
    val budget: String = "",
    val currency: String = "USD",
    val transport: String = "",
    val originId: String = "",
    val originQuery: String = "",
    val purpose: String = "",
    val pace: String = "moderate",
    val interests: String = "",
    val arrival: String = "",
    val departure: String = "",
    val notes: String = "",
    val step: Int = 1,
    val outputLanguage: String = "en"
) {
    fun domain() = PlannerDraft(destinationId = destinationId, destinationQuery = destinationQuery, startDate = startDate, endDate = endDate, adults = adults, children = children, childBands = childBands, budget = budget, currency = currency, transport = transport, originId = originId, originQuery = originQuery, purpose = purpose, pace = pace, interests = interests, arrival = arrival, departure = departure, notes = notes, step = step, outputLanguage = outputLanguage)
    companion object { fun from(d: PlannerDraft) = WorkspaceEntity(destinationId = d.destinationId, destinationQuery = d.destinationQuery, startDate = d.startDate, endDate = d.endDate, adults = d.adults, children = d.children, childBands = d.childBands, budget = d.budget, currency = d.currency, transport = d.transport, originId = d.originId, originQuery = d.originQuery, purpose = d.purpose, pace = d.pace, interests = d.interests, arrival = d.arrival, departure = d.departure, notes = d.notes, step = d.step, outputLanguage = d.outputLanguage) }
}
@Dao
abstract class WorkspaceDao {
    @Query("SELECT * FROM workspace_draft WHERE id = 1")
    abstract fun observe(): Flow<WorkspaceEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun write(value: WorkspaceEntity)
    @Query("DELETE FROM workspace_draft")
    abstract suspend fun discard()
    @Query("DELETE FROM saved_trip_summaries")
    abstract suspend fun deleteTripSummaries()
    @Transaction
    open suspend fun removeAll() { discard(); deleteTripSummaries() }
}
class LocalWorkspaceRepository @Inject constructor(private val db: LocalDatabase) : WorkspaceRepository {
    private val dao get()=db.workspace()
    override val draft = dao.observe().map { it?.domain() }
    override suspend fun write(draft: PlannerDraft) = dao.write(WorkspaceEntity.from(draft))
    override suspend fun discard() = db.withTransaction { db.invalidateGeneration();db.plans().clearCandidate();db.plans().clearBase();dao.discard() }
    override suspend fun removeAllLocalTrips() = db.withTransaction { db.invalidateGeneration();db.plans().clearCandidate();db.plans().clearBase();db.plans().clearRevisions();dao.removeAll() }
}
