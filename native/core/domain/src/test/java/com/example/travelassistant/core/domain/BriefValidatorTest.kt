package com.example.travelassistant.core.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BriefValidatorTest {
    private val today=LocalDate.of(2026,11,16)
    private val valid=PlannerDraft(destinationId="danang-vn",startDate="2026-12-01",endDate="2026-12-03",adults="2",budget="1200.50",currency="USD",transport="excluded",purpose="leisure")
    @Test fun validBriefAndCommaDecimalAreAccepted() { assertTrue(BriefValidator.validate(valid.copy(budget="1200,50"),today).isEmpty()) }
    @Test fun unresolvedCityNameCannotSubstituteForSelectedCountryAndId() { assertTrue(BriefIssue.DESTINATION in BriefValidator.validate(valid.copy(destinationId="",destinationQuery="Paris"),today)) }
    @Test fun inclusiveFourteenDaysIsAllowedButFifteenIsRejected() {
        assertFalse(BriefIssue.DATES in BriefValidator.validate(valid.copy(endDate="2026-12-14"),today))
        assertTrue(BriefIssue.DATES in BriefValidator.validate(valid.copy(endDate="2026-12-15"),today))
    }
    @Test fun pastAndReversedDatesAreRejected() {
        assertTrue(BriefIssue.DATES in BriefValidator.validate(valid.copy(startDate="2026-11-15"),today))
        assertTrue(BriefIssue.DATES in BriefValidator.validate(valid.copy(endDate="2026-11-30"),today))
    }
    @Test fun negativeFractionalAndOverLimitGroupsAreRejected() {
        for(value in listOf("-1","1.5","0","2147483647")) assertTrue(BriefIssue.GROUP in BriefValidator.validate(valid.copy(adults=value),today))
        assertTrue(BriefIssue.GROUP in BriefValidator.validate(valid.copy(adults="8",children="1",childBands="3_11"),today))
    }
    @Test fun EveryChildNeedsAnAgeBand() {
        assertTrue(BriefIssue.CHILD_AGES in BriefValidator.validate(valid.copy(children="2",childBands="3_11"),today))
        assertFalse(BriefIssue.CHILD_AGES in BriefValidator.validate(valid.copy(children="2",childBands="3_11,12_17"),today))
    }
    @Test fun unsupportedAndNonPositiveBudgetsAreRejected() {
        for(value in listOf("0","-2","NaN","Infinity","1e10","1,000.50")) assertTrue(BriefIssue.BUDGET in BriefValidator.validate(valid.copy(budget=value),today))
        assertTrue(BriefIssue.CURRENCY in BriefValidator.validate(valid.copy(currency="ZZZ"),today))
    }
    @Test fun transportIsExplicitAndIncludedTransportNeedsAnOrigin() {
        assertTrue(BriefIssue.TRANSPORT in BriefValidator.validate(valid.copy(transport=""),today))
        assertTrue(BriefIssue.ORIGIN in BriefValidator.validate(valid.copy(transport="included"),today))
        assertTrue(BriefIssue.ORIGIN in BriefValidator.validate(valid.copy(transport="included",originId="danang-vn"),today))
        assertFalse(BriefIssue.ORIGIN in BriefValidator.validate(valid.copy(transport="included",originId="hanoi-vn"),today))
    }
    @Test fun optionalTimesMustBeValidAndOrderedForSameDay() {
        assertTrue(BriefIssue.TIMES in BriefValidator.validate(valid.copy(arrival="25:00"),today))
        assertTrue(BriefIssue.TIMES in BriefValidator.validate(valid.copy(endDate=valid.startDate,arrival="18:00",departure="09:00"),today))
    }
    @Test fun purposeIsRequiredAndNotesAreLimited() {
        assertTrue(BriefIssue.PURPOSE in BriefValidator.validate(valid.copy(purpose=""),today))
        assertTrue(BriefIssue.NOTES in BriefValidator.validate(valid.copy(notes="a".repeat(1001)),today))
    }
    @Test fun firstStepDoesNotRequireLaterFieldsButGenerationRechecksEverything() {
        val firstStep=valid.copy(budget="",transport="",purpose="")
        assertTrue(BriefValidator.validate(firstStep,today,1).isEmpty())
        assertFalse(BriefValidator.validate(firstStep,today,3).isEmpty())
    }
    @Test fun optionalTimesFollowTheDisplayedHourMinuteContract() {
        for(value in listOf("09:30:00", "09:30:01.123", "9:30", " 09:30", "24:00")) {
            assertTrue(value,BriefIssue.TIMES in BriefValidator.validate(valid.copy(arrival=value),today))
            assertTrue(value,BriefIssue.TIMES in BriefValidator.validate(valid.copy(departure=value),today))
        }
        assertFalse(BriefIssue.TIMES in BriefValidator.validate(valid.copy(arrival="00:00",departure="23:59"),today))
    }

}
