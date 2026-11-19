package com.example.travelassistant.feature.planner

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.ui.*
import com.example.travelassistant.core.ui.R as UiR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

class PlanScreenViewModel(private val handle: SavedStateHandle) : ViewModel() {
    val selectedDay=handle.getStateFlow("selectedDay",0)
    fun selectDay(day: Int) { handle["selectedDay"]=day }
}

@AndroidEntryPoint
class ProgressFragment : MvpListFragment() {
    @Inject lateinit var catalog: UiPreviewCatalog
    @Inject lateinit var previousPlans: com.example.travelassistant.core.domain.PlanStore
    private val workspace: WorkspaceViewModel by activityViewModels()
    private val generation: GenerationViewModel by activityViewModels()
    override fun onViewCreated(view: View,state: Bundle?) {
        super.onViewCreated(view,state)
        val sample=preview(catalog)
        if(sample==null) {
            generation.resume()
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    combine(generation.state,generation.actionFailed,generation.busy) { op,error,busy -> Triple(op,error,busy) }.collect { (op,error,busy) ->
                        val waiting=op.status in setOf("PENDING","QUEUED","RUNNING")
                        val uncertain=op.status in setOf("UNKNOWN","AUTH_ERROR")
                        val ready=op.status=="RECEIVED"
                        val message=when {
                            op.error=="AI_PROVIDER_HTTP_503" -> UiR.string.generate_unavailable
                            op.error=="AI_RATE_LIMITED" -> UiR.string.generate_rate_limit
                            op.error=="AI_QUOTA_EXHAUSTED" -> UiR.string.generate_ai_quota
                            op.error=="DAILY_QUOTA" -> UiR.string.generate_daily_quota
                            op.status=="AUTH_ERROR" -> UiR.string.generate_auth_error
                            op.status=="CANCELED" && op.error=="REMOTE_CANCEL_UNCONFIRMED" -> UiR.string.generate_canceled_auth_body
                            op.status=="CANCELED" -> UiR.string.generate_canceled_body
                            op.status=="EXPIRED" -> UiR.string.expired_body
                            uncertain -> UiR.string.generate_recover_body
                            waiting -> UiR.string.generate_wait_body
                            ready -> UiR.string.generate_ready_body
                            op.status=="NONE" -> UiR.string.no_request
                            else -> UiR.string.generate_failed_body
                        }
                        val artwork=when {
                            waiting -> TravelArtwork.Generating
                            op.status=="UNKNOWN" -> TravelArtwork.Connection
                            op.status in setOf("FAILED","AUTH_ERROR") -> TravelArtwork.Error
                            else -> null
                        }
                        val sections=mutableListOf(UiSection("operation",getString(when {ready -> UiR.string.open_plan;waiting -> UiR.string.state_pending;op.status=="CANCELED" -> UiR.string.cancel_request;else -> UiR.string.generate_status}),getString(message),progress=waiting,artwork=artwork,
                            primary=if(ready) action(UiR.string.open_plan) { navigateUi(UiR.id.progress_fragment,UiR.id.itinerary_fragment,Bundle().apply {putString("candidateId",op.operationId)}) }
                                else if(waiting||uncertain) action(UiR.string.check_status,!busy){generation.resume()}
                                else action(if(op.status=="FAILED") UiR.string.review_retry else UiR.string.edit_brief,!busy){openPlanner(workspace,UiR.id.progress_fragment,true)},
                            secondary=if(waiting||uncertain) action(UiR.string.cancel_request){generation.cancel()} else action(UiR.string.back_home){navigateUi(UiR.id.progress_fragment,UiR.id.home_graph,topLevel=true)},
                            tertiary=if(waiting||uncertain) action(UiR.string.back_home){navigateUi(UiR.id.progress_fragment,UiR.id.home_graph,topLevel=true)} else null))
                        if(error) sections+=UiSection("connection",getString(UiR.string.generate_connect_error),artwork=TravelArtwork.Connection.takeUnless { artwork==it },primary=action(UiR.string.check_status){generation.resume()})
                        if(op.status in setOf("FAILED","EXPIRED","CANCELED")) {
                            val hasPrevious=op.baseTripId!=null || try {previousPlans.candidate()!=null} catch(c: kotlinx.coroutines.CancellationException){throw c} catch(_: Exception){false}
                            if(hasPrevious) sections+=UiSection("retry",getString(UiR.string.generate_preserved),secondary=action(UiR.string.open_plan){navigateUi(UiR.id.progress_fragment,UiR.id.itinerary_fragment,Bundle().apply {op.baseTripId?.let {putString("tripId",it)}})})
                            if(generation.mockAvailable && op.error in setOf("AI_RATE_LIMITED","AI_QUOTA_EXHAUSTED","DAILY_QUOTA","AI_PROVIDER_HTTP_503","AI_TIMEOUT","DEADLINE_EXCEEDED")) sections+=UiSection("mock",getString(UiR.string.mock_label),getString(UiR.string.mock_body),primary=action(UiR.string.use_mock,!busy){viewLifecycleOwner.lifecycleScope.launch {generation.begin(true)}})
                        }
                        rows(sections)
                    }
                }
            }
            return
        }
        val waiting=sample.state in setOf("normal","ack","recovery")
        rows(listOf(sampleBanner(),UiSection("stage",if(sample.state=="normal") getString(UiR.string.state_pending) else previewTitle(sample),
            getString(UiR.string.preview_banner),progress=waiting,artwork=if(waiting) TravelArtwork.Generating else TravelArtwork.Error,
            primary=action(if(sample.state in setOf("ack","recovery")) UiR.string.check_status else UiR.string.retry) { sampleAction() },
            secondary=if(waiting) action(UiR.string.cancel_request) {
                MaterialAlertDialogBuilder(requireContext()).setTitle(UiR.string.cancel_request).setMessage(UiR.string.preview_dialog)
                    .setPositiveButton(UiR.string.cancel_request) { _,_ -> sampleAction() }.setNegativeButton(UiR.string.keep_working,null).show()
            } else action(UiR.string.edit_brief) { navigateUi(UiR.id.progress_fragment,UiR.id.where_fragment,previewArguments("P01:normal")) },
            tertiary=if(!waiting) action(UiR.string.open_plan) { navigateUi(UiR.id.progress_fragment,UiR.id.itinerary_fragment,previewArguments("I01:saved")) } else null),
            UiSection("brief",getString(UiR.string.brief_review),catalog.plan()?.let { it.title+"\n"+getString(UiR.string.content_language,getString(UiR.string.english)) }.orEmpty(),
                primary=action(UiR.string.back_home) { navigateUi(UiR.id.progress_fragment,UiR.id.home_graph,topLevel=true) })))
    }
}

@AndroidEntryPoint
class ItineraryFragment : MvpListFragment() {
    @Inject lateinit var catalog: UiPreviewCatalog
    @Inject lateinit var planStore: com.example.travelassistant.core.domain.PlanStore
    private val localWorkspace: WorkspaceViewModel by activityViewModels()
    private val workspace: WorkspaceViewModel by activityViewModels()
    private val model: PlanScreenViewModel by viewModels()
    private val args get()=ItineraryFragmentArgs.fromBundle(requireArguments())
    override fun onViewCreated(view: View,state: Bundle?) {
        super.onViewCreated(view,state)
        if(preview(catalog)==null) { LocalPlanUi(this,planStore,localWorkspace,"itinerary",UiR.id.itinerary_fragment).start();return }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val expiration=flow {
                    while(true) { emit(expired()); delay(1_000) }
                }.distinctUntilChanged()
                combine(model.selectedDay,expiration) { day,expired -> day to expired }.collect { (day,expired) -> render(day,expired) }
            }
        }
    }
    private fun saved()=preview(catalog)?.state in setOf("saved","historical")
    private fun expired()=preview(catalog)?.state=="expired" || (!saved() && args.previewExpiry>0 && System.currentTimeMillis()>=args.previewExpiry)
    private fun render(dayIndex: Int,expired: Boolean) {
        val sample=preview(catalog)
        val plan=if(sample!=null) catalog.plan() else null
        if(plan==null) {
            rows(listOf(UiSection("missing",getString(UiR.string.plan_missing),getString(UiR.string.plan_missing_body),primary=action(UiR.string.resume_draft) { openPlanner(workspace,UiR.id.itinerary_fragment,true) })))
            return
        }
        if(expired) {
            rows(listOf(sampleBanner(),UiSection("expired",getString(UiR.string.expired_title),getString(UiR.string.expired_body),primary=action(UiR.string.resume_draft) {
                navigateUi(UiR.id.itinerary_fragment,UiR.id.where_fragment,previewArguments("P01:normal"))
            })))
            return
        }
        val historical=sample!!.state=="historical"
        val version=if(historical) 1 else plan.version
        val badge=when(sample.state) { "historical" -> UiR.string.historical; "saved" -> UiR.string.saved_local; "changes","conflict","mismatch" -> UiR.string.unsaved_changes; else -> UiR.string.not_saved }
        val list=mutableListOf(sampleBanner())
        list+=UiSection("summary",plan.title,getString(UiR.string.plan_summary,plan.days.first().date,plan.days.last().date,resources.getQuantityString(UiR.plurals.travelers_count,plan.group,plan.group),plan.requestedBudget.toPlainString(),plan.currency),
            badge=getString(badge)+(if(saved()) " · "+getString(UiR.string.version_label,version) else ""))
        if(!saved()) list+=UiSection("expiry",getString(UiR.string.expires_label,DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(resources.configuration.locales[0]).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(args.previewExpiry))),getString(UiR.string.not_saved))
        list+=UiSection("language",getString(UiR.string.content_language,if(plan.language=="vi") getString(UiR.string.vietnamese) else getString(UiR.string.english)),
            secondary=action(UiR.string.language_settings) { navigateUi(UiR.id.itinerary_fragment,UiR.id.settings_graph) })
        if(sample.state in setOf("savefail","conflict","writefail","mismatch")) list+=UiSection("error",previewTitle(sample),
            if(sample.state=="conflict") getString(UiR.string.conflict_body) else getString(UiR.string.preview_banner),
            primary=action(if(sample.state=="conflict") UiR.string.reopen_latest else UiR.string.retry) {
                if(sample.state=="conflict") navigateUi(UiR.id.itinerary_fragment,UiR.id.itinerary_fragment,previewArguments("I01:saved")) else sampleAction()
            })
        val selected=dayIndex.coerceIn(plan.days.indices)
        list+=UiSection("days",getString(UiR.string.itinerary_title),choices=plan.days.mapIndexed { index,day -> UiAction(getString(UiR.string.day_label,index+1,day.date)) { model.selectDay(index) } },selectedChoice=selected)
        val day=plan.days[selected]
        list+=UiSection("day_heading",getString(UiR.string.day_label,selected+1,day.date),day.title)
        day.activities.forEachIndexed { index,item -> list+=UiSection("activity_${day.date}_$index",item.time+" · "+item.title,getString(UiR.string.activity_details,item.area,item.durationMinutes,item.note)) }
        list+=UiSection("assumptions",getString(UiR.string.assumptions),plan.assumption+"\n"+getString(UiR.string.source_unverified),
            primary=action(UiR.string.open_budget) {
                if(!isResumed || findNavController().currentDestination?.id!=UiR.id.itinerary_fragment) return@action
                findNavController().navigate(ItineraryFragmentDirections.actionItineraryToBudget(previewCase=args.previewCase,previewExpiry=args.previewExpiry,tripId=args.tripId,selectedVersion=version))
            })
        val share=action(UiR.string.share_plan) { shareSample(plan,dayIndex) }
        if(historical) list+=UiSection("actions",getString(UiR.string.historical),primary=share)
        else if(saved()) list+=UiSection("actions",getString(UiR.string.saved_local),primary=share,
            secondary=action(UiR.string.edit_brief) { navigateUi(UiR.id.itinerary_fragment,UiR.id.review_fragment,previewArguments("P03:normal")) },
            tertiary=action(UiR.string.history_action) { navigateUi(UiR.id.itinerary_fragment,UiR.id.history_fragment,previewArguments("M02:normal")) })
        else if(sample.state!="conflict") list+=UiSection("actions",getString(UiR.string.not_saved),primary=action(if(sample.state in setOf("changes","mismatch")) UiR.string.save_changes else UiR.string.save_plan) {
            if(expired()) { render(dayIndex,true); return@action }
            if(sample.state=="mismatch") MaterialAlertDialogBuilder(requireContext()).setTitle(UiR.string.mismatch_title).setMessage(UiR.string.mismatch_body)
                .setPositiveButton(UiR.string.save_result) { _,_ -> sampleAction() }.setNegativeButton(UiR.string.cancel,null).show()
            else sampleAction()
        },secondary=share,tertiary=action(UiR.string.edit_brief) { navigateUi(UiR.id.itinerary_fragment,UiR.id.where_fragment,previewArguments("P01:normal")) })
        rows(list)
    }
    private fun shareSample(plan: PlanPresentation,day: Int) {
        if(expired()) { render(day,true); return }
        val text=buildString {
            append(getString(UiR.string.preview_banner));append("\n\n");append(plan.title)
            plan.days.forEach { d -> append("\n\n${d.date} · ${d.title}"); d.activities.forEach { a -> append("\n${a.time} · ${a.title}") } }
            append("\n\n");append(plan.assumption)
        }
        val intent=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text)
        try { startActivity(Intent.createChooser(intent,getString(UiR.string.share_plan))) }
        catch (_: android.content.ActivityNotFoundException) { sampleAction() }
    }
}

@AndroidEntryPoint
class BudgetFragment : MvpListFragment() {
    @Inject lateinit var catalog: UiPreviewCatalog
    @Inject lateinit var planStore: com.example.travelassistant.core.domain.PlanStore
    private val localWorkspace: WorkspaceViewModel by activityViewModels()
    override fun onViewCreated(view: View,state: Bundle?) {
        super.onViewCreated(view,state)
        if(preview(catalog)==null) { LocalPlanUi(this,planStore,localWorkspace,"budget",UiR.id.budget_fragment).start();return }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow { while(true) { emit(expired()); delay(1_000) } }
                    .distinctUntilChanged().collect { render(it) }
            }
        }
    }
    private fun expired(): Boolean = preview(catalog)?.state !in setOf("saved","historical") &&
        (arguments?.getLong("previewExpiry",0L) ?: 0L).let { it>0 && System.currentTimeMillis()>=it }
    private fun render(expired: Boolean) {
        val sample=preview(catalog);val plan=if(sample!=null) catalog.plan() else null
        if(plan==null) { rows(listOf(UiSection("empty",getString(UiR.string.budget_title),getString(UiR.string.plan_missing_body)))); return }
        if(expired) { rows(listOf(sampleBanner(),UiSection("expired",getString(UiR.string.expired_title),getString(UiR.string.expired_body)))); return }
        val costs=plan.costs.map { if(sample!!.state=="unknown" && it.category=="activities") it.copy(amount=null) else if(sample.state=="overrun" && it.category=="stay") it.copy(amount=BigDecimal("1200")) else it }
        fun money(amount: BigDecimal) = NumberFormat.getNumberInstance(resources.configuration.locales[0]).format(amount)+" "+plan.currency
        val list=mutableListOf(sampleBanner(),UiSection("budget",getString(UiR.string.budget_requested),money(plan.requestedBudget)),
            UiSection("scope",getString(UiR.string.transport_scope,getString(if(plan.transportIncluded) UiR.string.transport_included else UiR.string.transport_excluded)),getString(UiR.string.estimate_only)))
        costs.forEach { c -> list+=UiSection("cost_${c.category}",getString(when(c.category) { "stay" -> UiR.string.cost_stay; "food" -> UiR.string.cost_food; "local" -> UiR.string.cost_local; else -> UiR.string.cost_activities }),c.amount?.let { money(it) } ?: getString(UiR.string.unknown_cost)) }
        val known=costs.mapNotNull { it.amount }.fold(BigDecimal.ZERO,BigDecimal::add)
        list+=UiSection("subtotal",getString(UiR.string.known_subtotal),money(known))
        if(costs.any { it.amount==null }) list+=UiSection("incomplete",getString(UiR.string.incomplete_total),getString(UiR.string.unknown_cost))
        else {
            list+=UiSection("total",getString(UiR.string.estimated_total),money(known))
            val remaining=plan.requestedBudget-known
            list+=UiSection("remaining",getString(if(remaining.signum()>=0) UiR.string.remaining_budget else UiR.string.over_budget),money(remaining.abs()))
        }
        list+=UiSection("assumptions",getString(UiR.string.assumptions),plan.assumption+"\n"+getString(UiR.string.source_unverified))
        rows(list)
    }
}
