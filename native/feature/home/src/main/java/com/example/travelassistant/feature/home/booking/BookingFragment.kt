package com.example.travelassistant.feature.home.booking

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.travelassistant.core.domain.BookingIssue
import com.example.travelassistant.core.domain.BookingKind
import com.example.travelassistant.core.domain.BookingLinkRepository
import com.example.travelassistant.core.domain.BookingSearch
import com.example.travelassistant.core.domain.ExploreCatalog
import com.example.travelassistant.core.domain.ExploreDestination
import com.example.travelassistant.core.domain.TravelCatalog
import com.example.travelassistant.core.ui.MvpListFragment
import com.example.travelassistant.core.ui.R
import com.example.travelassistant.core.ui.UiAction
import com.example.travelassistant.core.ui.UiSection
import com.example.travelassistant.core.ui.destinationTitle
import com.example.travelassistant.core.ui.openExternalLink
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.launch

internal data class BookingInputs(
    val destinationId: String,
    val originId: String = "",
    val roundTrip: Boolean = true,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val adults: Int = 1,
    val rooms: Int = 1,
) : Serializable {
    fun search(kind: BookingKind) = BookingSearch(
        kind = kind,
        destination = ExploreCatalog.destination(destinationId),
        start = start,
        end = end,
        adults = adults,
        origin = ExploreCatalog.destination(originId),
        roundTrip = roundTrip,
        rooms = rooms,
    )
}

internal class BookingViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    val inputs = savedState.getStateFlow("bookingInputs", BookingInputs(savedState["destinationId"] ?: ""))
    val issue = savedState.getStateFlow<BookingIssue?>("bookingIssue", null)

    fun edit(update: (BookingInputs) -> BookingInputs) {
        savedState["bookingIssue"] = null
        savedState["bookingInputs"] = update(inputs.value)
    }

    fun validate(search: BookingSearch, today: LocalDate): Boolean {
        val next = search.issue(today)
        savedState["bookingIssue"] = next
        return next == null
    }
}

@AndroidEntryPoint
class BookingFragment : MvpListFragment() {
    @Inject lateinit var links: BookingLinkRepository
    private val model by viewModels<BookingViewModel>()
    private val kind get() = if (arguments?.getBoolean("stay") == true) BookingKind.STAY else BookingKind.FLIGHT
    private val isFlight get() = kind == BookingKind.FLIGHT
    private val publicSiteName get() = if (isFlight) "Skyscanner" else "Booking.com"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        for (end in listOf(false, true)) {
            @Suppress("UNCHECKED_CAST")
            val restoredPicker = childFragmentManager.findFragmentByTag(dateTag(end)) as? MaterialDatePicker<Long>
            restoredPicker?.let { bindPicker(it, end) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.inputs.collect { render() } }
                launch { model.issue.collect { render() } }
            }
        }
    }

    private fun render() {
        val search = model.inputs.value.search(kind)
        val startLabel = if (isFlight) R.string.booking_depart else R.string.booking_check_in
        val endLabel = if (isFlight) R.string.booking_return else R.string.booking_check_out
        val sections = buildList {
            links.publicSiteWithoutTemplate(kind)?.let { url ->
                add(UiSection("public_search", getString(R.string.booking_provider), getString(R.string.booking_manual_intro),
                    primary = UiAction(getString(R.string.booking_open_site, publicSiteName)) { openExternalLink(url) }))
            }
            if (isFlight) {
                add(UiSection("trip_type", getString(R.string.booking_flights), choices = listOf(
                    action(R.string.booking_round_trip) { model.edit { it.copy(roundTrip = true) } },
                    action(R.string.booking_one_way) { model.edit { it.copy(roundTrip = false) } },
                ), selectedChoice = if (search.roundTrip) 0 else 1))
                add(placeSection("origin", R.string.booking_origin, search.origin) { origin ->
                    model.edit { it.copy(originId = origin.placeId) }
                })
            }
            add(placeSection("destination", R.string.booking_destination, search.destination) { destination ->
                model.edit { it.copy(destinationId = destination.placeId) }
            })
            add(UiSection("dates", getString(R.string.booking_dates),
                primary = dateAction(startLabel, search.start, end = false),
                secondary = if (search.needsEndDate) dateAction(endLabel, search.end, end = true) else null))
            add(UiSection("party", getString(R.string.booking_party),
                primary = UiAction(getString(R.string.booking_adults_count, search.adults)) {
                    pickCount(R.string.booking_pick_adults, search.adults, 8) { count -> model.edit { it.copy(adults = count) } }
                },
                secondary = if (isFlight) null else UiAction(getString(R.string.booking_rooms_count, search.rooms)) {
                    pickCount(R.string.booking_pick_rooms, search.rooms, search.adults) { count -> model.edit { it.copy(rooms = count) } }
                }))
            add(UiSection("provider", getString(R.string.booking_provider),
                model.issue.value?.let { getString(it.message) } ?: getString(R.string.booking_provider_body),
                primary = action(R.string.booking_continue) { continueToProvider() }))
        }
        rows(sections)
    }

    private fun placeSection(
        key: String,
        label: Int,
        value: ExploreDestination?,
        update: (ExploreDestination) -> Unit,
    ) = UiSection(key, getString(label), value?.let(::placeName).orEmpty(),
        primary = action(if (value == null) R.string.booking_select_place else R.string.booking_change_place) {
            val options = ExploreCatalog.destinations
            choose(label, options.map(::placeName), options.indexOf(value)) { update(options[it]) }
        })

    private fun placeName(destination: ExploreDestination): String {
        val title = destinationTitle(destination)
        return if (isFlight) getString(R.string.booking_airport, title, destination.airport) else title
    }

    private fun pickCount(title: Int, selected: Int, max: Int, update: (Int) -> Unit) {
        val numbers = java.text.NumberFormat.getIntegerInstance(resources.configuration.locales[0])
        choose(title, (1..max).map { numbers.format(it) }, selected - 1) { update(it + 1) }
    }

    private fun choose(title: Int, values: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(values.toTypedArray(), selected.takeIf { it in values.indices } ?: -1) { dialog, index ->
                onSelect(index)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun dateAction(label: Int, date: LocalDate?, end: Boolean) =
        action(label) { showDatePicker(label, end) }.let {
            if (date == null) it else it.copy(label = getString(R.string.booking_selected_date, getString(label), displayDate(date)))
        }

    private fun displayDate(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(resources.configuration.locales[0]).format(date)

    private fun today(search: BookingSearch): LocalDate {
        val place = if (isFlight) search.origin else search.destination
        val zone = place?.let { TravelCatalog.place(it.placeId)?.zone }
        return LocalDate.now(zone?.let(ZoneId::of) ?: ZoneId.systemDefault())
    }

    private fun showDatePicker(label: Int, end: Boolean) {
        val tag = dateTag(end)
        if (childFragmentManager.isStateSaved || childFragmentManager.findFragmentByTag(tag) != null) return
        val search = model.inputs.value.search(kind)
        val today = today(search)
        val selectedStart = search.start
        val earliest = if (end && selectedStart != null) {
            maxOf(today, if (isFlight) selectedStart else selectedStart.plusDays(1))
        } else today
        val millis = earliest.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val old = if (end) search.end else search.start
        val builder = MaterialDatePicker.Builder.datePicker()
            .setTitleText(label)
            .setCalendarConstraints(CalendarConstraints.Builder()
                .setStart(millis)
                .setValidator(DateValidatorPointForward.from(millis))
                .build())
        old?.takeUnless { it.isBefore(earliest) }?.let {
            builder.setSelection(it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        }
        val picker = builder.build()
        bindPicker(picker, end)
        picker.showNow(childFragmentManager, tag)
    }

    private fun bindPicker(picker: MaterialDatePicker<Long>, end: Boolean) {
        picker.clearOnPositiveButtonClickListeners()
        picker.addOnPositiveButtonClickListener { timestamp ->
            val date = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate()
            model.edit { if (end) it.copy(end = date) else it.copy(start = date) }
        }
    }

    private fun dateTag(end: Boolean) = if (end) "booking_end_date" else "booking_start_date"

    private fun continueToProvider() {
        val search = model.inputs.value.search(kind)
        if (!model.validate(search, today(search))) return
        val link = links.link(search)
        if (link.carriesSearch) {
            openExternalLink(link.url)
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.booking_open_site, publicSiteName))
                .setMessage(R.string.booking_manual_search)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.booking_open) { _, _ -> openExternalLink(link.url) }
                .show()
        }
    }
}

private val BookingIssue.message: Int
    get() = when (this) {
        BookingIssue.ORIGIN -> R.string.booking_error_origin
        BookingIssue.DESTINATION -> R.string.booking_error_destination
        BookingIssue.SAME_AIRPORT -> R.string.booking_error_same_airport
        BookingIssue.START -> R.string.booking_error_start
        BookingIssue.END -> R.string.booking_error_end
        BookingIssue.PARTY -> R.string.booking_error_party
    }
