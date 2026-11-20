package com.example.ai_travel_assistant.feature.planner

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.example.ai_travel_assistant.feature.explore.BookingType
import com.example.ai_travel_assistant.feature.explore.ExploreScreen
import com.example.ai_travel_assistant.feature.explore.ItineraryBooking
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ai_travel_assistant.R
import com.example.ai_travel_assistant.domain.trip.TripRequest
import com.example.ai_travel_assistant.ui.format.TripBudgetVisualTransformation
import com.example.ai_travel_assistant.ui.format.asDisplayDate
import com.example.ai_travel_assistant.ui.format.formatTripBudget
import com.example.ai_travel_assistant.ui.format.normalizeTripBudget
import com.example.ai_travel_assistant.ui.theme.AiTravelAssistantTheme


private val GenerateButtonMinHeight = 56.dp

@Composable
internal fun PlannerForm(
    state: PlannerUiState,
    generate: (TripRequest, String) -> Unit,
    onExit: () -> Unit,
    openAiSettings: (() -> Unit)? = null,
    openHistory: () -> Unit,
) {
    var origin by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    val today = rememberTodayForPicker()
    var chosenDeparture by rememberSaveable { mutableStateOf<Long?>(null) }
    val departure = maxOf(today, chosenDeparture ?: today)
    var returnDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var purpose by rememberSaveable { mutableStateOf("") }
    var budget by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("VND") }
    var submittedDetails by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var pace by rememberSaveable { mutableIntStateOf(0) }
    var submittedOrigin by rememberSaveable { mutableStateOf("") }
    var submittedDestination by rememberSaveable { mutableStateOf("") }
    val styles = listOf(stringResource(R.string.relaxed), stringResource(R.string.active))
    val cities = stringArrayResource(R.array.trip_city_suggestions).asList()
    val purposes = stringArrayResource(R.array.trip_purpose_suggestions).asList()
    val locale = LocalConfiguration.current.locales[0]
    val language = locale.toLanguageTag()
    val amount = budget.toLongOrNull()?.takeIf { it > 0 }
    val invalidBudget = budget.isNotEmpty() && amount == null
    val dateRange = listOfNotNull(departure, returnDate)
        .distinct().joinToString(" – ") { it.asDisplayDate(locale) }
    val duration = departure.let { first ->
        returnDate?.takeIf { it >= first }?.let { last ->
            val days = ((last - first) / 86_400_000).toInt() + 1
            pluralStringResource(R.plurals.trip_duration_days, days, days)
        }
    }
    val budgetLabel = amount?.let {
        stringResource(R.string.trip_budget_summary, formatTripBudget(it.toString()), currency)
    }
    val loading = state is PlannerUiState.Loading
    val request = TripRequest(origin, destination, departure, returnDate, purpose, styles[pace], notes, amount, currency)
    val canGenerate = !loading && request.hasRequiredDetails &&
        origin in cities && destination in cities && purpose in purposes
    val submittedRoute = if (submittedOrigin.isBlank()) {
        submittedDestination
    } else {
        stringResource(R.string.trip_route, submittedOrigin, submittedDestination)
    }

    val focusManager = LocalFocusManager.current
    var tab by rememberSaveable { mutableStateOf(HomeTab.Planner) }
    val tabState = rememberSaveableStateHolder()
    var requestedBooking by rememberSaveable { mutableStateOf<BookingType?>(null) }
    var scrollTarget by remember { mutableStateOf<BringIntoViewRequester?>(null) }
    val tripDetailsRequester = remember { BringIntoViewRequester() }
    val itineraryRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(scrollTarget, tab) {
        if (tab == HomeTab.Planner) {
            scrollTarget?.bringIntoView()
            scrollTarget = null
        }
    }

    PlannerScaffold(
        tab = tab,
        selectTab = { tab = it },
        onExit = onExit,
        openAiSettings = openAiSettings,
        historyEnabled = !loading,
        openHistory = openHistory,
        showTripDetails = {
            focusManager.clearFocus()
            tab = HomeTab.Planner
            scrollTarget = tripDetailsRequester
        },
        showLatestItinerary = if (state is PlannerUiState.Success) {
            {
                focusManager.clearFocus()
                tab = HomeTab.Planner
                scrollTarget = itineraryRequester
            }
        } else null,
    ) {
        if (tab == HomeTab.Explore) {
            tabState.SaveableStateProvider(HomeTab.Explore) {
                val itineraryBooking = if (state is PlannerUiState.Success) {
                    requestedBooking?.let { ItineraryBooking(it, state.request) }
                } else null
                ExploreScreen(
                    cities = cities,
                    today = today,
                    canPlan = !loading,
                    itineraryBooking = itineraryBooking,
                    onItineraryBookingClosed = { requestedBooking = null },
                ) { selected ->
                    destination = selected
                    tab = HomeTab.Planner
                    scrollTarget = tripDetailsRequester
                }
            }
            return@PlannerScaffold
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SuggestedTripField(
                modifier = Modifier.bringIntoViewRequester(tripDetailsRequester),
                value = origin,
                onValueChange = { origin = it },
                label = stringResource(R.string.trip_origin),
                placeholder = stringResource(R.string.origin_example),
                allowClear = true,
                suggestions = cities,
                enabled = !loading,
            )
            SuggestedTripField(
                value = destination,
                onValueChange = { destination = it },
                label = stringResource(R.string.destination),
                placeholder = stringResource(R.string.destination_example),
                suggestions = cities,
                enabled = !loading,
            )
            TripDates(
                today = today,
                departure = departure,
                returnDate = returnDate,
                onDepartureChange = { chosenDeparture = it },
                onReturnChange = { returnDate = it },
                invalidDates = request.hasInvalidDates,
                enabled = !loading,
            )
            SuggestedTripField(
                value = purpose,
                onValueChange = { purpose = it },
                label = stringResource(R.string.trip_purpose),
                placeholder = stringResource(R.string.purpose_example),
                allowClear = true,
                suggestions = purposes,
                enabled = !loading,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.pace),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    styles.forEachIndexed { index, label ->
                        FilterChip(
                            selected = pace == index,
                            onClick = { pace = index },
                            enabled = !loading,
                            label = { Text(label) },
                        )
                    }
                }
            }
            TripBudget(
                value = budget,
                onValueChange = { input ->
                    normalizeTripBudget(input)?.let { budget = it }
                },
                currency = currency,
                onCurrencyChange = { currency = it },
                invalidBudget = invalidBudget,
                enabled = !loading,
            )
            TripNotes(
                value = notes,
                onValueChange = { notes = it },
                enabled = !loading,
            )
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                submittedOrigin = origin.trim()
                submittedDestination = destination.trim()
                submittedDetails = listOfNotNull(dateRange, duration, purpose, budgetLabel)
                    .filter(String::isNotBlank).joinToString(" · ")
                generate(request, language)
            },
            enabled = canGenerate,
            modifier = Modifier.fillMaxWidth().heightIn(min = GenerateButtonMinHeight),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = stringResource(if (loading) R.string.loading_plan else R.string.generate),
                style = MaterialTheme.typography.titleSmall,
            )
        }

        PlannerResult(state, submittedRoute, submittedDetails, submittedDestination, itineraryRequester) { type ->
            if (state is PlannerUiState.Success) {
                focusManager.clearFocus()
                requestedBooking = type
                tab = HomeTab.Explore
            }
        }
    }
}

@Composable
private fun TripBudget(
    value: String,
    onValueChange: (String) -> Unit,
    currency: String,
    onCurrencyChange: (String) -> Unit,
    invalidBudget: Boolean,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.trip_budget), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1.4f),
                label = { Text(stringResource(R.string.trip_budget_amount)) },
                enabled = enabled,
                isError = invalidBudget,
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = tripFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = TripBudgetVisualTransformation,
            )
            SuggestedTripField(
                value = currency,
                onValueChange = onCurrencyChange,
                label = stringResource(R.string.trip_budget_currency),
                placeholder = "",
                suggestions = listOf("VND", "USD", "EUR", "THB", "SGD", "JPY", "KRW", "INR"),
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
        if (invalidBudget) {
            Text(stringResource(R.string.trip_budget_error), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TripNotes(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.preferences)) },
        placeholder = { Text(stringResource(R.string.preferences_example)) },
        shape = MaterialTheme.shapes.small,
        colors = tripFieldColors(),
        minLines = 2,
        maxLines = 5,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

@Preview(name = "Compact · Vietnamese", widthDp = 320, heightDp = 640, locale = "vi", fontScale = 1.3f)
@Preview(name = "Phone · English", widthDp = 390, heightDp = 844)
@Preview(
    name = "Tablet · Dark",
    widthDp = 800,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TravelPreview() {
    AiTravelAssistantTheme { PlannerForm(PlannerUiState.Initial, { _, _ -> }, {}) {} }
}
