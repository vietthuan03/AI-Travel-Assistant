package com.example.travelassistant.feature.planner

import android.os.Bundle
import android.view.*
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.ui.*
import com.example.travelassistant.core.ui.R as UiR
import com.example.travelassistant.feature.planner.databinding.FragmentPlannerFormBinding
import com.example.travelassistant.feature.planner.databinding.ItemChildAgeBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.*
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class PlannerFormUiModel(private val handle: SavedStateHandle) : ViewModel() {
    val unavailable = handle.getStateFlow("unavailable", false)
    fun unavailable() { handle["unavailable"] = true }
}

abstract class PlannerFormFragment : Fragment() {
    protected abstract val step: Int
    @Inject lateinit var catalog: UiPreviewCatalog
    private val workspace: WorkspaceViewModel by activityViewModels()
    private val generation: GenerationViewModel by activityViewModels()
    private val local: PlannerFormUiModel by viewModels()
    private var binding: FragmentPlannerFormBinding? = null
    private var rendering = false
    private var generating = false
    private var inputReady = false
    private var entered = false
    private var childCount = -1
    private val ageRows = mutableListOf<ItemChildAgeBinding>()
    private val placeLabels get() = TravelCatalog.places.map { placeLabel(it) }
    private val bandCodes = listOf("0_2","3_11","12_17")
    private val bandLabels get() = listOf(getString(UiR.string.age_0_2),getString(UiR.string.age_3_11),getString(UiR.string.age_12_17))
    private val destinationId get() = when(step) { 1 -> UiR.id.where_fragment; 2 -> UiR.id.travelers_fragment; else -> UiR.id.review_fragment }
    private val sample get() = preview(catalog)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentPlannerFormBinding.inflate(inflater,container,false).also { binding=it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        if(step==1 && redirectUiEntry(UiR.id.where_fragment,"P01")) return
        val b=requireNotNull(binding)
        listOf(b.adults,b.children,b.budget).forEach { it.numericInput() }
        b.whereSection.isVisible=step==1
        b.groupSection.isVisible=step==2
        b.preferencesSection.isVisible=step==3
        b.languageSettings.isVisible=step==3
        b.stepLabel.text = getString(UiR.string.step_label, step)
        b.stepTitle.setText(when (step) {
            1 -> UiR.string.where_title
            2 -> UiR.string.travelers_title
            else -> UiR.string.review_title
        })
        b.stepIntro.setText(when (step) {
            1 -> UiR.string.planner_where_intro
            2 -> UiR.string.planner_group_intro
            else -> UiR.string.planner_review_intro
        })
        b.stepProgress.progress=step
        if (step == 1) {
            b.dateFields.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (right - left != oldRight - oldLeft) arrangeDateFields(b.dateFields, right - left)
            }
        }
        b.continuePlan.setText(if(step==3) UiR.string.generate_plan else UiR.string.action_continue)
        fun watch(edit: EditText, update: (PlannerDraft,String)->PlannerDraft) {
            edit.doAfterTextChanged { value -> if(inputReady && !rendering && sample==null) workspace.edit { update(it,value.toString()) } }
        }
        b.destination.options(placeLabels)
        b.origin.options(placeLabels)
        watch(b.destination) { d,v -> d.copy(destinationQuery=v,destinationId="") }
        watch(b.origin) { d,v -> d.copy(originQuery=v,originId="") }
        b.destination.setOnItemClickListener { parent,_,position,_ ->
            val label=parent.getItemAtPosition(position).toString()
            val place=TravelCatalog.places.first { placeLabel(it)==label }
            workspace.edit { it.copy(destinationId=place.id,destinationQuery=label) }
        }
        b.origin.setOnItemClickListener { parent,_,position,_ ->
            val label=parent.getItemAtPosition(position).toString()
            val place=TravelCatalog.places.first { placeLabel(it)==label }
            workspace.edit { it.copy(originId=place.id,originQuery=label) }
        }
        watch(b.adults) { d,v -> d.copy(adults=v) }
        watch(b.children) { d,v ->
            val count=v.toIntOrNull()?.takeIf { it in 0..7 }
            d.copy(children=v,childBands=if(count!=null) d.childBands.split(',').take(count).joinToString(",") else d.childBands)
        }
        watch(b.budget) { d,v -> d.copy(budget=v) }; watch(b.arrival) { d,v -> d.copy(arrival=v) }
        watch(b.departure) { d,v -> d.copy(departure=v) }; watch(b.notes) { d,v -> d.copy(notes=v) }
        choose(b.currency,TravelCatalog.currencies,TravelCatalog.currencies) { d,v -> d.copy(currency=v) }
        choose(b.transport,listOf("included","excluded"),listOf(getString(UiR.string.transport_included),getString(UiR.string.transport_excluded))) { d,v -> d.copy(transport=v) }
        choose(b.purpose,listOf("leisure","business","family","other"),purposeLabels()) { d,v -> d.copy(purpose=v) }
        choose(b.pace,listOf("relaxed","moderate","active"),paceLabels()) { d,v -> d.copy(pace=v) }
        listOf(b.interestFood to "food",b.interestCulture to "culture",b.interestNature to "nature",b.interestRelax to "relax").forEach { (chip,key) ->
            chip.fitLargeText()
            chip.setOnCheckedChangeListener { _,checked -> if(inputReady && !rendering && sample==null) workspace.edit { d ->
                val values=d.interests.split(',').filter(String::isNotBlank).toMutableSet()
                if(checked) values.add(key) else values.remove(key)
                d.copy(interests=values.sorted().joinToString(","))
            } }
        }
        b.startDate.setOnClickListener { selectDates() }; b.endDate.setOnClickListener { selectDates() }
        b.continuePlan.setOnClickListener { continueForm() }
        b.draftRetry.setOnClickListener { if(sample!=null) sampleAction() else if(workspace.state.value.readFailed) workspace.retryRead() else workspace.flushSoon() }
        b.editWhere.setOnClickListener { moveTo(1) }; b.editGroup.setOnClickListener { moveTo(2) }
        b.languageSettings.setOnClickListener { navigateUi(destinationId,UiR.id.settings_graph) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(workspace.state,workspace.issues,local.unavailable) { workspace,issues,unavailable -> Triple(workspace,issues,unavailable) }.collect { (s,issues,unavailable) ->
                    val currentDraft=s.draft
                    if(s.loaded && !entered && sample==null && currentDraft!=null) {
                        entered=true
                        if(currentDraft.step!=step) workspace.edit(clearValidation=false) { it.copy(step=step) }
                    }
                    val d=if(sample!=null) {
                        val base=catalog.draft()
                        when {
                            step==1 && sample?.state=="invalid" -> base.copy(endDate="2026-12-01")
                            step==1 && sample?.state=="ambiguous" -> base.copy(destinationId="",destinationQuery="Paris")
                            step==2 && sample?.state=="invalid" -> base.copy(adults="-1",children="1",childBands="",budget="",transport="")
                            else -> base
                        }
                    } else s.draft ?: PlannerDraft()
                    render(d,s,if(sample?.state=="invalid") setOf(if(step==1) BriefIssue.DATES else BriefIssue.GROUP, if(step==2) BriefIssue.CHILD_AGES else BriefIssue.DATES)
                        else if(sample?.state=="ambiguous") setOf(BriefIssue.DESTINATION) else issues,unavailable)
                }
            }
        }
    }
    private fun arrangeDateFields(fields: LinearLayout, available: Int) {
        val metrics = resources.displayMetrics
        val sideBySide = available >= 320 * metrics.density && resources.configuration.fontScale <= 1.3f
        fields.orientation = if (sideBySide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        for (index in 0 until fields.childCount) {
            val group = fields.getChildAt(index)
            val params = group.layoutParams as LinearLayout.LayoutParams
            params.width = if (sideBySide) 0 else ViewGroup.LayoutParams.MATCH_PARENT
            params.weight = if (sideBySide) 1f else 0f
            params.marginStart = if (sideBySide && index > 0) (12 * metrics.density).toInt() else 0
            group.layoutParams = params
        }
    }
    private fun choose(view: AutoCompleteTextView, codes: List<String>, labels: List<String>, update: (PlannerDraft,String)->PlannerDraft) {
        view.options(labels); view.keyListener=null
        view.setOnItemClickListener { parent,_,position,_ ->
            if(sample==null) workspace.edit { update(it,codes[labels.indexOf(parent.getItemAtPosition(position).toString())]) }
        }
    }
    private fun purposeLabels()=listOf(getString(UiR.string.purpose_leisure),getString(UiR.string.purpose_business),getString(UiR.string.purpose_family),getString(UiR.string.purpose_other))
    private fun paceLabels()=listOf(getString(UiR.string.pace_relaxed),getString(UiR.string.pace_moderate),getString(UiR.string.pace_active))
    private fun EditText.fill(value: String) { if(text.toString()!=value) { setText(value); setSelection(text.length) } }
    private fun render(d: PlannerDraft,s: WorkspaceUiState,issues: Set<BriefIssue>,unavailable: Boolean) {
        val b=binding ?: return
        rendering=true
        b.previewBanner.isVisible=sample!=null
        b.destination.choose(TravelCatalog.place(d.destinationId)?.let { placeLabel(it) } ?: d.destinationQuery)
        b.selectedPlace.isVisible=d.destinationId.isNotBlank()
        b.selectedPlace.text=TravelCatalog.place(d.destinationId)?.let { placeLabel(it) }.orEmpty()
        b.origin.choose(TravelCatalog.place(d.originId)?.let { placeLabel(it) } ?: d.originQuery)
        b.startDate.fill(d.startDate); b.endDate.fill(d.endDate)
        b.adults.fill(d.adults); b.children.fill(d.children); b.budget.fill(d.budget)
        b.currency.choose(d.currency)
        b.transport.choose(when(d.transport) { "included" -> getString(UiR.string.transport_included); "excluded" -> getString(UiR.string.transport_excluded); else -> "" })
        b.originLayout.isVisible=d.transport=="included"
        b.originLabel.isVisible=d.transport=="included"
        b.purpose.choose(listOf("leisure","business","family","other").indexOf(d.purpose).takeIf { it>=0 }?.let { purposeLabels()[it] }.orEmpty())
        b.pace.choose(paceLabels()[listOf("relaxed","moderate","active").indexOf(d.pace).coerceAtLeast(0)])
        b.arrival.fill(d.arrival); b.departure.fill(d.departure); b.notes.fill(d.notes)
        listOf(b.interestFood to "food",b.interestCulture to "culture",b.interestNature to "nature",b.interestRelax to "relax").forEach { (chip,key) -> chip.isChecked=key in d.interests.split(',') }
        renderChildren(d)
        b.reviewSummary.text=getString(UiR.string.brief_summary,
            TravelCatalog.place(d.destinationId)?.let { placeLabel(it) } ?: getString(UiR.string.not_selected),
            d.startDate,d.endDate,personCount(d.adults,UiR.plurals.adults_count),personCount(d.children,UiR.plurals.children_count),d.budget,d.currency,
            when(d.transport) { "included" -> getString(UiR.string.transport_included); "excluded" -> getString(UiR.string.transport_excluded); else -> getString(UiR.string.not_selected) },
            if(resources.configuration.locales[0].language=="vi") getString(UiR.string.vietnamese) else getString(UiR.string.english))
        b.reviewSummary.append(buildString {
            if(d.transport=="included") append("\n"+getString(UiR.string.origin_label)+": "+(TravelCatalog.place(d.originId)?.let { placeLabel(it) } ?: getString(UiR.string.not_selected)))
            d.childBands.split(',').filter(String::isNotBlank).forEachIndexed { index,band ->
                append("\n"+getString(UiR.string.child_age_label,index+1)+": "+(bandCodes.indexOf(band).takeIf { it>=0 }?.let { bandLabels[it] } ?: getString(UiR.string.not_selected)))
            }
            append("\n"+getString(UiR.string.purpose_label)+": "+(listOf("leisure","business","family","other").indexOf(d.purpose).takeIf { it>=0 }?.let { purposeLabels()[it] } ?: getString(UiR.string.not_selected)))
            append("\n"+getString(UiR.string.pace_label)+": "+paceLabels()[listOf("relaxed","moderate","active").indexOf(d.pace).coerceAtLeast(0)])
            val interests=listOf("food" to UiR.string.interest_food,"culture" to UiR.string.interest_culture,"nature" to UiR.string.interest_nature,"relax" to UiR.string.interest_relax)
                .filter { it.first in d.interests.split(',') }.map { getString(it.second) }
            if(interests.isNotEmpty()) append("\n"+getString(UiR.string.interests_label)+": "+interests.joinToString(", "))
            if(d.arrival.isNotBlank()) append("\n"+getString(UiR.string.arrival_label)+": "+d.arrival)
            if(d.departure.isNotBlank()) append("\n"+getString(UiR.string.departure_label)+": "+d.departure)
            if(d.notes.isNotBlank()) append("\n"+getString(UiR.string.notes_label)+": "+d.notes)
        })
        b.draftStatus.setText(if(sample!=null) { if(sample?.state=="draftfail") UiR.string.draft_failed else UiR.string.preview_banner }
            else if(s.readFailed) UiR.string.draft_read_failed else if(!s.loaded) UiR.string.draft_loading else when(s.storage) {
                DraftStorage.WRITING -> UiR.string.draft_saving; DraftStorage.FAILED -> UiR.string.draft_failed; else -> UiR.string.draft_kept })
        b.draftRetry.isVisible=s.readFailed || s.storage==DraftStorage.FAILED || sample?.state=="draftfail"
        b.availability.isVisible=step==3 || unavailable
        b.availability.text=if(sample!=null && sample?.state in setOf("offline","quota")) previewTitle(sample!!) else getString(UiR.string.generate_local_intro)
        val missing=sample==null && s.loaded && !s.readFailed && s.draft==null
        b.draftStatus.isVisible=!missing
        b.whereSection.isVisible=step==1 && !missing
        b.groupSection.isVisible=step==2 && !missing
        b.preferencesSection.isVisible=step==3 && !missing
        b.continuePlan.setText(if(missing) UiR.string.back_home else if(step==3) UiR.string.generate_plan else UiR.string.action_continue)
        if(missing) { b.availability.isVisible=true;b.availability.setText(UiR.string.draft_missing) }
        b.continuePlan.isEnabled=sample!=null || s.loaded && !s.readFailed
        errors(issues)
        val count=runCatching { ChronoUnit.DAYS.between(LocalDate.parse(d.startDate),LocalDate.parse(d.endDate))+1 }.getOrNull()
        b.dateHelper.text=if(count!=null && count in 1..14) resources.getQuantityString(UiR.plurals.inclusive_days,count.toInt(),count.toInt()) else getString(UiR.string.date_helper)
        setInputsEnabled(b.root,sample==null && s.loaded && !s.readFailed && !missing)
        rendering=false
    }
    private fun setInputsEnabled(view: View,enabled: Boolean) {
        if(view is EditText || view is com.google.android.material.chip.Chip) view.isEnabled=enabled
        if(view is ViewGroup) for(i in 0 until view.childCount) setInputsEnabled(view.getChildAt(i),enabled)
    }
    private fun renderChildren(d: PlannerDraft) {
        val b=binding ?: return
        val count=d.children.toIntOrNull()?.takeIf { it in 0..7 } ?: 0
        if(count!=childCount) {
            childCount=count; ageRows.clear(); b.childAges.removeAllViews()
            repeat(count) { index ->
                val row=ItemChildAgeBinding.inflate(layoutInflater,b.childAges,false)
                row.ageLabel.text=getString(UiR.string.child_age_label,index+1)
                row.age.options(bandLabels); row.age.keyListener=null
                row.age.setOnItemClickListener { parent,_,position,_ -> if(sample==null) workspace.edit { current ->
                    val values=current.childBands.split(',').toMutableList()
                    while(values.size<count) values.add("")
                    values[index]=bandCodes[bandLabels.indexOf(parent.getItemAtPosition(position).toString())]
                    current.copy(childBands=values.take(count).joinToString(","))
                } }
                ageRows+=row; b.childAges.addView(row.root)
            }
        }
        val bands=d.childBands.split(',')
        ageRows.forEachIndexed { i,row -> row.age.choose(bandCodes.indexOf(bands.getOrNull(i).orEmpty()).takeIf { it>=0 }?.let { bandLabels[it] }.orEmpty()) }
    }
    private fun personCount(raw: String,plural: Int): String = raw.toIntOrNull()?.let { resources.getQuantityString(plural,it,it) } ?: raw
    private fun errorFields(): List<Triple<BriefIssue,TextInputLayout,Int>> {
        val b=binding!!
        return listOf(Triple(BriefIssue.DESTINATION,b.destinationLayout,UiR.string.error_destination),Triple(BriefIssue.DATES,b.startDateLayout,UiR.string.error_dates),
            Triple(BriefIssue.DATES,b.endDateLayout,UiR.string.error_dates),Triple(BriefIssue.GROUP,b.adultsLayout,UiR.string.error_group),Triple(BriefIssue.GROUP,b.childrenLayout,UiR.string.error_group),
            Triple(BriefIssue.BUDGET,b.budgetLayout,UiR.string.error_budget),Triple(BriefIssue.CURRENCY,b.currencyLayout,UiR.string.error_currency),Triple(BriefIssue.TRANSPORT,b.transportLayout,UiR.string.error_transport),
            Triple(BriefIssue.ORIGIN,b.originLayout,UiR.string.error_origin),Triple(BriefIssue.PURPOSE,b.purposeLayout,UiR.string.error_purpose),Triple(BriefIssue.TIMES,b.arrivalLayout,UiR.string.error_times),
            Triple(BriefIssue.TIMES,b.departureLayout,UiR.string.error_times),Triple(BriefIssue.NOTES,b.notesLayout,UiR.string.error_notes))
    }
    private fun errors(issues: Set<BriefIssue>) {
        errorFields().forEach { (issue,field,message) -> field.error=if(issue in issues) getString(message) else null }
        ageRows.forEach { it.ageLayout.error=if(BriefIssue.CHILD_AGES in issues) getString(UiR.string.error_child_ages) else null }
    }
    private fun continueForm() {
        if(generating)return
        if(sample!=null) { sampleAction(); return }
        val d=workspace.state.value.draft ?: run { navigateUi(destinationId,UiR.id.home_graph,topLevel=true); return }
        val issues=workspace.validateStep(step)
        if(issues.isNotEmpty()) {
            val earlier=if(BriefIssue.DESTINATION in issues || BriefIssue.DATES in issues) 1 else if(issues.any { it in setOf(BriefIssue.GROUP,BriefIssue.CHILD_AGES,BriefIssue.BUDGET,BriefIssue.CURRENCY,BriefIssue.TRANSPORT,BriefIssue.ORIGIN) }) 2 else 3
            if(earlier<step) { moveTo(earlier); return }
            errors(issues)
            val first=errorFields().firstOrNull { it.first in issues && it.second.isShown }?.second ?: ageRows.firstOrNull()?.ageLayout
            first?.let {
                val focus=it.editText?.takeIf { field -> field.isFocusable } ?: it.apply { isFocusableInTouchMode=true }
                focus.requestFocus()
                val rect=android.graphics.Rect()
                it.getDrawingRect(rect)
                binding?.plannerScroll?.let { scroll -> scroll.offsetDescendantRectToMyCoords(it,rect); scroll.smoothScrollTo(0,rect.top) }
            }
            return
        }
        if(step==3)generating=true
        viewLifecycleOwner.lifecycleScope.launch {
          try {
            workspace.edit(clearValidation=false) { it.copy(step=(step+1).coerceAtMost(3),outputLanguage=resources.configuration.locales[0].language) }
            if(!workspace.flush()) return@launch
            if(!isResumed || findNavController().currentDestination?.id!=destinationId) return@launch
            when(step) {
                1 -> findNavController().navigate(WhereFragmentDirections.actionWhereToTravelers())
                2 -> findNavController().navigate(TravelersFragmentDirections.actionTravelersToReview())
                else -> {
                    binding?.continuePlan?.isEnabled=false
                    if(generation.begin()) navigateUi(destinationId,UiR.id.progress_fragment)
                    else if(isResumed) com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(UiR.string.generate_failed).setMessage(when(generation.actionError.value) {
                            "SESSION_RATE_LIMIT" -> UiR.string.generate_session_limit
                            "REQUEST_BUSY" -> UiR.string.generate_busy
                            else -> UiR.string.generate_connect_error
                        })
                        .setPositiveButton(UiR.string.keep_working,null).show()
                    binding?.continuePlan?.isEnabled=true
                }
            }
          } finally { generating=false }
        }
    }
    private fun moveTo(targetStep: Int) {
        if(sample!=null) { sampleAction(); return }
        val target=if(targetStep==1) UiR.id.where_fragment else UiR.id.travelers_fragment
        val nav=findNavController()
        if(!nav.popBackStack(target,false)) navigateUi(destinationId,target)
    }
    private fun selectDates() {
        if(sample!=null) return
        childFragmentManager.executePendingTransactions()
        if((childFragmentManager.findFragmentByTag("trip_dates") as? androidx.fragment.app.DialogFragment)?.dialog?.isShowing==true) return
        val d=workspace.state.value.draft ?: PlannerDraft()
        val today=BriefValidator.today(d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val builder=MaterialDatePicker.Builder.dateRangePicker().setTitleText(UiR.string.where_title)
            .setPositiveButtonText(UiR.string.use_dates)
            .setCalendarConstraints(CalendarConstraints.Builder().setValidator(DateValidatorPointForward.from(today)).build())
        val dates=runCatching { androidx.core.util.Pair(LocalDate.parse(d.startDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),LocalDate.parse(d.endDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) }.getOrNull()
        // Restored drafts may be stale or invalid. Keep their text for correction,
        // but never hand an inverted/out-of-picker-range selection to Material.
        val pickerEnd=LocalDate.of(2100,12,31).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        if(dates!=null && dates.first>=today && dates.second>=dates.first && dates.second<=pickerEnd) builder.setSelection(dates)
        builder.build().apply {
            addOnPositiveButtonClickListener { range ->
                workspace.edit { it.copy(startDate=Instant.ofEpochMilli(range.first).atZone(ZoneOffset.UTC).toLocalDate().toString(),endDate=Instant.ofEpochMilli(range.second).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
            }
        }.show(childFragmentManager,"trip_dates")
    }
    // Framework view restoration calls TextWatchers too. The Room/ViewModel draft
    // owns input values and selected place IDs; restored display text is not an edit.
    override fun onViewStateRestored(state: Bundle?) {
        super.onViewStateRestored(state)
        inputReady=true
    }
    override fun onStop() { if(sample==null) workspace.flushSoon(); super.onStop() }
    override fun onDestroyView() { inputReady=false; entered=false; binding=null; ageRows.clear(); childCount=-1; super.onDestroyView() }
}
@AndroidEntryPoint class WhereFragment : PlannerFormFragment() { override val step=1 }
@AndroidEntryPoint class TravelersFragment : PlannerFormFragment() { override val step=2 }
@AndroidEntryPoint class ReviewFragment : PlannerFormFragment() { override val step=3 }
