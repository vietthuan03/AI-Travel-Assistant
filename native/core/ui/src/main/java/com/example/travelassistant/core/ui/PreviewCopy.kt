package com.example.travelassistant.core.ui

import androidx.fragment.app.Fragment
import com.example.travelassistant.core.domain.PreviewScenario

fun Fragment.previewTitle(s: PreviewScenario): String = getString(when (s.state) {
    "draft" -> R.string.draft_title
    "pending" -> R.string.state_pending
    "candidate" -> R.string.not_saved
    "saved" -> R.string.saved_local
    "changes" -> R.string.unsaved_changes
    "historical" -> R.string.historical
    "invalid" -> if (s.screen == "P01") R.string.error_dates else R.string.error_group
    "ambiguous" -> R.string.error_destination
    "draftfail", "writefail" -> R.string.draft_failed
    "offline" -> if (s.screen.startsWith("P")) R.string.state_offline else if (s.screen == "T02") R.string.rate_unavailable else R.string.weather_unavailable
    "quota" -> R.string.state_quota
    "ack" -> R.string.state_ack
    "failed", "timeout", "restart" -> R.string.state_failed
    "canceled" -> R.string.state_canceled
    "recovery" -> R.string.state_recovery
    "invalidai", "refused" -> R.string.state_invalidai
    "late" -> R.string.state_late
    "savefail" -> R.string.save_failed
    "expired" -> R.string.expired_title
    "conflict" -> R.string.conflict_title
    "mismatch" -> R.string.mismatch_title
    "unknown" -> R.string.incomplete_total
    "overrun" -> R.string.over_budget
    "empty" -> R.string.trips_empty
    "undo" -> R.string.trip_deleted
    "cleanupfail" -> R.string.cleanup_failed
    "stale" -> if (s.screen == "T02") R.string.rate_stale else R.string.weather_stale
    "unsupported" -> R.string.unsupported_pair
    "denied" -> R.string.permission_denied
    "permanent" -> R.string.permission_permanent
    "horizon" -> R.string.weather_horizon
    "providerfail" -> R.string.weather_failure
    "loading" -> R.string.weather_loading
    "languagefail" -> R.string.language_save_error
    "fallback" -> R.string.english
    else -> R.string.state_default
})

fun screenTitle(screen: String): Int = when(screen) {
    "H01" -> R.string.home_title; "P01" -> R.string.where_title
    "P02" -> R.string.travelers_title; "P03" -> R.string.review_title
    "P04" -> R.string.progress_title; "I01" -> R.string.itinerary_title
    "I02" -> R.string.budget_title; "M01" -> R.string.trips_title
    "M02" -> R.string.history_title; "T01" -> R.string.tools_title
    "T02" -> R.string.currency_title; "T03" -> R.string.time_title
    "T04" -> R.string.weather_title; else -> R.string.settings_title
}
