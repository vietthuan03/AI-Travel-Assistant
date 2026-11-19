package com.example.travelassistant.core.domain

import java.math.BigDecimal
import java.time.LocalTime

/** Deterministic checks, not an LLM's assertion that its own answer is feasible. */
enum class PlanProblem { INVALID_TIME, OVERLAP, DAY_OVERFLOW, BEFORE_ARRIVAL, AFTER_DEPARTURE, TRANSFER_CONFLICT, ROUTE_UNKNOWN, PLACE_HOURS_UNKNOWN, INVALID_COST, CURRENCY_MISMATCH }
data class PlanFinding(val problem: PlanProblem,val day: Int,val activity: Int,val blocking: Boolean)
data class VerifiedTransfer(val planId: String,val leg: String,val minutes: Int,val source: String)
data class PlanAssessment(val findings: List<PlanFinding>,val knownCost: BigDecimal,val unknownCategories: Set<String>,val overBudget: Boolean) {
    val hasConflicts get()=findings.any { it.blocking }
    val fullyEstimated get()=unknownCategories.isEmpty()
    // Missing source data is never equivalent to "verified feasible".
    val needsVerification get()=findings.any { !it.blocking }
}
object ItineraryAssessment {
    fun leg(date: String,from: PlanActivity,to: PlanActivity)=listOf(date,from.time,from.title,from.area,to.time,to.title,to.area).joinToString("") { "${it.length}:$it" }
    private fun minute(raw: String): Int? = raw.takeIf { it.matches(Regex("[0-9]{2}:[0-9]{2}")) }?.let { runCatching {LocalTime.parse(it).let { t->t.hour*60+t.minute }}.getOrNull() }
    fun assess(brief: PlannerDraft,plan: PlanPresentation,transfers: List<VerifiedTransfer> = emptyList(),bufferMinutes: Int=10): PlanAssessment {
        require(bufferMinutes in 0..120)
        val findings=mutableListOf<PlanFinding>()
        fun issue(p: PlanProblem,d: Int,a: Int,blocking: Boolean=true) {findings+=PlanFinding(p,d,a,blocking)}
        plan.days.forEachIndexed { day,d ->
            var previous: PlanActivity?=null;var end: Int?=null
            d.activities.forEachIndexed { index,a ->
                val start=minute(a.time)
                if(start==null || a.durationMinutes<=0) issue(PlanProblem.INVALID_TIME,day,index)
                else {
                    val finish=start.toLong()+a.durationMinutes
                    if(finish>1440) issue(PlanProblem.DAY_OVERFLOW,day,index)
                    if(day==0 && minute(brief.arrival)?.let { start<it }==true) issue(PlanProblem.BEFORE_ARRIVAL,day,index)
                    if(day==plan.days.lastIndex && minute(brief.departure)?.let { finish>it }==true) issue(PlanProblem.AFTER_DEPARTURE,day,index)
                    end?.let { previousEnd ->
                        if(start<previousEnd) issue(PlanProblem.OVERLAP,day,index)
                        else {
                            val signature=leg(d.date,previous!!,a)
                            val evidence=transfers.singleOrNull { it.planId==plan.id && it.leg==signature && it.minutes in 0..1440 && it.source.startsWith("https://") }
                            if(evidence==null) issue(PlanProblem.ROUTE_UNKNOWN,day,index,false)
                            else if(start.toLong()-previousEnd<evidence.minutes.toLong()+bufferMinutes) issue(PlanProblem.TRANSFER_CONFLICT,day,index)
                        }
                    }
                    end=finish.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
                // Opening-hours evidence is not represented in the legacy payload yet.
                issue(PlanProblem.PLACE_HOURS_UNKNOWN,day,index,false)
                previous=a
            }
        }
        if(plan.currency!=brief.currency) issue(PlanProblem.CURRENCY_MISMATCH,-1,-1)
        if(plan.costs.any { it.amount?.signum()?.let { n->n<0 }==true }) issue(PlanProblem.INVALID_COST,-1,-1)
        val known=plan.costs.mapNotNull { it.amount?.takeIf { n->n.signum()>=0 } }.fold(BigDecimal.ZERO,BigDecimal::add)
        val expected=setOf("stay","food","local","activities")+if(brief.transport=="included") setOf("transport") else emptySet()
        val unknown=(expected-plan.costs.map{it.category}.toSet())+plan.costs.filter{it.amount==null}.map{it.category}
        return PlanAssessment(findings,known,unknown,known>plan.requestedBudget)
    }
}
