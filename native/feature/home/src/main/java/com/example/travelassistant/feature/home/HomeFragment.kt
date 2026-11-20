package com.example.travelassistant.feature.home

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.travelassistant.core.domain.ExploreCatalog
import com.example.travelassistant.core.domain.UiPreviewCatalog
import com.example.travelassistant.core.domain.TravelCatalog
import com.example.travelassistant.core.ui.*
import com.example.travelassistant.core.ui.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import com.example.travelassistant.core.ui.databinding.DialogTextInputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

internal class ExploreSearchViewModel(private val state: SavedStateHandle) : ViewModel() {
    var query: String
        get() = state["query"] ?: ""
        set(value) { state["query"] = value.trim().take(60) }
}

@AndroidEntryPoint
class HomeFragment : MvpListFragment() {
    @Inject lateinit var catalog: UiPreviewCatalog
    @Inject lateinit var plans: com.example.travelassistant.core.domain.PlanStore
    @Inject lateinit var savedRepository: com.example.travelassistant.core.domain.TripRepository
    private var savedRows=emptyList<com.example.travelassistant.core.domain.SavedTripSummary>()
    private var savedReadFailed=false
    private var hasCandidate=false
    private val workspace: WorkspaceViewModel by activityViewModels()
    private val generation: GenerationViewModel by activityViewModels()
    private var pending=false
    private val search by viewModels<ExploreSearchViewModel>()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view,savedInstanceState)
        if (redirectUiEntry(R.id.home_fragment, "H01")) return
        render(workspace.state.value)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(workspace.state,generation.state){state,op -> pending=op.status in setOf("PENDING","UNKNOWN","QUEUED","RUNNING","AUTH_ERROR");state}.collect { state -> hasCandidate=try {plans.candidate()!=null} catch(cancel: CancellationException){throw cancel} catch(_: Exception){false};readSaved();render(state) }
            }
        }
    }
    private suspend fun readSaved() {
        try {savedRows=savedRepository.savedTrips.first();savedReadFailed=false}
        catch(cancel: CancellationException){throw cancel}
        catch(_: Exception){savedReadFailed=true}
    }
    private fun render(state: WorkspaceUiState) {
        val sample=preview(catalog)
        val list=mutableListOf<UiSection>()
        if(sample!=null) list+=sampleBanner()
        else {
            list += UiSection(
                "explore", getString(R.string.explore_tab),
                primary = action(R.string.explore_search) { showSearch() },
                secondary = action(R.string.booking_find_flights) { navigateUi(R.id.home_fragment, R.id.flight_graph, topLevel = true) },
                tertiary = action(R.string.booking_find_stays) { navigateUi(R.id.home_fragment, R.id.stay_graph, topLevel = true) },
            )
        }
        if (sample == null && search.query.isNotEmpty()) list += destinations()
        list+=UiSection("plan",getString(if (sample == null) R.string.explore_prepare_trip else R.string.home_hero_title),
            if (sample == null) "" else getString(R.string.plan_intro),artwork=TravelArtwork.Hero.takeIf { sample!=null },
            primary=action(R.string.plan_trip, state.loaded && !state.readFailed, R.id.plan_trip_action) {
                if(sample!=null) sampleAction() else openPlanner(workspace,R.id.home_fragment)
            })
        if(sample!=null && sample.state in setOf("draft","pending","candidate")) {
            list+=UiSection("workspace",previewTitle(sample),getString(R.string.preview_banner),primary=action(R.string.resume_draft) {
                val key=if(sample.state=="pending") "P04:normal" else if(sample.state=="candidate") "I01:candidate" else "P01:normal"
                navigateUi(R.id.home_fragment,if(sample.state=="pending") R.id.progress_fragment else if(sample.state=="candidate") R.id.itinerary_fragment else R.id.where_fragment,previewArguments(key))
            })
        } else if(sample==null && state.readFailed) {
            list+=UiSection("read_error",getString(R.string.draft_read_failed),primary=action(R.string.retry) { workspace.retryRead() })
        } else if(sample==null && state.draft!=null) {
            val draft=requireNotNull(state.draft)
            val place=TravelCatalog.place(draft.destinationId)?.let { placeLabel(it) } ?: getString(R.string.destination_label)
            list+=UiSection("workspace",getString(R.string.draft_title),place+"\n"+getString(if(state.storage==DraftStorage.FAILED) R.string.draft_failed else if(state.storage==DraftStorage.WRITING) R.string.draft_saving else R.string.draft_kept),
                primary=action(R.string.resume_draft,viewId=R.id.resume_draft_action) { if(pending) navigateUi(R.id.home_fragment,R.id.progress_fragment) else if(hasCandidate) navigateUi(R.id.home_fragment,R.id.itinerary_fragment) else openPlanner(workspace,R.id.home_fragment,true) },
                secondary=if(state.storage==DraftStorage.FAILED) action(R.string.retry) { workspace.flushSoon() } else null)
        }
        if (sample == null && search.query.isEmpty()) list += destinations()
        if(sample?.state=="saved") list+=UiSection("recent",catalog.plan()?.title.orEmpty(),getString(R.string.saved_local),primary=action(R.string.open_plan) {
            navigateUi(R.id.home_fragment,R.id.itinerary_fragment,previewArguments("I01:saved"))
        }) else if(sample==null && savedReadFailed) list+=UiSection("saved_error",getString(R.string.data_read_error),primary=action(R.string.retry){viewLifecycleOwner.lifecycleScope.launch {readSaved();render(workspace.state.value)}})
        else if(sample==null && savedRows.isNotEmpty()) list+=UiSection("saved",savedRows.first().title,getString(R.string.saved_local),primary=action(R.string.open_plan){navigateUi(R.id.home_fragment,R.id.itinerary_fragment,Bundle().apply {putString("tripId",savedRows.first().id)})},secondary=action(R.string.trips_title, viewId=R.id.trips_shortcut){navigateUi(R.id.home_fragment,R.id.trips_graph,topLevel=true)})
        else list+=UiSection("saved",getString(R.string.trips_title),getString(R.string.home_empty),secondary=action(R.string.trips_title, viewId=R.id.trips_shortcut) {
            navigateUi(R.id.home_fragment,R.id.trips_graph,topLevel=true)
        })
        if (sample == null) list += UiSection("tools", getString(R.string.essential_tools),
            primary = action(R.string.tools_title, viewId = R.id.tools_shortcut) {
                navigateUi(R.id.home_fragment, R.id.tools_graph)
            })
        else list+=UiSection("tools",getString(R.string.essential_tools),
            primary=action(R.string.currency_title,viewId=R.id.currency_action,icon=R.drawable.ic_tool_currency) { navigateUi(R.id.home_fragment,R.id.currency_fragment) },
            secondary=action(R.string.time_title,viewId=R.id.time_action,icon=R.drawable.ic_tool_time) { navigateUi(R.id.home_fragment,R.id.time_fragment) },
            tertiary=action(R.string.weather_title,viewId=R.id.weather_action,icon=R.drawable.ic_tool_weather) { navigateUi(R.id.home_fragment,R.id.weather_fragment) })
        rows(list)
    }

    private fun showSearch() {
        val input = DialogTextInputBinding.inflate(layoutInflater)
        input.textLayout.setHint(R.string.explore_search_hint)
        input.textInput.setText(search.query)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.explore_search)
            .setView(input.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.explore_search_apply) { _, _ ->
                search.query = input.textInput.text.toString()
                render(workspace.state.value)
            }.create()
        input.textInput.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }

    private fun destinations(): List<UiSection> {
        val query = search.query.normalized()
        val matches = ExploreCatalog.destinations.filter { destination ->
            query.isEmpty() || listOf(
                destinationTitle(destination), TravelCatalog.place(destination.placeId)?.name.orEmpty(),
                destination.airport, getString(destination.theme.title),
            ).any { query in it.normalized() }
        }
        return buildList {
            if (search.query.isNotEmpty()) {
                add(UiSection("search_filter", getString(R.string.explore_results, search.query),
                    primary = action(R.string.explore_clear_search) {
                        search.query = ""
                        render(workspace.state.value)
                    }))
            }
            if (matches.isEmpty()) add(UiSection("no_match", getString(R.string.explore_no_match)))
            matches.forEach { destination ->
                add(UiSection("destination_${destination.placeId}", destinationTitle(destination),
                    getString(destination.guide().summary), badge = getString(destination.theme.title),
                    primary = action(R.string.explore_details) {
                        navigateUi(R.id.home_fragment, R.id.destination_fragment, bundleOf("destinationId" to destination.placeId))
                    }))
            }
        }
    }
}

private fun String.normalized(): String =
    Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('đ', 'd')
