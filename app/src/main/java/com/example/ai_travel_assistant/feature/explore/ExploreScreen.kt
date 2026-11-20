package com.example.ai_travel_assistant.feature.explore

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ai_travel_assistant.R
import com.example.ai_travel_assistant.feature.planner.DepartureDateDialog
import com.example.ai_travel_assistant.feature.planner.SuggestedTripField
import com.example.ai_travel_assistant.feature.planner.TripDateButton

private val airportCities = mapOf(
    "HAN" to R.string.city_hanoi, "SGN" to R.string.city_hcmc, "DAD" to R.string.city_danang,
    "DLI" to R.string.city_dalat, "CXR" to R.string.city_nhatrang, "BKK" to R.string.city_bangkok,
    "SIN" to R.string.city_singapore, "HND" to R.string.city_tokyo, "ICN" to R.string.city_seoul,
    "AMD" to R.string.city_ahmedabad, "GOI" to R.string.city_goa,
)

private enum class BookingType(val label: Int) {
    Flight(R.string.explore_flights), Stay(R.string.explore_stays)
}

@Composable
internal fun ExploreScreen(cities: List<String>, today: Long, canPlan: Boolean, onPlan: (String) -> Unit) {
    var cityIndex by rememberSaveable { mutableIntStateOf(-1) }
    val city = cities.getOrNull(cityIndex).orEmpty()
    var booking by rememberSaveable { mutableStateOf<BookingType?>(null) }
    val forms = rememberSaveableStateHolder()
    Text(stringResource(R.string.tab_explore), style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() })
    SuggestedTripField(city, { cityIndex = cities.indexOf(it) }, stringResource(R.string.explore_destination),
        stringResource(R.string.destination_example), cities, enabled = true)
    if (city.isEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cities.take(3).forEachIndexed { index, suggestion ->
                SuggestionChip(onClick = { cityIndex = index }, label = { Text(suggestion) })
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onPlan(city) }, enabled = canPlan, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.explore_plan))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookingType.entries.forEach { type ->
                FilterChip(selected = booking == type, onClick = { booking = type },
                    label = { Text(stringResource(type.label)) })
            }
        }
    }
    booking?.let { type ->
        forms.SaveableStateProvider(type) { BookingForm(type, city, today) }
    }
}

@Composable
private fun BookingForm(type: BookingType, city: String, today: Long) {
    val context = LocalContext.current
    val flight = type == BookingType.Flight
    var from by rememberSaveable { mutableStateOf("") }
    val resources = LocalResources.current
    var to by key(city) {
        rememberSaveable {
            mutableStateOf(airportCities.entries.firstOrNull { resources.getString(it.value) == city }?.key.orEmpty())
        }
    }
    var roundTrip by rememberSaveable { mutableStateOf(false) }
    var chosenStart by rememberSaveable { mutableLongStateOf(today) }
    val start = maxOf(today, chosenStart)
    val earliestEnd = if (flight) start else start + 86_400_000L
    var chosenEnd by rememberSaveable { mutableLongStateOf(earliestEnd) }
    val end = maxOf(earliestEnd, chosenEnd)
    var adultInput by rememberSaveable { mutableStateOf("1") }
    var roomInput by rememberSaveable { mutableStateOf("1") }
    val adults = adultInput.toIntOrNull() ?: 0
    val rooms = roomInput.toIntOrNull() ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (flight) {
            AirportField(R.string.explore_to, to, airportCities.keys) { code ->
                to = code
                if (from == code) from = ""
            }
            AirportField(R.string.explore_from, from.takeUnless { it == to }.orEmpty(), airportCities.keys - to) { from = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!roundTrip, { roundTrip = false }, { Text(stringResource(R.string.explore_oneway)) })
                FilterChip(roundTrip, { roundTrip = true }, { Text(stringResource(R.string.explore_round)) })
            }
        }
        BookingDate(if (flight) R.string.explore_depart else R.string.explore_arrive, start, today) {
            chosenStart = it
        }
        if (!flight || roundTrip) {
            BookingDate(if (flight) R.string.explore_return else R.string.explore_leave, end, earliestEnd) {
                chosenEnd = it
            }
        }
        CountField(R.string.explore_adults, adultInput) { adultInput = it }
        if (!flight) CountField(R.string.explore_rooms, roomInput) { roomInput = it }
        Button(
            enabled = adults > 0 && (if (flight) from.isNotEmpty() && to.isNotEmpty() && from != to else rooms > 0),
            onClick = {
                val url = if (flight) flightSearchUrl(from, to, start, end.takeIf { roundTrip }, adults)
                    else staySearchUrl(city, start, end, adults, rooms)
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.explore_open_failed, Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (flight) R.string.explore_sky else R.string.explore_booking)) }
    }
}

@Composable
private fun AirportField(label: Int, code: String, options: Set<String>, onSelect: (String) -> Unit) {
    val labels = options.associateWith { airport ->
        stringResource(R.string.explore_airport_option, stringResource(airportCities.getValue(airport)), airport)
    }
    SuggestedTripField(labels[code].orEmpty(), { selected ->
        onSelect(labels.entries.firstOrNull { it.value == selected }?.key.orEmpty())
    },
        stringResource(label), stringResource(R.string.explore_airport_hint), labels.values.toList(), enabled = true)
}

@Composable
private fun BookingDate(labelId: Int, value: Long, earliest: Long, onChange: (Long) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(labelId)
    TripDateButton(label, value, enabled = true, modifier = Modifier.fillMaxWidth()) { open = true }
    if (open) DepartureDateDialog(label, value, earliest, { open = false }) {
        onChange(it)
        open = false
    }
}

@Composable
private fun CountField(label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = {
        if (it.isEmpty() || it.all(Char::isDigit) && it.toIntOrNull() != null) onChange(it)
    }, label = { Text(stringResource(label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = value.isNotEmpty() && value.toIntOrNull() == 0)
}
