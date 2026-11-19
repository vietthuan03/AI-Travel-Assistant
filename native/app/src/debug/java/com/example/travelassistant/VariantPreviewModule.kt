package com.example.travelassistant

import com.example.travelassistant.core.domain.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.math.BigDecimal
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object VariantPreviewModule {
    @Provides @Singleton fun previews(): UiPreviewCatalog = VariantPreviewCatalog()
}
/** Debug samples never enter Room, a paid generation request or a release build. */
private class VariantPreviewCatalog : UiPreviewCatalog {
    override val scenarios = listOf(
        PreviewScenario("H01:normal", "H01", "normal", ""),
        PreviewScenario("H01:draft", "H01", "draft", ""),
        PreviewScenario("H01:pending", "H01", "pending", ""),
        PreviewScenario("H01:candidate", "H01", "candidate", ""),
        PreviewScenario("H01:saved", "H01", "saved", ""),
        PreviewScenario("P01:normal", "P01", "normal", ""),
        PreviewScenario("P01:invalid", "P01", "invalid", "E02"),
        PreviewScenario("P01:ambiguous", "P01", "ambiguous", "E01"),
        PreviewScenario("P01:draftfail", "P01", "draftfail", "E11"),
        PreviewScenario("P02:normal", "P02", "normal", ""),
        PreviewScenario("P02:invalid", "P02", "invalid", "E03"),
        PreviewScenario("P03:normal", "P03", "normal", ""),
        PreviewScenario("P03:offline", "P03", "offline", "E04"),
        PreviewScenario("P03:quota", "P03", "quota", "E09"),
        PreviewScenario("P04:normal", "P04", "normal", ""),
        PreviewScenario("P04:ack", "P04", "ack", "E05"),
        PreviewScenario("P04:failed", "P04", "failed", "E07"),
        PreviewScenario("P04:canceled", "P04", "canceled", "E10"),
        PreviewScenario("P04:recovery", "P04", "recovery", "E06"),
        PreviewScenario("P04:invalidai", "P04", "invalidai", "E08"),
        PreviewScenario("P04:refused", "P04", "refused", ""),
        PreviewScenario("P04:timeout", "P04", "timeout", ""),
        PreviewScenario("P04:restart", "P04", "restart", ""),
        PreviewScenario("P04:late", "P04", "late", "E26"),
        PreviewScenario("I01:normal", "I01", "normal", ""),
        PreviewScenario("I01:candidate", "I01", "candidate", ""),
        PreviewScenario("I01:saved", "I01", "saved", ""),
        PreviewScenario("I01:changes", "I01", "changes", ""),
        PreviewScenario("I01:historical", "I01", "historical", "E25"),
        PreviewScenario("I01:savefail", "I01", "savefail", "E12"),
        PreviewScenario("I01:expired", "I01", "expired", "E13"),
        PreviewScenario("I01:conflict", "I01", "conflict", "E14"),
        PreviewScenario("I01:writefail", "I01", "writefail", "E28"),
        PreviewScenario("I01:mismatch", "I01", "mismatch", "E23"),
        PreviewScenario("I02:normal", "I02", "normal", ""),
        PreviewScenario("I02:unknown", "I02", "unknown", "E15"),
        PreviewScenario("I02:overrun", "I02", "overrun", ""),
        PreviewScenario("M01:normal", "M01", "normal", ""),
        PreviewScenario("M01:empty", "M01", "empty", ""),
        PreviewScenario("M01:saved", "M01", "saved", ""),
        PreviewScenario("M01:undo", "M01", "undo", "E20"),
        PreviewScenario("M01:cleanupfail", "M01", "cleanupfail", ""),
        PreviewScenario("M02:normal", "M02", "normal", ""),
        PreviewScenario("T01:normal", "T01", "normal", ""),
        PreviewScenario("T02:normal", "T02", "normal", ""),
        PreviewScenario("T02:stale", "T02", "stale", ""),
        PreviewScenario("T02:unsupported", "T02", "unsupported", "E16"),
        PreviewScenario("T02:offline", "T02", "offline", ""),
        PreviewScenario("T03:normal", "T03", "normal", ""),
        PreviewScenario("T04:normal", "T04", "normal", ""),
        PreviewScenario("T04:denied", "T04", "denied", "E17"),
        PreviewScenario("T04:stale", "T04", "stale", ""),
        PreviewScenario("T04:horizon", "T04", "horizon", "E18"),
        PreviewScenario("T04:offline", "T04", "offline", ""),
        PreviewScenario("T04:providerfail", "T04", "providerfail", ""),
        PreviewScenario("T04:permanent", "T04", "permanent", ""),
        PreviewScenario("T04:loading", "T04", "loading", ""),
        PreviewScenario("S01:normal", "S01", "normal", ""),
        PreviewScenario("S01:languagefail", "S01", "languagefail", "E19"),
        PreviewScenario("S01:fallback", "S01", "fallback", "E22")
    )
    override fun draft() = PlannerDraft(destinationId="danang-vn", destinationQuery="Da Nang", startDate="2026-12-12", endDate="2026-12-14", adults="2", budget="1000", currency="USD", transport="excluded", purpose="leisure", interests="food,culture", outputLanguage="en")
    override fun plan() = PlanPresentation(
        id="ui-sample-danang", title="Da Nang", language="en", currency="USD", requestedBudget=BigDecimal("1000"), group=2, version=2,
        days=(0..2).map { i -> PlanDay("2026-12-${12+i}", "Explore at a comfortable pace", listOf(
            PlanActivity("09:00", "Walk by the river", "City center", 60, "Sample activity. Allow rest and local travel time."),
            PlanActivity("11:00", "Lunch and a rest break", "Near your accommodation", 90, "Sample activity. Opening hours and availability are unverified."),
            PlanActivity("15:00", "Explore a neighborhood", "Central area", 90, "Sample activity. Confirm accessibility and local conditions before travel.")
        )) },
        costs=listOf(PlanCost("stay",BigDecimal("200")), PlanCost("food",BigDecimal("120")), PlanCost("local",BigDecimal("60")), PlanCost("activities",BigDecimal("80"))),
        assumption="Illustrative plan, not AI output. Travel-day times are unknown; allow arrival/departure buffers. No live prices, weather or visa information is included.",
        transportIncluded=false
    )
}
