package com.example.ai_travel_assistant.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai_travel_assistant.R
import com.example.ai_travel_assistant.domain.trip.TripSummary
import com.example.ai_travel_assistant.ui.format.asDisplayDate
import com.example.ai_travel_assistant.ui.format.formatTripBudget
import com.example.ai_travel_assistant.ui.itinerary.ItineraryCard

@Composable
internal fun TripHistoryDialog(model: HistoryViewModel, close: () -> Unit) {
    val state by model.uiState.collectAsState()
    val selectedId by model.selectedId.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(model) { model.start() }
    val deleting = (state as? HistoryState.Detail)?.deleting == true
    val back = {
        if (!deleting) {
            if (selectedId != null) model.load(null) else {
                model.close()
                close()
            }
        }
    }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = back, enabled = !deleting) {
                        Text(stringResource(if (selectedId == null) R.string.history_close else R.string.history_back))
                    }
                }
                when (val current = state) {
                    HistoryState.Loading -> CircularProgressIndicator()
                    HistoryState.ReadFailed -> Text(stringResource(R.string.history_read_failed))
                    is HistoryState.ListReady -> HistoryList(current.trips, model::load)
                    is HistoryState.Detail -> Column(Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (current.deleteFailed) Text(stringResource(R.string.history_delete_failed),
                            color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { confirmDelete = true }, enabled = !current.deleting) {
                            Text(stringResource(R.string.history_delete))
                        }
                        val saved = current.trip
                        ItineraryCard(saved.itinerary, saved.summary.route(), Modifier,
                            saved.summary.details(), saved.summary.destination)
                    }
                }
            }
        }
    }
    if (confirmDelete && state is HistoryState.Detail) {
        AlertDialog(onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.history_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.deleteSelected()
                }) { Text(stringResource(R.string.history_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryList(trips: List<TripSummary>, open: (String) -> Unit) {
    when {
        trips.isEmpty() -> Text(stringResource(R.string.history_empty))
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(trips, key = { it.id }) { trip ->
                OutlinedCard(onClick = { open(trip.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(trip.route(), style = MaterialTheme.typography.titleMedium)
                        Text(trip.details(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TripSummary.route() = stringResource(R.string.trip_route, origin, destination)

@Composable
private fun TripSummary.details(): String {
    val locale = LocalConfiguration.current.locales[0]
    val dates = listOf(departure, returnDate).distinct().joinToString(" – ") { it.asDisplayDate(locale) }
    return "$dates · $purpose · ${formatTripBudget(budgetAmount.toString())} $budgetCurrency"
}
