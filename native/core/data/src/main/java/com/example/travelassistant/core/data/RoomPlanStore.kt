package com.example.travelassistant.core.data
import androidx.room.*
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.domain.PlanStore
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

@Entity(tableName="plan_candidates",indices=[Index(value=["candidateId"],unique=true)])
data class CandidateEntity(@PrimaryKey val slot: Int=1,val candidateId: String,val tripId: String?,val baseVersion: Int,val payload: String,val readyAt: Long,val expiresAt: Long)
@Entity(tableName="plan_revisions",primaryKeys=["tripId","version"],indices=[Index(value=["candidateId"],unique=true)])
data class RevisionEntity(val tripId: String,val version: Int,val candidateId: String,val payload: String,val readyAt: Long,val savedAt: Long)
@Entity(tableName="workspace_base")
data class WorkspaceBase(@PrimaryKey val slot: Int=1,val tripId: String,val version: Int)
@Dao interface PlanDao {
 @Query("SELECT * FROM plan_candidates WHERE slot=1") suspend fun candidate(): CandidateEntity?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun candidate(value: CandidateEntity)
 @Query("DELETE FROM plan_candidates") suspend fun clearCandidate()
 @Query("SELECT * FROM plan_revisions WHERE tripId=:id ORDER BY version DESC") suspend fun history(id: String): List<RevisionEntity>
 @Query("SELECT * FROM plan_revisions WHERE candidateId=:id") suspend fun source(id: String): RevisionEntity?
 @Query("SELECT * FROM plan_revisions WHERE tripId=:id AND (:version=0 OR version=:version) ORDER BY version DESC LIMIT 1") suspend fun revision(id: String,version: Int): RevisionEntity?
 @Query("SELECT MAX(version) FROM plan_revisions WHERE tripId=:id") suspend fun currentVersion(id: String): Int?
 @Insert suspend fun revision(value: RevisionEntity)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun summary(value: SavedTripEntity)
 @Query("SELECT * FROM workspace_base WHERE slot=1") suspend fun base(): WorkspaceBase?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun base(value: WorkspaceBase)
 @Query("DELETE FROM workspace_base") suspend fun clearBase()
 @Query("DELETE FROM plan_revisions") suspend fun clearRevisions()
}
class RoomPlanStore @Inject constructor(private val db: LocalDatabase): PlanStore {
 private val dao get()=db.plans()
 private fun canonical(d: PlannerDraft)=d.copy(step=1,destinationQuery="",originQuery="",budget=BriefValidator.decimal(d.budget)?.stripTrailingZeros()?.toPlainString() ?: d.budget,adults=d.adults.toIntOrNull()?.toString() ?: d.adults,children=d.children.toIntOrNull()?.toString() ?: d.children,interests=d.interests.split(',').filter(String::isNotBlank).sorted().joinToString(","))
 private fun RevisionEntity.domain(): StoredPlan { val (b,p)=PlanCodec.decode(payload);return StoredPlan(candidateId,tripId,version,b,p.copy(version=version),readyAt,Long.MAX_VALUE) }
 override suspend fun committed(candidateId: String)=dao.source(candidateId)?.domain()
 override suspend fun candidate(): StoredPlan? = db.withTransaction {
  val c=dao.candidate() ?: return@withTransaction null
  if(System.currentTimeMillis()>=c.expiresAt) { dao.clearCandidate();return@withTransaction null }
  val (b,p)=PlanCodec.decode(c.payload);StoredPlan(c.candidateId,c.tripId,c.baseVersion,b,p,c.readyAt,c.expiresAt)
 }
 override suspend fun revision(tripId: String,version: Int): StoredPlan? = dao.revision(tripId,version)?.domain()
 override suspend fun history(tripId: String)=dao.history(tripId).map { it.domain() }
 override suspend fun receive(candidateId: String,brief: PlannerDraft,plan: PlanPresentation,readyAt: Long,baseTripId: String?,baseVersion: Int) {
  // Device and backend wall clocks may differ slightly; retain the original server deadline.
  require(candidateId.isNotBlank() && readyAt<=System.currentTimeMillis()+60_000 && readyAt>0)
  require(BriefValidator.validate(brief,java.time.LocalDate.parse(brief.startDate)).isEmpty())
  require(plan.days.isNotEmpty() && plan.language==brief.outputLanguage && plan.currency==brief.currency && plan.requestedBudget.compareTo(BriefValidator.decimal(brief.budget))==0)
  require(plan.days.first().date==brief.startDate && plan.days.last().date==brief.endDate && plan.days.all { it.activities.isNotEmpty() })
  require(readyAt+86_400_000>System.currentTimeMillis())
  require(plan.title.isNotBlank() && plan.title.length<=120 && plan.assumption.length<=8000 && plan.language in setOf("en","vi"))
  require(plan.days.all { d -> d.title.length<=300 && d.activities.size in 1..24 && d.activities.all { a -> a.title.isNotBlank() && a.title.length<=200 && a.area.length<=200 && a.note.length<=4000 && a.durationMinutes in 1..1440 && a.time.matches(Regex("[0-9]{2}:[0-9]{2}")) && runCatching{java.time.LocalTime.parse(a.time)}.isSuccess } })
  val categories=setOf("stay","food","local","activities")+if(brief.transport=="included") setOf("transport") else emptySet()
  require(plan.costs.map{it.category}.toSet()==categories && plan.costs.size==categories.size)
  require(plan.group==(brief.adults.toInt()+brief.children.toInt()) && plan.transportIncluded==(brief.transport=="included"))
  val start=java.time.LocalDate.parse(brief.startDate)
  require(plan.days.size==java.time.temporal.ChronoUnit.DAYS.between(start,java.time.LocalDate.parse(brief.endDate)).toInt()+1)
  require(plan.days.mapIndexed { i,d -> d.date==start.plusDays(i.toLong()).toString() }.all { it })
  require(plan.costs.all { (it.amount?.signum() ?: 0)>=0 })
  require(!ItineraryAssessment.assess(brief,plan).hasConflicts) { "Itinerary contains timing or budget data conflicts" }
  val payload=PlanCodec.encode(brief,plan.copy(version=0))
  db.withTransaction {
   require(dao.source(candidateId)==null)
   val old=dao.candidate();if(old?.candidateId==candidateId) { require(old.payload==payload && old.readyAt==readyAt);return@withTransaction }
   val base=dao.base();require(base?.tripId==baseTripId && (base?.version ?: 0)==baseVersion);require(db.workspace().observe().first()!=null);dao.candidate(CandidateEntity(candidateId=candidateId,tripId=base?.tripId,baseVersion=base?.version ?: 0,payload=payload,readyAt=readyAt,expiresAt=Math.addExact(readyAt,86_400_000)))
  }
 }
 override suspend fun save(candidateId: String,discardNewerDraft: Boolean): SaveResult = db.withTransaction {
  dao.source(candidateId)?.let { return@withTransaction SaveResult.Saved(it.tripId,it.version) }
  val c=dao.candidate()?.takeIf { it.candidateId==candidateId } ?: return@withTransaction SaveResult.Expired
  if(System.currentTimeMillis()>=c.expiresAt) { dao.clearCandidate();return@withTransaction SaveResult.Expired }
  val current=c.tripId?.let { dao.currentVersion(it) } ?: 0
  if(current!=c.baseVersion) return@withTransaction SaveResult.Conflict
  val (brief,plan)=PlanCodec.decode(c.payload)
  val draft=db.workspace().observe().first()?.domain()
  if(draft!=null && canonical(draft)!=canonical(brief) && !discardNewerDraft) return@withTransaction SaveResult.Mismatch
  val id=c.tripId ?: UUID.randomUUID().toString();val version=current+1
  dao.revision(RevisionEntity(id,version,c.candidateId,c.payload,c.readyAt,System.currentTimeMillis()))
  if(version==1) dao.summary(SavedTripEntity(id,plan.title))
  if(db.generation().current()?.state!="RECEIVED") db.invalidateGeneration()
  dao.clearCandidate();dao.clearBase();db.workspace().discard()
  SaveResult.Saved(id,version)
 }
 override suspend fun edit(tripId: String,discardWorkspace: Boolean): Boolean = db.withTransaction {
  val r=dao.revision(tripId,0) ?: return@withTransaction false
  if(!discardWorkspace && (db.workspace().observe().first()!=null || dao.candidate()!=null)) return@withTransaction false
  db.invalidateGeneration()
  val b=PlanCodec.decode(r.payload).first
  dao.clearCandidate();db.workspace().write(WorkspaceEntity.from(b.copy(step=1)));dao.base(WorkspaceBase(tripId=tripId,version=r.version));true
 }
}
