package com.example.travelassistant.core.ui
import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.example.travelassistant.core.domain.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*

/** Real Room content only. Debug preview catalog never enters this presenter. */
class LocalPlanUi(private val f: MvpListFragment,private val store: PlanStore,private val workspace: WorkspaceViewModel,private val kind: String,private val source: Int) {
 private var lastRows: List<UiSection> = emptyList()
 private var busy=false
 private var selected=0
 private var confirmedSave=false
 private fun text(id: Int)=f.getString(id)
 private fun action(id: Int,run: ()->Unit)=UiAction(text(id),run=run)
 private fun launch(block: suspend ()->Unit) {
  if(busy)return;busy=true
  f.viewLifecycleOwner.lifecycleScope.launch { try { block() } catch(c: CancellationException){throw c} catch(_: Exception) {
   f.rows(listOf(UiSection("error",text(R.string.data_read_error),primary=action(R.string.retry){load()})))
  } finally {busy=false} }
 }
 fun start() {
  f.rows(listOf(UiSection("loading",text(R.string.plan_loading),progress=true)))
  f.viewLifecycleOwner.lifecycleScope.launch {
   f.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
    load()
    this.launch {workspace.failedCandidate.collect { if(it!=null) load() }}
    while(true) { delay(1000);if(kind!="history" && f.arguments?.getString("tripId").isNullOrBlank()) { val deadline=f.findNavController().currentBackStackEntry?.savedStateHandle?.get<Long>("localExpiry") ?: Long.MAX_VALUE;if(System.currentTimeMillis()>=deadline) { load();break } } }
   }
  }
 }
 fun load() { f.viewLifecycleOwner.lifecycleScope.launch { try { render() } catch(c: CancellationException){throw c} catch(_: Exception){f.rows(listOf(UiSection("read_error",text(R.string.data_read_error),primary=action(R.string.retry){load()})))} } }
 private suspend fun render() {
  val trip=f.findNavController().currentBackStackEntry?.savedStateHandle?.get<String>("localSavedTrip") ?: f.arguments?.getString("tripId").orEmpty();val version=f.arguments?.getInt("selectedVersion",0) ?: 0
  if(kind=="history") {
   val history=store.history(trip)
   f.rows(if(history.isEmpty()) listOf(UiSection("empty",text(R.string.no_history))) else history.map { r -> UiSection("v${r.version}",f.getString(R.string.version_label,r.version),text(R.string.history_readonly),primary=action(R.string.open_plan){f.navigateUi(source,R.id.itinerary_fragment,Bundle().apply{putString("tripId",trip);putInt("selectedVersion",r.version)})}) });return
  }
  val item=if(trip.isEmpty()) store.candidate()?.takeIf { f.arguments?.getString("candidateId").isNullOrBlank() || it.candidateId==f.arguments?.getString("candidateId") } else store.revision(trip,version)
  if(item==null && trip.isEmpty()) {
   val recovered=f.findNavController().currentBackStackEntry?.savedStateHandle?.get<String>("localCandidate")?.let { store.committed(it) } ?: f.arguments?.getString("candidateId")?.let {store.committed(it)}
   if(recovered!=null) {f.arguments?.putString("tripId",recovered.tripId);f.findNavController().currentBackStackEntry?.savedStateHandle?.set("localSavedTrip",recovered.tripId);workspace.reloadFromDisk();render();return}
  }
  if(item==null) { val expired=trip.isEmpty() && (f.findNavController().currentBackStackEntry?.savedStateHandle?.get<String>("localCandidate")!=null || !f.arguments?.getString("candidateId").isNullOrBlank());f.rows(listOf(UiSection("missing",text(if(expired) R.string.expired_title else R.string.plan_missing),text(if(expired) R.string.expired_body else R.string.plan_missing_body),primary=action(R.string.resume_draft){f.openPlanner(workspace,source,true)})));return }
  val candidate=trip.isEmpty();val plan=item.plan
  if(candidate) f.findNavController().currentBackStackEntry?.savedStateHandle?.set("localCandidate",item.candidateId)
  if(candidate) f.findNavController().currentBackStackEntry?.savedStateHandle?.set("localExpiry",item.expiresAt)
  fun money(value: java.math.BigDecimal)=java.text.NumberFormat.getNumberInstance(f.resources.configuration.locales[0]).format(value)+" "+plan.currency
  val list=mutableListOf(UiSection("summary",plan.title,f.getString(R.string.plan_summary,plan.days.first().date,plan.days.last().date,f.resources.getQuantityString(R.plurals.travelers_count,plan.group,plan.group),java.text.NumberFormat.getNumberInstance(f.resources.configuration.locales[0]).format(plan.requestedBudget),plan.currency),
   artwork=TravelArtwork.Saved.takeIf { confirmedSave && !candidate },
   badge=if(candidate) text(if(item.tripId==null) R.string.not_saved else R.string.unsaved_changes) else text(if(version>0) R.string.historical else R.string.saved_local)+" · "+f.getString(R.string.version_label,item.version)))
  if(plan.sourceMode=="mock") list+=UiSection("mock",text(R.string.mock_label),text(R.string.mock_body))
  if(candidate) list+=UiSection("expiry",f.getString(R.string.expires_label,java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM).withLocale(f.resources.configuration.locales[0]).withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(item.expiresAt))),text(R.string.not_saved))
  list+=UiSection("language",f.getString(R.string.content_language,text(if(plan.language=="vi") R.string.vietnamese else R.string.english)))
  if(kind=="budget") {
   list+=UiSection("requested",text(R.string.budget_requested),money(plan.requestedBudget))
   list+=UiSection("scope",f.getString(R.string.transport_scope,text(if(plan.transportIncluded) R.string.transport_included else R.string.transport_excluded)),text(R.string.estimate_only))
   plan.costs.forEachIndexed { i,c -> list+=UiSection("cost$i",text(when(c.category){"stay"->R.string.cost_stay;"food"->R.string.cost_food;"local"->R.string.cost_local;"transport"->R.string.transport_label;else->R.string.cost_activities}),c.amount?.let { money(it) } ?: text(R.string.unknown_cost)) }
   val sum=plan.costs.mapNotNull{it.amount}.fold(java.math.BigDecimal.ZERO,java.math.BigDecimal::add)
   list+=UiSection("subtotal",text(R.string.known_subtotal),money(sum))
   list+=UiSection("total",text(if(plan.costs.any{it.amount==null}) R.string.incomplete_total else R.string.estimated_total),if(plan.costs.any{it.amount==null}) text(R.string.unknown_cost) else money(sum))
   if(plan.costs.all{it.amount!=null}) {val remaining=plan.requestedBudget-sum;list+=UiSection("remaining",text(if(remaining.signum()>=0) R.string.remaining_budget else R.string.over_budget),money(remaining.abs()))}
  } else {
   selected=(f.findNavController().currentBackStackEntry?.savedStateHandle?.get<Int>("localDay") ?: selected).coerceIn(plan.days.indices)
   list+=UiSection("days",text(R.string.itinerary_title),choices=plan.days.mapIndexed { i,d -> UiAction(f.getString(R.string.day_label,i+1,d.date)){selected=i;f.findNavController().currentBackStackEntry?.savedStateHandle?.set("localDay",i);load()} },selectedChoice=selected)
   plan.days[selected].activities.forEachIndexed { i,a -> list+=UiSection("activity$i",a.time+" · "+a.title,f.getString(R.string.activity_details,a.area,a.durationMinutes,a.note),
    secondary=if(plan.sourceMode in setOf("openai","gemini") && a.sourceUrl?.startsWith("https://")==true) action(R.string.view_source){
     try {f.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(a.sourceUrl)))}catch(_: android.content.ActivityNotFoundException){MaterialAlertDialogBuilder(f.requireContext()).setMessage(R.string.source_open_failed).setPositiveButton(R.string.keep_working,null).show()}
    } else null) }
   list+=UiSection("budget",text(R.string.budget_title),primary=action(R.string.open_budget){f.navigateUi(source,R.id.budget_fragment,Bundle(f.arguments ?: Bundle()))})
  }
  val assessment=ItineraryAssessment.assess(item.brief,plan)
  list+=UiSection("quality",text(if(assessment.hasConflicts) R.string.quality_conflicts else R.string.quality_unverified),text(R.string.quality_explanation))
  list+=UiSection("assumptions",text(R.string.assumptions),plan.assumption+"\n"+text(R.string.source_unverified))
  if(candidate) list+=UiSection("save",text(R.string.not_saved),primary=action(if(item.tripId==null) R.string.save_plan else R.string.save_changes){save(item,false)})
  list+=UiSection("share",text(R.string.share_plan),primary=action(R.string.share_plan){launch {
   if(candidate && store.candidate()?.candidateId!=item.candidateId) {load();return@launch}
   val body=(if(plan.sourceMode=="mock") text(R.string.mock_label)+"\n"+text(R.string.mock_body)+"\n\n" else "")+plan.title+"\n"+plan.days.joinToString("\n\n"){d->d.date+" · "+d.title+"\n"+d.activities.joinToString("\n"){it.time+" · "+it.title}}+"\n"+plan.assumption
   f.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,body),text(R.string.share_plan)))
  }})
  if(!candidate) {
   list+=UiSection("history",text(R.string.history_title),primary=action(R.string.history_action){f.navigateUi(source,R.id.history_fragment,Bundle().apply{putString("tripId",trip)})})
   if(version==0) list+=UiSection("edit",text(R.string.edit_brief),primary=action(R.string.edit_brief){MaterialAlertDialogBuilder(f.requireContext()).setTitle(R.string.unfinished_title).setMessage(R.string.unfinished_body).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.discard_continue){_,_->launch{workspace.flush();if(store.edit(trip,true)){workspace.reloadFromDisk();f.navigateUi(source,R.id.where_fragment)}}}.show()})
  }
  if(candidate && workspace.failedCandidate.value==item.candidateId) list+=UiSection("save_error",text(R.string.save_failed),primary=action(R.string.retry){save(item,false)})
  lastRows=list;f.rows(list)
 }
 private fun save(item: StoredPlan,confirm: Boolean) { launch {
  try {
  when(val result=workspace.commitPlan(store,item.candidateId,confirm)) {
   is SaveResult.Saved -> {confirmedSave=true;f.findNavController().currentBackStackEntry?.savedStateHandle?.set("localSavedTrip",result.tripId);f.arguments?.putString("tripId",result.tripId);f.arguments?.putInt("selectedVersion",0);render()}
   SaveResult.Expired -> render()
   SaveResult.Conflict -> f.rows(listOf(UiSection("conflict",text(R.string.conflict_body),primary=action(R.string.reopen_latest){f.arguments?.putString("tripId",item.tripId);load()})))
   SaveResult.Mismatch -> MaterialAlertDialogBuilder(f.requireContext()).setTitle(R.string.mismatch_title).setMessage(R.string.mismatch_body).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.save_result){_,_->save(item,true)}.show()
  }
  } catch(c: CancellationException){throw c} catch(_: Exception) { f.rows(lastRows+UiSection("save_error",text(R.string.save_failed),primary=action(R.string.retry){save(item,false)})) }
 } }
}
