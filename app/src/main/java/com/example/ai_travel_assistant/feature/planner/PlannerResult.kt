package com.example.ai_travel_assistant.feature.planner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ai_travel_assistant.R
import com.example.ai_travel_assistant.domain.trip.Itinerary
import com.example.ai_travel_assistant.domain.trip.PlanFailure
import com.example.ai_travel_assistant.feature.explore.BookingType
import com.example.ai_travel_assistant.ui.itinerary.ItineraryCard
import com.example.ai_travel_assistant.ui.itinerary.PlanStatusHeader

@Composable
internal fun PlannerResult(
    state: PlannerUiState,
    route: String,
    details: String,
    destination: String,
    itineraryRequester: BringIntoViewRequester,
    onBook: (BookingType) -> Unit,
) {
    if (state == PlannerUiState.Initial) return

    val slideDistance = with(LocalDensity.current) { 16.dp.roundToPx() }
    AnimatedContent(
        targetState = ResultPresentation(state, route, details, destination),
        contentKey = { it.state },
        transitionSpec = {
            val enter = fadeIn() + slideInVertically { slideDistance }
            (enter togetherWith fadeOut()).using(null)
        },
        label = "Itinerary status",
    ) { result ->
        PlanResultContent(result, itineraryRequester, onBook)
    }
}

private data class ResultPresentation(val state: PlannerUiState, val route: String, val details: String, val destination: String)

@Composable
private fun PlanResultContent(
    result: ResultPresentation,
    itineraryRequester: BringIntoViewRequester,
    onBook: (BookingType) -> Unit,
) {
    val state = result.state
    val resultHeading = remember { BringIntoViewRequester() }
    val heading = if (state is PlannerUiState.Success) itineraryRequester else resultHeading
    val status = Modifier.bringIntoViewRequester(heading).semantics {
        liveRegion = LiveRegionMode.Polite
    }

    LaunchedEffect(state) {
        if (state != PlannerUiState.Initial) {
            withFrameNanos {}
            heading.bringIntoView()
        }
    }

    when (state) {
        PlannerUiState.Initial -> Unit
        PlannerUiState.Loading -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            PlanStatusHeader(
                title = stringResource(R.string.loading_plan),
                artwork = R.drawable.travel_state_generating,
                modifier = status,
                loading = true,
            )
        }
        is PlannerUiState.Error -> Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            PlanStatusHeader(
                title = stringResource(R.string.failed_plan),
                body = stringResource(
                    when (state.failure) {
                        PlanFailure.Network -> R.string.plan_error_network
                        PlanFailure.Timeout -> R.string.plan_error_timeout
                        PlanFailure.Quota -> R.string.plan_error_quota
                        PlanFailure.TokenLimit -> R.string.plan_error_token_limit
                        PlanFailure.Configuration, PlanFailure.ModelUnavailable -> R.string.plan_error_configuration
                        PlanFailure.Blocked -> R.string.plan_error_blocked
                        PlanFailure.EmptyResponse -> R.string.plan_error_empty
                        PlanFailure.Unknown -> R.string.retry_hint
                    }
                ),
                artwork = R.drawable.travel_state_error,
                modifier = status,
            )
        }
        is PlannerUiState.Success -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.saveFailed) {
                Surface(modifier = status.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.history_save_failed), Modifier.padding(16.dp))
                }
            }
            ItineraryCard(state.itinerary, result.route, if (state.saveFailed) Modifier else status,
                result.details, result.destination)
            BookingType.entries.forEach { type ->
                OutlinedButton(onClick = { onBook(type) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(type.label))
                }
            }
        }
    }
}
