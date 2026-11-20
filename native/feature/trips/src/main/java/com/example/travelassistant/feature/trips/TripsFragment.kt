package com.example.travelassistant.feature.trips

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.example.travelassistant.core.domain.ExploreCatalog
import com.example.travelassistant.core.domain.UiPreviewCatalog
import com.example.travelassistant.core.ui.*
import com.example.travelassistant.core.ui.R
import com.example.travelassistant.core.ui.databinding.DialogTextInputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

class TripsPreviewViewModel(private val handle: SavedStateHandle) : ViewModel() {
    val name=handle.getStateFlow("sampleName","")
    val deleted=handle.getStateFlow("sampleDeleted",false)
    val until=handle.getStateFlow("sampleUndoUntil",0L)
    fun rename(value: String) { handle["sampleName"]=value }
    fun delete(now: Long=System.currentTimeMillis()) { handle["sampleDeleted"]=true;handle["sampleUndoUntil"]=now+5_000 }
    fun undo() { if(System.currentTimeMillis()<until.value) { handle["sampleDeleted"]=false;handle["sampleUndoUntil"]=0L } }
    fun seedUndo(anchor: Long) { if(handle.get<Boolean>("seeded")!=true) { handle["seeded"]=true;delete(anchor) } }
}

@AndroidEntryPoint
class TripsFragment : MvpListFragment() {
    @Inject lateinit var catalog: UiPreviewCatalog
    private val trips: TripsViewModel by viewModels()
    private val model: TripsPreviewViewModel by viewModels()
    private val destinations: SavedDestinationsViewModel by activityViewModels()
    private val workspace: WorkspaceViewModel by activityViewModels()
    override fun onViewCreated(view: View,state: Bundle?) {
        super.onViewCreated(view,state)
        if(redirectUiEntry(R.id.trips_fragment,"M01")) return
        val sample=preview(catalog)
        if(sample?.state=="undo") model.seedUndo((arguments?.getLong("previewExpiry") ?: 86_400_000L)-86_400_000L)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val ticks=flow { while(true) { emit(System.currentTimeMillis());delay(250) } }
                launch {
                    combine(trips.state,trips.savedTrips,workspace.state,model.name,model.deleted,combine(model.until,ticks) { until,now -> until>now }.distinctUntilChanged()) { values -> values }
                        .collect { render() }
                }
                if (sample == null) launch { destinations.state.collect { render() } }
            }
        }
    }
    private fun render() {
        val sample=preview(catalog)
        val list=mutableListOf<UiSection>()
        if(sample!=null) list+=sampleBanner()
        list+=UiSection("local",getString(R.string.trips_title),getString(R.string.local_only))
        if(sample==null && trips.state.value==TripsReadState.LOADING) list+=UiSection("loading",getString(R.string.trips_loading),progress=true)
        else if(sample==null && trips.state.value==TripsReadState.FAILED) list+=UiSection("error",getString(R.string.data_read_error),artwork=TravelArtwork.Error,primary=action(R.string.retry) { trips.retry() })
        else if(sample==null && trips.savedTrips.value.isNotEmpty()) {
            trips.savedTrips.value.forEach { trip -> list+=UiSection("trip_${trip.id}",trip.title,getString(R.string.saved_local),primary=action(R.string.open_plan) {
                navigateUi(R.id.trips_fragment,R.id.itinerary_fragment,Bundle().apply { putString("tripId",trip.id) })
            }) }
        } else if(sample?.state in setOf("saved","undo") && !model.deleted.value) {
            val plan=requireNotNull(catalog.plan())
            list+=UiSection("sample_trip",model.name.value.ifBlank { plan.title },plan.days.first().date+" → "+plan.days.last().date,badge=getString(R.string.version_label,plan.version),
                primary=action(R.string.open_plan) { navigateUi(R.id.trips_fragment,R.id.itinerary_fragment,previewArguments("I01:saved")) },
                secondary=action(R.string.rename_trip) { renameSample() },
                tertiary=action(R.string.delete_trip) {
                    model.delete()
                    Snackbar.make(requireView(),getString(R.string.preview_short)+" · "+getString(R.string.trip_deleted),Snackbar.LENGTH_LONG)
                        .setDuration(5_000).setAction(R.string.undo) { model.undo() }.show()
                })
        } else list+=UiSection("empty",getString(R.string.trips_empty),getString(R.string.home_empty),artwork=TravelArtwork.EmptyTrips,primary=action(R.string.plan_trip,workspace.state.value.loaded) {
            if(sample!=null) sampleAction() else openPlanner(workspace,R.id.trips_fragment)
        })
        if(sample!=null && model.deleted.value && System.currentTimeMillis()<model.until.value) list+=UiSection("undo",getString(R.string.trip_deleted),primary=action(R.string.undo) { model.undo() })
        if(sample?.state=="cleanupfail") list+=UiSection("cleanup",getString(R.string.cleanup_failed),primary=action(R.string.retry) { sampleAction() })
        if (sample == null) {
            val bookmarks = destinations.state.value
            if (bookmarks.readFailed) {
                list += UiSection("bookmarks_error", getString(R.string.data_read_error),
                    primary = action(R.string.retry) { destinations.retry() })
            }
            ExploreCatalog.destinations.filter { it.placeId in bookmarks.ids.orEmpty() }.forEach { destination ->
                list += UiSection("bookmark_${destination.placeId}", destinationTitle(destination),
                    badge = getString(R.string.explore_saved_destination),
                    primary = action(R.string.explore_details) {
                        navigateUi(R.id.trips_fragment, R.id.destination_fragment, bundleOf("destinationId" to destination.placeId))
                    })
            }
        }
        rows(list)
    }
    private fun renameSample() {
        val input=DialogTextInputBinding.inflate(layoutInflater)
        input.textLayout.hint=getString(R.string.trip_name)
        input.textInput.setText(model.name.value.ifBlank { catalog.plan()?.title.orEmpty() })
        val dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rename_trip).setView(input.root)
            .setPositiveButton(R.string.rename_trip,null).setNegativeButton(R.string.cancel,null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name=input.textInput.text.toString().trim()
                if(name.isEmpty() || name.length>80) input.textLayout.error=getString(R.string.error_trip_name)
                else { model.rename(name);dialog.dismiss() }
            }
        }
        dialog.show()
    }
}

@AndroidEntryPoint
class HistoryFragment : MvpListFragment() {
    @Inject lateinit var catalog: UiPreviewCatalog
    @Inject lateinit var planStore: com.example.travelassistant.core.domain.PlanStore
    private val localWorkspace: WorkspaceViewModel by activityViewModels()
    override fun onViewCreated(view: View,state: Bundle?) {
        super.onViewCreated(view,state)
        if(preview(catalog)==null) { LocalPlanUi(this,planStore,localWorkspace,"history",R.id.history_fragment).start();return }
        if(preview(catalog)==null || catalog.plan()==null) { rows(listOf(UiSection("empty",getString(R.string.no_history),getString(R.string.history_readonly))));return }
        val list=mutableListOf(sampleBanner(),UiSection("title",catalog.plan()!!.title,getString(R.string.history_readonly)))
        listOf(2,1).forEach { version -> list+=UiSection("version_$version",getString(R.string.version_label,version),getString(if(version==2) R.string.saved_local else R.string.historical),
            primary=action(R.string.open_plan) {
                val args=previewArguments(if(version==2) "I01:saved" else "I01:historical").apply { putInt("selectedVersion",version) }
                navigateUi(R.id.history_fragment,R.id.itinerary_fragment,args)
            }) }
        rows(list)
    }
}
