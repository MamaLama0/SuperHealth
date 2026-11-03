package app.superhealth.analysis

import app.superhealth.bridge.Bridge
import app.superhealth.health.data.HealthRepository.SleepPhase
import app.superhealth.health.data.Nights
import app.superhealth.health.data.Performance
import app.superhealth.health.data.TrackPoint
import app.superhealth.insights.Insights
import app.superhealth.nutrition.domain.*
import app.superhealth.training.domain.Progression
import app.superhealth.training.domain.SetLog
import app.superhealth.wellbeing.Checkin
import app.superhealth.wellbeing.Cycle
import app.superhealth.wellbeing.Form
import app.superhealth.wellbeing.Journal
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class AdaptiveTrackingTest {
    @Test fun foodModelAbstainsWithoutRepeatedBenefitAndUsesNoFutureWeighIns() {
        val carbs = (0L..90).associateWith { if (it % 2 == 0L) 100.0 else 300.0 }
        fun points(noEffect: Boolean) = (1L..91).map { day ->
            val water = if (noEffect) 0.0 else (carbs.getValue(day - 1) - 200) / 200
            WeightTrendV2.Point(day, 80 + water, 80.0, 80.0, 0.0, 0.0, 0.3, predictedTween = 80.0)
        }
        val data = points(false)
        val shifts = FoodLogAdaptation.weightShifts(data, carbs)
        assertTrue(shifts.keys.all { it >= 44L })
        assertTrue(shifts.getValue(61) < 0 && shifts.getValue(62) > 0)
        assertEquals(shifts.filterKeys { it <= 65 }, FoodLogAdaptation.weightShifts(data.take(65), carbs))
        assertTrue(FoodLogAdaptation.weightShifts(points(true), carbs).isEmpty())
        val meals = listOf(.25,.35,.3,.1)
        assertEquals(meals, FoodLogAdaptation.mealShares(meals, List(6) { listOf(100.0,500.0,100.0,0.0) }))
        val adapted = FoodLogAdaptation.mealShares(meals, List(14) { listOf(100.0,500.0,100.0,0.0) })
        assertTrue(adapted[1] > meals[1]); assertTrue(adapted.last() > 0); assertEquals(1.0, adapted.sum(), 1e-12)
    }

    @Test fun sameStepsAloneCannotEarnAnExerciseCorrection() {
        fun weeks(confounded: Boolean, failedReplication: Boolean = false) = List(24) { i ->
            val sport = 100.0 + 80 * (if (i % 2 == 0) 1 else -1)
            val steps = if (confounded) sport * 10 else 8000.0
            ActivityEnergy.Week(i * 7L, sport, steps, (sport - 100) * (if (failedReplication && i >= 20) -3 else 3))
        }
        assertEquals(0.0, ActivityEnergy.correction(weeks(false).take(15),180.0,8000.0,100.0),0.0)
        assertEquals(0.0, ActivityEnergy.correction(weeks(true),180.0,1800.0,100.0),0.0)
        assertEquals(0.0, ActivityEnergy.correction(weeks(false,true),180.0,8000.0,100.0),0.0)
        assertEquals(100.0, ActivityEnergy.correction(weeks(false),180.0,8000.0,100.0),1e-9)
    }

    @Test fun replayMatchesPrefixIncludingDaysWithNoWeighIn() {
        val weights = (0L..75).filter { it % 4 != 2L }.associateWith { 80.0 + kotlin.math.sin(it * .3) }
        val full = WeightTrendV2.compute(weights,75)
        for (point in full) {
            val at = WeightTrendV2.compute(weights,point.epochDay).last()
            assertEquals(at.tween,point.filteredTween,1e-10)
            assertEquals(at.trend,point.filteredLevel,1e-10)
        }
        assertTrue(WeightTrendV2.compute(mapOf(99L to 90.0),4).isEmpty())
    }

    @Test fun energyHoldsUntilAnActualWeighInReturns() {
        val days = (0..20).map { i -> ExpenditureV3.Day(i.toLong(),80.0, if (i==0 || i==20) 80.0 else null,2600.0,false,false) }
        val series = ExpenditureV3.compute(days,2000.0, emptyList(), false, requireRecentWeight = true)
        assertTrue(series.subList(7,20).all { it.paused && it.tdee == series[6].tdee })
        assertFalse(series.last().paused)
    }

    @Test fun calendarsAndLabelsRespectTheDecisionCutoff() {
        val entry = Journal.Entry(1, at = 900)
        assertNull(Form.Model.moodKnownAt(entry,Checkin.Feel(3,3)))
        assertNull(Form.Model.moodKnownAt(entry,Checkin.Feel(3,3,700)))
        assertEquals(1,Form.Model.moodKnownAt(entry,Checkin.Feel(3,3,1000)))
        val days = setOf(0L,28L,56L)
        assertEquals(true,Cycle.phaseForBaseline(76,days))
        assertEquals(Cycle.phaseForBaseline(76,days),Cycle.phaseForBaseline(76,days+84L))
        assertNull(Cycle.phaseForBaseline(92,days))
    }

    @Test fun duplicateSleepIntervalsAndSparseHeartDataDoNotCreateRest() {
        val phase = ActivityKind.SLEEP_ANY
        val deep = ActivityKind.DEEP_SLEEP
        val awake = ActivityKind.AWAKE_SLEEP
        val stages = Bridge.normalizeSleep(listOf(SleepPhase(phase,0,100),SleepPhase(deep,10,60),SleepPhase(awake,30,40),SleepPhase(deep,10,60),SleepPhase(phase,110,120)))
        assertEquals(110L,stages.sumOf { it.end - it.start })
        assertEquals(10L,stages.filter { it.kind == awake }.sumOf { it.end-it.start })
        assertNull(Nights.lowest(List(60) { (it / 2 * 3 * 60_000L) to 50 }))
        assertEquals(50.0,Nights.lowest(List(30) { it * 60_000L to 50 })!!.second,0.0)
    }

    @Test fun aggregationKeepsOriginalsAndRealGpsGapsBreakRecords() {
        val gym = Bridge.Activity("gym",Bridge.Kind.STRENGTH,"gym",1000,100000,Bridge.Source.GYM,gymId="gym")
        val watch = gym.copy(key="watch",start=1100,source=Bridge.Source.WATCH,gymId=null)
        val short = gym.copy(key="short",start=90000,end=100500,source=Bridge.Source.IMPORT,gymId=null)
        val run = watch.copy(kind=Bridge.Kind.RUN,key="run")
        assertEquals(3,Bridge.reconcile(listOf(gym,watch,short,run,gym.copy())).size)
        val trace = listOf(TrackPoint(0.0,0.0,null,null,null,null,null,null),TrackPoint(600.0,1000.0,null,null,null,null,null,null))
        assertNull(Performance.bestEffort(trace,1000.0))
    }

    @Test fun twoLaterBlocksAndKnownExposureAreNeededForATimingInsight() {
        val end = LocalDate.of(2026,11,2)
        val exposures = (1L..56).associate { k -> end.minusDays(k) to ((k-1)/7%2 == 0L) }
        val sleep = exposures.map { (d,late) -> d.plusDays(1) to if (late) 400.0 else 450.0 }.toMap()
        val input = Insights.Input(end,sleepMin=sleep)
        val effect = Insights.nightTiming(input,exposures,Insights.Type.LATE_CAFFEINE)!!
        assertEquals(-50.0,effect.value,0.0)
        assertNull(Insights.nightTiming(input,exposures.filterValues { it },Insights.Type.LATE_CAFFEINE))
        val reversed = sleep.mapValues { (d,v) -> if (d > end.minusDays(28)) 850.0-v else v }
        assertNull(Insights.nightTiming(input.copy(sleepMin=reversed),exposures,Insights.Type.LATE_CAFFEINE))
    }

    @Test fun setCompletionOnlyBoundsRestWhenTimestampsAreCredible() {
        val set = SetLog("a",completedAt=10000)
        assertEquals(0.5,Progression.restMinutes(set,set.copy(completedAt=40000),120),0.0)
        assertEquals(2.0,Progression.restMinutes(set,set.copy(completedAt=10001),120),0.0)
        assertEquals(2.0,Progression.restMinutes(set,null,120),0.0)
    }
}
