package com.example.travelassistant.core.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow

data class TravelPlace(val id: String, val name: String, val country: String, val zone: String)

/** Small offline UI catalog. Production provider/catalog selection remains a release gate. */
object TravelCatalog {
    val places = listOf(
        TravelPlace("danang-vn", "Da Nang", "VN", "Asia/Ho_Chi_Minh"),
        TravelPlace("hanoi-vn", "Hanoi", "VN", "Asia/Ho_Chi_Minh"),
        TravelPlace("hochiminh-vn", "Ho Chi Minh City", "VN", "Asia/Ho_Chi_Minh"),
        TravelPlace("hoian-vn", "Hoi An", "VN", "Asia/Ho_Chi_Minh"),
        TravelPlace("bangkok-th", "Bangkok", "TH", "Asia/Bangkok"),
        TravelPlace("tokyo-jp", "Tokyo", "JP", "Asia/Tokyo"),
        TravelPlace("singapore-sg", "Singapore", "SG", "Asia/Singapore"),
        TravelPlace("paris-fr", "Paris", "FR", "Europe/Paris"),
        TravelPlace("paris-us", "Paris, Texas", "US", "America/Chicago"),
        TravelPlace("london-gb", "London", "GB", "Europe/London")
    )
    val currencies = listOf("USD", "VND", "EUR", "JPY", "SGD", "THB", "GBP")
    fun place(id: String) = places.firstOrNull { it.id == id }
}

data class PlannerDraft(
    val destinationId: String = "", val destinationQuery: String = "",
    val startDate: String = "", val endDate: String = "",
    val adults: String = "1", val children: String = "0", val childBands: String = "",
    val budget: String = "", val currency: String = "USD", val transport: String = "",
    val originId: String = "", val originQuery: String = "", val purpose: String = "",
    val pace: String = "moderate", val interests: String = "", val arrival: String = "",
    val departure: String = "", val notes: String = "", val step: Int = 1,
    val outputLanguage: String = "en"
)

enum class BriefIssue { DESTINATION, DATES, GROUP, CHILD_AGES, BUDGET, CURRENCY, TRANSPORT, ORIGIN, PURPOSE, TIMES, NOTES }

object BriefValidator {
    fun decimal(value: String): BigDecimal? = value.trim().takeIf { it.length <= 30 && it.matches(Regex("[0-9]+([.,][0-9]+)?")) }
        ?.replace(',', '.')?.toBigDecimalOrNull()

    fun validate(d: PlannerDraft, todayAtDestination: LocalDate, throughStep: Int = 3): Set<BriefIssue> = buildSet {
        if (TravelCatalog.place(d.destinationId) == null) add(BriefIssue.DESTINATION)
        val start = runCatching { LocalDate.parse(d.startDate) }.getOrNull()
        val end = runCatching { LocalDate.parse(d.endDate) }.getOrNull()
        if (start == null || end == null || start < todayAtDestination || ChronoUnit.DAYS.between(start, end) !in 0..13) add(BriefIssue.DATES)
        if (throughStep < 2) return@buildSet
        val adults = d.adults.takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()
        val children = d.children.takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()
        if (adults == null || children == null || adults !in 1..8 || children !in 0..7 || adults + children > 8) add(BriefIssue.GROUP)
        if (children != null && children in 1..7) {
            val bands = d.childBands.split(',')
            if (bands.size != children || bands.any { it !in setOf("0_2", "3_11", "12_17") }) add(BriefIssue.CHILD_AGES)
        }
        if (decimal(d.budget)?.let { it > BigDecimal.ZERO } != true) add(BriefIssue.BUDGET)
        if (d.currency !in TravelCatalog.currencies) add(BriefIssue.CURRENCY)
        if (d.transport !in setOf("included", "excluded")) add(BriefIssue.TRANSPORT)
        if (d.transport == "included" && (TravelCatalog.place(d.originId) == null || d.originId == d.destinationId)) add(BriefIssue.ORIGIN)
        if (throughStep < 3) return@buildSet
        if (d.purpose !in setOf("leisure", "business", "family", "other")) add(BriefIssue.PURPOSE)
        fun time(value: String) = value.takeIf { it.matches(Regex("[0-9]{2}:[0-9]{2}")) }
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val arrival = time(d.arrival)
        val departure = time(d.departure)
        if ((d.arrival.isNotBlank() && arrival == null) || (d.departure.isNotBlank() && departure == null)
            || (start == end && arrival != null && departure != null && departure < arrival)) add(BriefIssue.TIMES)
        if (d.notes.length > 1000) add(BriefIssue.NOTES)
    }

    fun today(d: PlannerDraft) = LocalDate.now(ZoneId.of(TravelCatalog.place(d.destinationId)?.zone ?: "UTC"))
}

interface WorkspaceRepository {
    val draft: Flow<PlannerDraft?>
    suspend fun write(draft: PlannerDraft)
    suspend fun discard()
    suspend fun removeAllLocalTrips()
}

data class DisplayPreferences(val currency: String = "USD", val units: String = "celsius")
interface DisplayPreferencesRepository {
    val preferences: Flow<DisplayPreferences>
    suspend fun update(currency: String, units: String)
}

/** Presentation contracts. No provider payload or generated prose is a navigation argument. */
data class PlanActivity(val time: String, val title: String, val area: String, val durationMinutes: Int, val note: String, val sourceUrl: String? = null)
data class PlanDay(val date: String, val title: String, val activities: List<PlanActivity>)
data class PlanCost(val category: String, val amount: BigDecimal?)
data class PlanPresentation(
    val id: String, val title: String, val language: String, val currency: String,
    val requestedBudget: BigDecimal, val group: Int, val version: Int, val days: List<PlanDay>,
    val costs: List<PlanCost>, val assumption: String, val transportIncluded: Boolean, val sourceMode: String = "legacy"
)
data class PreviewScenario(val key: String, val screen: String, val state: String, val errorId: String = "")
interface UiPreviewCatalog {
    val scenarios: List<PreviewScenario>
    fun plan(): PlanPresentation?
    fun draft(): PlannerDraft
}
