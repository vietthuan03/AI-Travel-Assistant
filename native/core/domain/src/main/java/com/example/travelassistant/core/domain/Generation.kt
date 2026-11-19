package com.example.travelassistant.core.domain
import kotlinx.coroutines.flow.Flow

data class GenerationState(val requestId: String="",val operationId: String?=null,val status: String="NONE",val error: String?=null,val mock: Boolean=false,val baseTripId: String?=null)
open class GenerationException(val reason: String): Exception()

interface GenerationRepository {
 val state: Flow<GenerationState>
 val mockAvailable: Boolean
 suspend fun begin(mock: Boolean=false)
 suspend fun refresh()
 suspend fun cancel()
}
