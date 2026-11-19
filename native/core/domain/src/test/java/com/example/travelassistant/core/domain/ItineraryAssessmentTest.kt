package com.example.travelassistant.core.domain
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
class ItineraryAssessmentTest {
 private val brief=PlannerDraft(currency="USD")
 private fun plan(vararg activities: PlanActivity)=PlanPresentation("p","Test","en","USD",BigDecimal("100"),1,0,listOf(PlanDay("2026-12-01","Day",activities.toList())),listOf(PlanCost("stay",BigDecimal("30")),PlanCost("food",null),PlanCost("local",null),PlanCost("activities",null)),"Unverified",false)
 private fun a(time: String,minutes: Int=60)=PlanActivity(time,"Place $time","Area",minutes,"")
 @Test fun overlappingActivitiesAreBlocked(){assertTrue(ItineraryAssessment.assess(brief,plan(a("09:00"),a("09:30"))).hasConflicts)}
 @Test fun routeCannotBeAssumedFeasibleFromAnEmptyGap(){val r=ItineraryAssessment.assess(brief,plan(a("09:00"),a("10:00")));assertFalse(r.hasConflicts);assertTrue(r.findings.any{it.problem==PlanProblem.ROUTE_UNKNOWN})}
 @Test fun verifiedTravelNeedsItsBuffer(){val p=plan(a("09:00"),a("10:20"));val route=VerifiedTransfer("p",ItineraryAssessment.leg(p.days[0].date,p.days[0].activities[0],p.days[0].activities[1]),15,"https://provider.example/route");assertTrue(ItineraryAssessment.assess(brief,p,listOf(route)).hasConflicts)}
 @Test fun evidenceForAnotherPlanIsIgnored(){val p=plan(a("09:00"),a("10:00"));val route=VerifiedTransfer("another",ItineraryAssessment.leg(p.days[0].date,p.days[0].activities[0],p.days[0].activities[1]),1,"https://provider.example/route");assertTrue(ItineraryAssessment.assess(brief,p,listOf(route)).findings.any{it.problem==PlanProblem.ROUTE_UNKNOWN})}
 @Test fun unknownCostsAreNeverZeroOrComplete(){val r=ItineraryAssessment.assess(brief,plan(a("09:00")));assertEquals(BigDecimal("30"),r.knownCost);assertFalse(r.fullyEstimated);assertEquals(setOf("food","local","activities"),r.unknownCategories)}
 @Test fun knownSubtotalAlreadyOverBudgetIsAnOverrunEvenIfPartial(){val p=plan(a("09:00")).copy(costs=listOf(PlanCost("stay",BigDecimal("120"))));val r=ItineraryAssessment.assess(brief,p);assertTrue(r.overBudget);assertFalse(r.fullyEstimated)}
 @Test fun arrivalDepartureAndDayOverflowAreBlocked(){val r=ItineraryAssessment.assess(brief.copy(arrival="11:00",departure="20:00"),plan(a("09:00"),a("23:30")));assertTrue(r.findings.any{it.problem==PlanProblem.BEFORE_ARRIVAL});assertTrue(r.findings.any{it.problem==PlanProblem.AFTER_DEPARTURE});assertTrue(r.findings.any{it.problem==PlanProblem.DAY_OVERFLOW})}
 @Test fun missingCategoriesAreUnknown(){assertFalse(ItineraryAssessment.assess(brief,plan(a("09:00")).copy(costs=emptyList())).fullyEstimated)}
}
