package com.example.travelassistant.core.data

import androidx.room.*
import androidx.room.withTransaction
import java.io.ByteArrayOutputStream
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.domain.GenerationRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.security.MessageDigest
import javax.inject.Inject

@Entity(tableName="generation_operation")
data class GenerationEntity(@PrimaryKey val slot: Int=1,val requestId: String,val serverId: String?,val body: String,val ownerHash: String,val state: String,val error: String?,val baseTrip: String?,val baseVersion: Int)
@Entity(tableName="generation_cancel_outbox")
data class CancelEntity(@PrimaryKey val requestId: String,val ownerHash: String)
@Dao interface GenerationDao {
 @Query("SELECT * FROM generation_operation WHERE slot=1") fun observe(): Flow<GenerationEntity?>
 @Query("SELECT * FROM generation_operation WHERE slot=1") suspend fun current(): GenerationEntity?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(value: GenerationEntity)
 @Query("DELETE FROM generation_operation") suspend fun clear()
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun cancel(value: CancelEntity)
 @Query("SELECT * FROM generation_cancel_outbox") suspend fun cancellations(): List<CancelEntity>
 @Query("DELETE FROM generation_cancel_outbox WHERE requestId=:id") suspend fun ackCancel(id: String)
}
suspend fun LocalDatabase.invalidateGeneration() {
 val dao=generation();val op=dao.current() ?: return
 if(op.state !in setOf("RECEIVED","FAILED","CANCELED","EXPIRED")) dao.cancel(CancelEntity(op.requestId,op.ownerHash))
 dao.put(op.copy(state="CANCELED",error="CANCELED",body="{}"))
}
class GenerationFailure(reason: String): GenerationException(reason)
class LocalGenerationRepository @Inject constructor(private val db: LocalDatabase,private val preferences: DataStore<Preferences>,private val plans: PlanStore): GenerationRepository {
 private val lock=Mutex()
 private val canceling=AtomicBoolean(false)
 private val tokenKey=stringPreferencesKey("backend_guest_token")
 override val mockAvailable get()=BuildConfig.DEBUG
 override val state=db.generation().observe().map { e -> if(e==null) GenerationState() else GenerationState(e.requestId,e.serverId,e.state,e.error,runCatching {JSONObject(e.body).optString("mode")=="mock"}.getOrDefault(false),e.baseTrip) }
 private fun digest(value: String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
 private suspend fun token(create: Boolean): String {
  preferences.data.first()[tokenKey]?.let{return it}
  if(!create)throw GenerationFailure("AUTH_RECOVERY_REQUIRED")
  val result=http("POST","/v1/sessions",null,null).getString("token")
  require(result.matches(Regex("[a-f0-9]{64}")))
  preferences.edit{it[tokenKey]=result};return result
 }
 private suspend fun http(method: String,path: String,token: String?,body: String?): JSONObject = withContext(Dispatchers.IO) {
  if(BuildConfig.BACKEND_URL.isBlank())throw GenerationFailure("BACKEND_NOT_CONFIGURED")
  val uri=URI(BuildConfig.BACKEND_URL)
  require(uri.scheme=="https" || (BuildConfig.DEBUG && uri.scheme=="http" && uri.host in setOf("127.0.0.1","localhost","10.0.2.2")))
  val connection=uri.resolve(path).toURL().openConnection() as HttpURLConnection
  try {
   connection.requestMethod=method;connection.connectTimeout=8000;connection.readTimeout=15000;connection.instanceFollowRedirects=false
   connection.setRequestProperty("Accept","application/json");if(token!=null)connection.setRequestProperty("Authorization","Bearer $token")
   if(body!=null){connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json");connection.outputStream.use{it.write(body.toByteArray())}}
   val status=connection.responseCode
   val stream=if(status in 200..299)connection.inputStream else connection.errorStream
   val bytes=stream?.use { input ->
    val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true){val count=input.read(buffer);if(count<0)break;if(output.size()+count>2_000_000)throw GenerationFailure("OUTPUT_TOO_LARGE");output.write(buffer,0,count)}
    output.toByteArray()
   } ?: byteArrayOf();if(bytes.size>2_000_000)throw GenerationFailure("OUTPUT_TOO_LARGE")
   val json=runCatching{JSONObject(String(bytes,Charsets.UTF_8))}.getOrElse{throw GenerationFailure("INVALID_RESPONSE")}
   if(status !in 200..299){val code=json.optString("error");throw GenerationFailure(if(code.matches(Regex("[A-Z0-9_]{1,80}")))code else "HTTP_$status")}
   json
  } finally {connection.disconnect()}
 }
 private fun briefJson(d: PlannerDraft): JSONObject=JSONObject(PlanCodec.encode(d,PlanPresentation("","",d.outputLanguage,d.currency,java.math.BigDecimal.ZERO,1,0,emptyList(),emptyList(),"",false))).getJSONObject("brief")
 override suspend fun begin(mock: Boolean) = lock.withLock {
  if(canceling.get())throw GenerationFailure("REQUEST_BUSY")
  require(!mock || mockAvailable)
  val existing=db.generation().current()
  if(existing!=null && existing.state in setOf("PENDING","UNKNOWN","QUEUED","RUNNING","AUTH_ERROR"))return@withLock
  val guest=token(true)
  db.withTransaction {
   // Cancellation may arrive while session creation/DataStore persistence is suspended.
   // Re-check inside the transaction so cancel-before-request cannot be lost.
   if(canceling.get())throw GenerationFailure("REQUEST_BUSY")
   val d=db.workspace().observe().first()?.domain() ?: throw GenerationFailure("DRAFT_MISSING")
   require(BriefValidator.validate(d,BriefValidator.today(d)).isEmpty())
   val base=db.plans().base();val id=UUID.randomUUID().toString()
   val body=JSONObject().put("requestId",id).put("createdAt",System.currentTimeMillis()).put("brief",briefJson(d)).put("instruction",d.notes).put("mode",if(mock)"mock" else "live")
   val previous=base?.let { plans.revision(it.tripId,it.version) }
   if(previous!=null && previous.plan.sourceMode in setOf("openai","gemini")) {
    val p=previous.plan
    val days=JSONArray();p.days.forEach { day -> val activities=JSONArray();day.activities.forEach { a -> activities.put(JSONObject().put("time",a.time).put("title",a.title).put("area",a.area).put("durationMinutes",a.durationMinutes).put("note",a.note).put("sourceUrl",a.sourceUrl)) };days.put(JSONObject().put("date",day.date).put("title",day.title).put("activities",activities)) }
    body.put("previous",JSONObject().put("brief",briefJson(previous.brief)).put("proposal",JSONObject().put("title",p.title).put("language",p.language).put("days",days).put("assumption",p.assumption)).put("sources",JSONArray(p.days.flatMap { it.activities }.mapNotNull { it.sourceUrl }.distinct())))
   }
   if(body.toString().toByteArray().size>65000)throw GenerationFailure("CONTEXT_TOO_LARGE")
   db.generation().put(GenerationEntity(requestId=id,serverId=null,body=body.toString(),ownerHash=digest(guest),state="PENDING",error=null,baseTrip=base?.tripId,baseVersion=base?.version ?: 0))
  }
 }
 override suspend fun cancel() {
  if(!canceling.compareAndSet(false,true))return
  try {
  db.withTransaction {db.invalidateGeneration()}
  lock.withLock {
   try {flushCancellations()}
   catch(error: GenerationFailure) {
    if(error.reason !in setOf("UNAUTHORIZED","AUTH_RECOVERY_REQUIRED"))throw error
    // Explicit user cancellation abandons this local request. Never replay it under a new owner.
    preferences.edit {it.remove(tokenKey)}
    db.withTransaction {
     db.generation().cancellations().forEach {db.generation().ackCancel(it.requestId)}
     db.generation().current()?.takeIf {it.state=="CANCELED"}?.let {db.generation().put(it.copy(error="REMOTE_CANCEL_UNCONFIRMED"))}
    }
   }
  }
  } finally {canceling.set(false)}
 }
 private suspend fun flushCancellations() {
  val rows=db.generation().cancellations();if(rows.isEmpty())return
  val guest=token(false)
  for(row in rows){if(digest(guest)!=row.ownerHash)throw GenerationFailure("AUTH_RECOVERY_REQUIRED");http("DELETE","/v1/requests/${row.requestId}",guest,null);db.generation().ackCancel(row.requestId)}
 }
 override suspend fun refresh()=lock.withLock {
  val op=db.generation().current() ?: return@withLock
  if(op.state in setOf("RECEIVED","FAILED","CANCELED","EXPIRED","STALE")) {if(op.state=="CANCELED")try {flushCancellations()}catch(c: CancellationException){throw c}catch(_: Exception){};return@withLock}
  try {
   val guest=token(false);if(digest(guest)!=op.ownerHash)throw GenerationFailure("AUTH_RECOVERY_REQUIRED")
   flushCancellations()
   val result=if(op.serverId==null)http("POST","/v1/operations",guest,op.body) else http("GET","/v1/operations/${op.serverId}",guest,null)
   val serverId=result.getString("id");val serverState=result.getString("state")
   if(result.getString("requestId")!=op.requestId || !serverId.matches(Regex("[a-f0-9-]{36}")) || (op.serverId!=null && serverId!=op.serverId))throw GenerationFailure("INVALID_RESPONSE")
   db.withTransaction {
    val current=db.generation().current();if(current?.requestId!=op.requestId||current.state=="CANCELED")return@withTransaction
    when(serverState){
     "READY" -> {
      val request=JSONObject(op.body);val b=decodeBrief(request.getJSONObject("brief"));val readyAt=result.getLong("readyAt")
      if(result.getLong("expiresAt")!=readyAt+86_400_000 || System.currentTimeMillis()>=result.getLong("expiresAt"))throw GenerationFailure("EXPIRED")
      val content=result.getJSONObject("result");val proposal=content.getJSONObject("proposal");val mode=content.getJSONObject("metadata").optString("provider")
      if(mode !in setOf("mock","openai","gemini") || (mode=="mock")!=(request.getString("mode")=="mock"))throw GenerationFailure("INVALID_RESPONSE")
      val days=proposal.getJSONArray("days");val costs=content.getJSONArray("costs")
      if(mode in setOf("openai","gemini")) {
       val rawSources=content.getJSONArray("sources");val sources=(0 until rawSources.length()).map {rawSources.getString(it)}.toSet()
       require(sources.isNotEmpty() && sources.all {runCatching {val u=URI(it);u.scheme=="https" && !u.host.isNullOrBlank()}.getOrDefault(false)})
       for(i in 0 until days.length()){val activities=days.getJSONObject(i).getJSONArray("activities");for(j in 0 until activities.length())require(activities.getJSONObject(j).getString("sourceUrl") in sources)}
      }
      val plan=PlanPresentation(serverId,proposal.getString("title"),proposal.getString("language"),b.currency,BriefValidator.decimal(b.budget)!!,b.adults.toInt()+b.children.toInt(),0,
       (0 until days.length()).map {i->val d=days.getJSONObject(i);val a=d.getJSONArray("activities");PlanDay(d.getString("date"),d.getString("title"),(0 until a.length()).map {j->val x=a.getJSONObject(j);PlanActivity(x.getString("time"),x.getString("title"),x.getString("area"),x.getInt("durationMinutes"),x.getString("note"),x.optString("sourceUrl").takeIf{it.startsWith("https://")})})},
       (0 until costs.length()).map {i->val c=costs.getJSONObject(i);PlanCost(c.getString("category"),if(c.isNull("amount"))null else c.getString("amount").toBigDecimal())},proposal.getString("assumption"),b.transport=="included",mode)
      plans.receive(serverId,b,plan,readyAt,op.baseTrip,op.baseVersion)
      db.generation().put(op.copy(serverId=serverId,state="RECEIVED",error=null))
     }
     "FAILED","CANCELED","EXPIRED" -> db.generation().put(op.copy(serverId=serverId,state=serverState,error=result.optString("error").takeIf{it.matches(Regex("[A-Z0-9_]{1,80}"))} ?: serverState))
     "QUEUED","RUNNING" -> db.generation().put(op.copy(serverId=serverId,state=serverState,error=null))
     else -> throw GenerationFailure("INVALID_RESPONSE")
    }
   }
  }catch(c: CancellationException){throw c}
  catch(error: Exception){
   val reason=(error as? GenerationFailure)?.reason ?: if(error is IllegalArgumentException)"INVALID_OR_STALE_RESULT" else if(error is JSONException)"INVALID_RESPONSE" else "CONNECTION_FAILED"
   db.withTransaction {val current=db.generation().current();if(current?.requestId==op.requestId&&current.state!="CANCELED")db.generation().put(current.copy(state=if(reason in setOf("AUTH_RECOVERY_REQUIRED","UNAUTHORIZED"))"AUTH_ERROR" else if(reason in setOf("INVALID_OR_STALE_RESULT","INVALID_RESPONSE","EXPIRED","INVALID_REQUEST","INVALID_BRIEF","DAILY_QUOTA","MOCK_DISABLED","AI_NOT_CONFIGURED","IDEMPOTENCY_CONFLICT","OUTPUT_TOO_LARGE","NOT_FOUND"))"FAILED" else "UNKNOWN",error=reason))}
  }
 }
 private fun decodeBrief(b: JSONObject): PlannerDraft {
  val wrapper=JSONObject(PlanCodec.encode(PlannerDraft(),PlanPresentation("","","en","USD",java.math.BigDecimal.ZERO,1,0,emptyList(),emptyList(),"",false))).put("brief",b)
  return PlanCodec.decode(wrapper.toString()).first
 }
}
