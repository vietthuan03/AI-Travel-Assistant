package com.example.ai_travel_assistant.feature.explore

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.example.ai_travel_assistant.domain.trip.TripRequest
import com.example.ai_travel_assistant.domain.trip.formatUtc
import java.text.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

private const val UnselectedChildAge = -1
private const val SkyscannerMaxAdults = 8
private const val SkyscannerMaxMinors = 8
private const val BookingComMaxAdults = 30
private const val BookingComMaxChildren = 10
private const val BookingComMaxRooms = 30

internal enum class BookingType(val label: Int) {
    Flight(R.string.explore_flights), Stay(R.string.explore_stays)
}

internal data class ItineraryBooking(val type: BookingType, val trip: TripRequest)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExploreScreen(
    cities: List<String>,
    today: Long,
    canPlan: Boolean,
    itineraryBooking: ItineraryBooking?,
    onItineraryBookingClosed: () -> Unit,
    onPlan: (String) -> Unit,
) {
    var cityIndex by rememberSaveable { mutableIntStateOf(-1) }
    val city = itineraryBooking?.trip?.destination ?: cities.getOrNull(cityIndex).orEmpty()
    var booking by rememberSaveable { mutableStateOf<BookingType?>(null) }
    val forms = rememberSaveableStateHolder()
    var choosing by rememberSaveable { mutableStateOf(false) }
    var pendingBooking by rememberSaveable { mutableStateOf<BookingType?>(null) }
    ExploreDestination(
        cities = cities,
        city = city,
        choosing = choosing,
        onChoose = { choosing = true },
        onDismiss = {
            choosing = false
            pendingBooking = null
        },
        onSelect = { selected ->
            cityIndex = cities.indexOf(selected)
            choosing = false
            booking = pendingBooking
            pendingBooking = null
        },
    ) {
        if (city.isNotEmpty()) {
            Button(onClick = { onPlan(city) }, enabled = canPlan, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_trip_ai), null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.explore_plan))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookingType.entries.forEach { type ->
                OutlinedCard(onClick = {
                    if (city.isEmpty()) {
                        pendingBooking = type
                        choosing = true
                    } else {
                        booking = type
                    }
                }, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(if (type == BookingType.Flight) R.drawable.ic_explore_flight
                            else R.drawable.ic_explore_stay), null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(type.label), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    val activeBooking = itineraryBooking?.type ?: booking
    if (activeBooking != null) {
        val closeBooking = {
            booking = null
            if (itineraryBooking != null) {
                val tripDestinationIndex = cities.indexOf(city)
                if (tripDestinationIndex >= 0) cityIndex = tripDestinationIndex
                onItineraryBookingClosed()
            }
        }
        ModalBottomSheet(onDismissRequest = closeBooking,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(activeBooking.label), Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = closeBooking) { Text(stringResource(R.string.history_close)) }
                }
                if (itineraryBooking != null) {
                    BookingForm(activeBooking, city, today, itineraryBooking.trip)
                } else {
                    forms.SaveableStateProvider("${activeBooking.name}/$cityIndex") {
                        BookingForm(activeBooking, city, today)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingForm(type: BookingType, city: String, today: Long, trip: TripRequest? = null) {
    val context = LocalContext.current
    val flight = type == BookingType.Flight
    val resources = LocalResources.current
    val arrivalAirport = airportCities.entries.firstOrNull { resources.getString(it.value) == city }?.key.orEmpty()
    val departureAirport = airportCities.entries.firstOrNull {
        resources.getString(it.value) == trip?.origin
    }?.key.orEmpty().takeUnless { it == arrivalAirport }.orEmpty()
    var from by rememberSaveable { mutableStateOf(departureAirport) }
    var to by key(city) { rememberSaveable { mutableStateOf(arrivalAirport) } }
    var roundTrip by rememberSaveable { mutableStateOf(flight && trip?.returnDate != null) }
    var chosenStart by rememberSaveable { mutableLongStateOf(trip?.departure ?: today) }
    val start = maxOf(today, chosenStart)
    val earliestEnd = if (flight) start else start + 86_400_000L
    var chosenEnd by rememberSaveable { mutableLongStateOf(trip?.returnDate ?: earliestEnd) }
    val end = maxOf(earliestEnd, chosenEnd)
    var adultInput by rememberSaveable { mutableStateOf("1") }
    var roomInput by rememberSaveable { mutableStateOf("1") }
    var savedChildAges by rememberSaveable { mutableStateOf(intArrayOf()) }
    var cabin by rememberSaveable { mutableStateOf(CabinClass.Economy) }
    val maxAdults = if (flight) SkyscannerMaxAdults else BookingComMaxAdults
    val adults = (adultInput.toIntOrNull() ?: 1).coerceIn(1, maxAdults)
    val maxRooms = minOf(adults, BookingComMaxRooms)
    val rooms = (roomInput.toIntOrNull() ?: 1).coerceIn(1, maxRooms)
    val maxChildren = if (flight) SkyscannerMaxMinors else BookingComMaxChildren
    val childAges = if (savedChildAges.size <= maxChildren) savedChildAges else savedChildAges.copyOf(maxChildren)
    SideEffect {
        if (adultInput != adults.toString()) adultInput = adults.toString()
        if (!flight && roomInput != rooms.toString()) roomInput = rooms.toString()
        if (savedChildAges !== childAges) savedChildAges = childAges
    }
    val supportedChildAges = 0..17
    val childrenReady = childAges.all { it in supportedChildAges }

    var openFailed by remember { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf<BookingSection?>(null) }
    fun toggle(section: BookingSection) { editor = if (editor == section) null else section }
    val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val startLabel = dates.formatUtc(start)
    val dateSummary = if (flight && !roundTrip) stringResource(R.string.explore_summary, startLabel,
        stringResource(R.string.explore_oneway)) else stringResource(R.string.trip_route, startLabel, dates.formatUtc(end))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (flight) {
            SummaryButton(R.string.explore_route, stringResource(R.string.trip_route,
                from.ifEmpty { stringResource(R.string.explore_from) },
                to.ifEmpty { stringResource(R.string.explore_to) })) { toggle(BookingSection.Route) }
        }
        if (flight && editor == BookingSection.Route) {
            AirportField(R.string.explore_to, to, airportCities.keys) { code ->
                to = code
                if (from == code) from = ""
            }
            AirportField(R.string.explore_from, from.takeUnless { it == to }.orEmpty(), airportCities.keys - to) { from = it }
        }
        SummaryButton(if (flight) R.string.explore_depart else R.string.explore_arrive, dateSummary) {
            toggle(BookingSection.Dates)
        }
        if (editor == BookingSection.Dates) {
            if (flight) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!roundTrip, { roundTrip = false }, { Text(stringResource(R.string.explore_oneway)) })
                FilterChip(roundTrip, { roundTrip = true }, { Text(stringResource(R.string.explore_round)) })
            }
            BookingDate(if (flight) R.string.explore_depart else R.string.explore_arrive, start, today) {
                chosenStart = it
            }
            if (!flight || roundTrip) {
                BookingDate(if (flight) R.string.explore_return else R.string.explore_leave, end, earliestEnd) {
                    chosenEnd = it
                }
            }
        }
        val adultSummary = pluralStringResource(R.plurals.explore_adult_count, adults, adults)
        val guestSummary = listOfNotNull(
            adultSummary,
            pluralStringResource(R.plurals.explore_child_count, childAges.size, childAges.size),
            if (flight) stringResource(cabin.label) else pluralStringResource(R.plurals.explore_room_count, rooms, rooms),
        ).joinToString(stringResource(R.string.explore_count_separator))
        SummaryButton(if (flight) R.string.explore_cabin_guests else R.string.explore_guests, guestSummary) { toggle(BookingSection.Guests) }
        if (editor == BookingSection.Guests) {
            if (flight) {
                val cabinOptions = CabinClass.entries.associateBy { stringResource(it.label) }
                SuggestedTripField(
                    value = stringResource(cabin.label),
                    onValueChange = { cabin = cabinOptions.getValue(it) },
                    label = stringResource(R.string.explore_cabin),
                    placeholder = "",
                    suggestions = cabinOptions.keys.toList(),
                    enabled = true,
                )
            }
            CountStepper(
                R.string.explore_adults,
                adults,
                minimum = if (flight) 1 else rooms,
                maximum = maxAdults,
                hint = if (flight) stringResource(R.string.explore_adults_age_range) else null,
            ) { adultInput = it.toString() }
            CountStepper(
                R.string.explore_children, childAges.size, minimum = 0, maximum = maxChildren,
                hint = if (flight) stringResource(R.string.explore_children_age_range) else null,
            ) { count ->
                savedChildAges = if (count > childAges.size) {
                    childAges + UnselectedChildAge
                } else {
                    childAges.copyOf(count)
                }
            }
            if (flight) {
                if (adults == maxAdults || childAges.size == maxChildren) {
                    BookingLimitNote(R.string.explore_skyscanner_limit, SkyscannerMaxAdults, SkyscannerMaxMinors)
                }
            } else {
                CountStepper(R.string.explore_rooms, rooms, minimum = 1, maximum = maxRooms) {
                    roomInput = it.toString()
                }
                if (adults == maxAdults || childAges.size == maxChildren || rooms == BookingComMaxRooms) {
                    BookingLimitNote(
                        R.string.explore_booking_com_limit, BookingComMaxAdults, BookingComMaxChildren, BookingComMaxRooms,
                    )
                }
                if (rooms == adults && rooms < BookingComMaxRooms) BookingLimitNote(R.string.explore_room_limit)
            }
            val ageOptions = supportedChildAges.map { it.toString() }
            childAges.forEachIndexed { index, age ->
                SuggestedTripField(
                    value = if (age in supportedChildAges) age.toString() else "",
                    onValueChange = { selected ->
                        savedChildAges = childAges.copyOf().apply { this[index] = selected.toInt() }
                    },
                    label = stringResource(R.string.explore_child_age, index + 1),
                    placeholder = stringResource(R.string.explore_choose_age),
                    suggestions = ageOptions,
                    enabled = true,
                )
            }
        }
        if (!childrenReady) {
            Text(stringResource(R.string.explore_choose_child_ages),
                style = MaterialTheme.typography.bodySmall)
        }
        Button(
            enabled = childrenReady && (!flight || (from.isNotEmpty() && to.isNotEmpty() && from != to)),
            onClick = {
                val url = if (flight) {
                    flightSearchUrl(from, to, start, end.takeIf { roundTrip }, adults, childAges.toList(), cabin)
                } else {
                    staySearchUrl(city, start, end, adults, rooms, childAges.toList())
                }
                try {
                    openFailed = false
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: ActivityNotFoundException) {
                    openFailed = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (flight) R.string.explore_sky else R.string.explore_booking)) }
        if (openFailed) {
            Text(stringResource(R.string.explore_open_failed), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
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
private fun BookingLimitNote(labelId: Int, vararg arguments: Any) {
    Text(
        stringResource(labelId, *arguments),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CountStepper(labelId: Int, count: Int, minimum: Int, maximum: Int, hint: String? = null, onChange: (Int) -> Unit) {
    val label = stringResource(labelId)
    val decrease = stringResource(R.string.explore_decrease_count, label)
    val increase = stringResource(R.string.explore_increase_count, label)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedIconButton(onClick = { onChange(count - 1) }, enabled = count > minimum,
            modifier = Modifier.semantics { contentDescription = decrease }) {
            Text("−")
        }
        Text(count.toString(), Modifier.padding(horizontal = 8.dp))
        OutlinedIconButton(onClick = { onChange(count + 1) }, enabled = count < maximum,
            modifier = Modifier.semantics { contentDescription = increase }) {
            Text("+")
        }
    }
}

private enum class BookingSection { Route, Dates, Guests }

@Composable
private fun SummaryButton(label: Int, value: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
