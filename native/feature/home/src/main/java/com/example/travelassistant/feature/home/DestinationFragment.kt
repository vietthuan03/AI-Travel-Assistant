package com.example.travelassistant.feature.home

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.travelassistant.core.domain.DestinationTheme
import com.example.travelassistant.core.domain.ExploreCatalog
import com.example.travelassistant.core.domain.ExploreDestination
import com.example.travelassistant.core.domain.TravelCatalog
import com.example.travelassistant.core.ui.MvpListFragment
import com.example.travelassistant.core.ui.R
import com.example.travelassistant.core.ui.SavedDestinationsUiState
import com.example.travelassistant.core.ui.SavedDestinationsViewModel
import com.example.travelassistant.core.ui.UiSection
import com.example.travelassistant.core.ui.WorkspaceViewModel
import com.example.travelassistant.core.ui.destinationTitle
import com.example.travelassistant.core.ui.navigateUi
import com.example.travelassistant.core.ui.openExternalLink
import com.example.travelassistant.core.ui.openPlanner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DestinationFragment : MvpListFragment() {
    private val saved: SavedDestinationsViewModel by activityViewModels()
    private val workspace: WorkspaceViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val destination = ExploreCatalog.destination(arguments?.getString("destinationId"))
        if (destination == null) {
            rows(listOf(UiSection("missing", getString(R.string.explore_no_match))))
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(saved.state, workspace.state) { bookmarks, draft ->
                    sections(destination, bookmarks, draft.loaded && !draft.readFailed)
                }.collect(::rows)
            }
        }
    }

    private fun sections(destination: ExploreDestination, state: SavedDestinationsUiState, canPlan: Boolean): List<UiSection> {
        val isSaved = destination.placeId in state.ids.orEmpty()
        val source = R.id.destination_fragment
        val args = bundleOf("destinationId" to destination.placeId)
        val guide = destination.guide()
        return buildList {
            add(UiSection("destination", destinationTitle(destination), getString(guide.summary),
                badge = getString(destination.theme.title),
                primary = action(if (isSaved) R.string.explore_unsave else R.string.explore_save,
                    state.ids != null && !state.writing && !state.readFailed) { saved.toggle(destination) }))
            if (state.readFailed) {
                add(UiSection("save_error", getString(R.string.data_read_error),
                    primary = action(R.string.retry) { saved.retry() }))
            } else if (state.writeFailed) {
                add(UiSection("save_error", getString(R.string.explore_save_error)))
            }
            add(UiSection("where_to_stay", getString(R.string.explore_where_to_stay), getString(guide.whereToStay),
                secondary = action(R.string.explore_tourism_source) { openExternalLink(destination.tourismUrl) }))
            add(UiSection("travel", getString(R.string.explore_prepare_trip),
                primary = action(R.string.booking_find_flights) { navigateUi(source, R.id.flight_graph, args) },
                secondary = action(R.string.booking_find_stays) { navigateUi(source, R.id.stay_graph, args) },
                tertiary = action(R.string.plan_trip, canPlan) {
                    openPlanner(workspace, source, destination = TravelCatalog.place(destination.placeId))
                }))
        }
    }
}

internal val DestinationTheme.title: Int
    get() = when (this) {
        DestinationTheme.CULTURE -> R.string.explore_theme_culture
        DestinationTheme.COAST -> R.string.explore_theme_coast
        DestinationTheme.CITY -> R.string.explore_theme_city
    }

internal data class DestinationGuide(val summary: Int, val whereToStay: Int)

internal fun ExploreDestination.guide(): DestinationGuide = when (placeId) {
    "danang-vn" -> DestinationGuide(R.string.explore_danang_summary, R.string.explore_danang_stay)
    "hanoi-vn" -> DestinationGuide(R.string.explore_hanoi_summary, R.string.explore_hanoi_stay)
    "hochiminh-vn" -> DestinationGuide(R.string.explore_hochiminh_summary, R.string.explore_hochiminh_stay)
    else -> error("Missing guide for $placeId")
}
