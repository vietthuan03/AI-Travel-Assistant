package com.example.travelassistant.core.domain

 data class StoredPlan(val candidateId: String, val tripId: String?, val version: Int, val brief: PlannerDraft, val plan: PlanPresentation, val readyAt: Long, val expiresAt: Long)
 sealed interface SaveResult {
 data class Saved(val tripId: String,val version: Int): SaveResult
 data object Expired: SaveResult
 data object Conflict: SaveResult
 data object Mismatch: SaveResult
 }
 interface PlanStore {
 suspend fun committed(candidateId: String): StoredPlan?
 suspend fun candidate(): StoredPlan?
 suspend fun revision(tripId: String,version: Int=0): StoredPlan?
 suspend fun history(tripId: String): List<StoredPlan>
 suspend fun receive(candidateId: String,brief: PlannerDraft,plan: PlanPresentation,readyAt: Long,baseTripId: String?=null,baseVersion: Int=0)
 suspend fun save(candidateId: String,discardNewerDraft: Boolean=false): SaveResult
 suspend fun edit(tripId: String,discardWorkspace: Boolean=false): Boolean
 }
