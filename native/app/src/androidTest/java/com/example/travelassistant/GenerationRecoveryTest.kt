package com.example.travelassistant

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelassistant.core.data.*
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class GenerationRecoveryTest {
 @org.junit.Before fun safeTestServer(){LocalApiTestGate.requireSimulation()}
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 companion object {private val testPreferences by lazy {PreferenceDataStoreFactory.create(produceFile={java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"recovery-test.preferences_pb")})}}
 private val preferences get()=testPreferences
 private val brief get()=PlannerDraft(destinationId="danang-vn",startDate=LocalDate.now().plusDays(8).toString(),endDate=LocalDate.now().plusDays(8).toString(),budget="100",transport="excluded",purpose="leisure")
 private fun open(name: String)=Room.databaseBuilder(context,LocalDatabase::class.java,name).addMigrations(LocalDatabase.MIGRATION_3_4).build()
 private suspend fun post(body: String): JSONObject=withContext(Dispatchers.IO){val token=preferences.data.first()[stringPreferencesKey("backend_guest_token")]!!;val c=URL("http://127.0.0.1:8787/v1/operations").openConnection() as HttpURLConnection;try{c.requestMethod="POST";c.connectTimeout=5000;c.readTimeout=5000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer $token");c.outputStream.use{it.write(body.toByteArray())};check(c.responseCode==202);JSONObject(c.inputStream.bufferedReader().use{it.readText()})}finally{c.disconnect()}}
 private suspend fun ready(repo: GenerationRepository){repeat(20){repo.refresh();if(repo.state.first().status=="RECEIVED")return;delay(100)};fail("No candidate: status=${repo.state.first().status}, error=${repo.state.first().error}")}
 @Test fun lostAckRecoversSameRequestAfterDatabaseReopen()=runBlocking {
  val name="generation-lost-ack.db";context.deleteDatabase(name);var db=open(name);db.workspace().write(WorkspaceEntity.from(brief))
  var repo=LocalGenerationRepository(db,preferences,RoomPlanStore(db));repo.begin(true);val original=db.generation().current()!!
  val ack=post(original.body);val id=ack.getString("id") // Pretend this ACK never reached the repository.
  db.close();db=open(name);repo=LocalGenerationRepository(db,preferences,RoomPlanStore(db));ready(repo)
  assertEquals(original.requestId,db.generation().current()!!.requestId);assertEquals(id,RoomPlanStore(db).candidate()!!.candidateId)
  assertEquals(id,post(original.body).getString("id"));val expiry=RoomPlanStore(db).candidate()!!.expiresAt
  repo.refresh();assertEquals(expiry,RoomPlanStore(db).candidate()!!.expiresAt)
  db.close();context.deleteDatabase(name);Unit
 }
 @Test fun cancelBeforeAckSuppressesLateSubmission()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,LocalDatabase::class.java).build();val store=RoomPlanStore(db);db.workspace().write(WorkspaceEntity.from(brief));val repo=LocalGenerationRepository(db,preferences,store)
  repo.begin(true);val body=db.generation().current()!!.body;repo.cancel()
  assertEquals("CANCELED",post(body).getString("state"));repo.refresh();assertNull(store.candidate());assertEquals("CANCELED",repo.state.first().status);assertNotNull(db.workspace().observe().first());db.close()
 }
 @Test fun savingPreviousCandidateInvalidatesAnActiveReplacement()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,LocalDatabase::class.java).build();val store=RoomPlanStore(db);db.workspace().write(WorkspaceEntity.from(brief));val repo=LocalGenerationRepository(db,preferences,store)
  repo.begin(true);ready(repo);val old=store.candidate()!!
  repo.begin(true);val replacement=db.generation().current()!!;post(replacement.body)
  val saved=store.save(old.candidateId) as SaveResult.Saved
  repo.refresh();assertEquals("CANCELED",db.generation().current()!!.state);assertNull(store.candidate());assertEquals(old.candidateId,store.revision(saved.tripId)!!.candidateId);assertEquals(1,store.history(saved.tripId).size);db.close()
 }
 @Test fun explicitCancelCanRecoverAnUnavailableSessionWithoutReplayingOldRequest()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,LocalDatabase::class.java).build();val store=RoomPlanStore(db);db.workspace().write(WorkspaceEntity.from(brief));val repo=LocalGenerationRepository(db,preferences,store)
  repo.begin(true);val old=db.generation().current()!!.requestId
  preferences.edit {it[stringPreferencesKey("backend_guest_token")]="0".repeat(64)}
  repo.refresh();assertEquals("AUTH_ERROR",repo.state.first().status)
  repo.cancel();assertEquals("REMOTE_CANCEL_UNCONFIRMED",repo.state.first().error)
  assertNull(preferences.data.first()[stringPreferencesKey("backend_guest_token")])
  repo.begin(true);assertNotEquals(old,db.generation().current()!!.requestId);repo.cancel();assertNull(store.candidate());db.close()
 }
 @Test fun migrationThreeToFourPreservesRevisionDraftAndCandidate()=runBlocking {
  val name="generation-migration.db";context.deleteDatabase(name);var db=open(name);var store=RoomPlanStore(db)
  db.workspace().write(WorkspaceEntity.from(brief));val repo=LocalGenerationRepository(db,preferences,store);repo.begin(true);ready(repo)
  val saved=store.save(store.candidate()!!.candidateId) as SaveResult.Saved
  store.edit(saved.tripId);val next=LocalGenerationRepository(db,preferences,store);next.begin(true);ready(next);val candidate=store.candidate()!!
  db.close()
  android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use{old->old.execSQL("DROP TABLE generation_operation");old.execSQL("DROP TABLE generation_cancel_outbox");old.execSQL("DROP TABLE room_master_table");old.version=3}
  db=open(name);store=RoomPlanStore(db);assertEquals(saved.tripId,store.revision(saved.tripId)!!.tripId);assertEquals(candidate.candidateId,store.candidate()!!.candidateId);assertEquals(candidate.expiresAt,store.candidate()!!.expiresAt);assertNotNull(db.workspace().observe().first());assertNull(db.generation().current());db.close();context.deleteDatabase(name);Unit
 }
}
