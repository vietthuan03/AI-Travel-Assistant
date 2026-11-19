package com.example.ai_travel_assistant.data.ai

import android.os.SystemClock
import android.util.Log
import com.example.ai_travel_assistant.domain.trip.Itinerary
import com.example.ai_travel_assistant.domain.trip.PlanFailure
import com.example.ai_travel_assistant.domain.trip.TripGenerator
import com.example.ai_travel_assistant.domain.trip.TripRequest
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class FirebaseTripGenerator : TripGenerator {
    private val fallback = TravelModelFallback(SystemClock::elapsedRealtime, reportFailure = ::reportFailure)

    override suspend fun generate(request: TripRequest, language: String): Itinerary {
        val prompt = request.prompt(language)
        val text = fallback.generate { model ->
            Firebase.ai.generativeModel(modelName = model).generateContent(prompt).text
        }
        return readItinerary(text)
    }

    private fun reportFailure(model: String, failure: PlanFailure, error: Exception?) {
        val finishReason = (error as? ResponseStoppedException)
            ?.response?.candidates?.firstOrNull()?.finishReason?.name
        // Log only diagnostic metadata. SDK messages can contain full server responses.
        Log.w("TravelAI", "model=$model failure=$failure exception=${error?.javaClass?.simpleName} finish=$finishReason")
    }

}

internal fun Throwable.toPlanFailure(): PlanFailure {
    return generateSequence(this) { it.cause?.takeUnless { cause -> cause === it } }
        .take(10)
        .firstNotNullOfOrNull { cause ->
            when (cause) {
                is QuotaExceededException -> PlanFailure.Quota
                is RequestTimeoutException, is SocketTimeoutException -> PlanFailure.Timeout
                is UnknownHostException, is ConnectException -> PlanFailure.Network
                is APINotConfiguredException, is InvalidAPIKeyException, is ServiceDisabledException,
                is PermissionMissingException, is UnsupportedUserLocationException -> PlanFailure.Configuration
                is PromptBlockedException, is ContentBlockedException -> PlanFailure.Blocked
                is ResponseStoppedException -> when (cause.response.candidates.firstOrNull()?.finishReason) {
                    FinishReason.MAX_TOKENS -> PlanFailure.TokenLimit
                    FinishReason.SAFETY, FinishReason.RECITATION -> PlanFailure.Blocked
                    else -> PlanFailure.Unknown
                }
                is ServerException -> {
                    val message = cause.message.orEmpty().lowercase()
                    when {
                        "resource exhausted" in message || "rate limit" in message -> PlanFailure.Quota
                        "app check" in message -> PlanFailure.Configuration
                        "model" in message && (
                            "not found" in message || "not supported for generatecontent" in message ||
                                "does not have access to" in message || "do not have access to" in message
                        ) -> PlanFailure.ModelUnavailable
                        else -> null
                    }
                }
                else -> null
            }
        } ?: PlanFailure.Unknown
}
