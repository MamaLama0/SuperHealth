package app.superhealth.nutrition

import app.superhealth.nutrition.domain.GoalType
import app.superhealth.nutrition.domain.Senior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeniorTest {
    @Test fun adultUnchanged() {
        assertEquals(1400.0 to null, Senior.limitCut(40, 175.0, 90.0, 2500.0, 1400.0))
    }
    @Test fun transitionCapsAt750() {
        val (k, r) = Senior.limitCut(62, 175.0, 90.0, 2500.0, 1400.0)
        assertEquals(1750.0, k, 0.01); assertEquals(Senior.Reason.CAPPED, r)
    }
    @Test fun olderNotObeseKeepsMaintenance() {
        // 175 cm, 85 kg = IMC 27,8 : pas de perte après 65 ans
        assertEquals(2300.0 to Senior.Reason.NOT_OBESE, Senior.limitCut(70, 175.0, 85.0, 2300.0, 1800.0))
    }
    @Test fun olderObeseCapsAt500AndFloor1200() {
        assertEquals(2000.0, Senior.limitCut(70, 170.0, 100.0, 2500.0, 1500.0).first, 0.01)
        // dépense basse : jamais sous 1 200 kcal
        assertEquals(1200.0, Senior.limitCut(72, 155.0, 80.0, 1600.0, 900.0).first, 0.01)
    }
    @Test fun oldestLosesOnlyFromBmi35() {
        // 155 cm, 80 kg = IMC 33,3 : perte à 72 ans, maintien à 80 ans (Cheng 2016)
        assertEquals(1600.0 to Senior.Reason.NOT_OBESE, Senior.limitCut(80, 155.0, 80.0, 1600.0, 900.0))
        // 155 cm, 90 kg = IMC 37,5 : déficit 500 puis plancher 1 200
        assertEquals(1200.0, Senior.limitCut(80, 155.0, 90.0, 1600.0, 900.0).first, 0.01)
    }
    @Test fun transitionNeedsOverweight() {
        // 175 cm, 72 kg = IMC 23,5 : pas de perte à 62 ans
        assertEquals(2300.0 to Senior.Reason.NOT_OBESE, Senior.limitCut(62, 175.0, 72.0, 2300.0, 1800.0))
        assertEquals(1800.0 to null, Senior.limitCut(40, 175.0, 72.0, 2300.0, 1800.0))
    }
    @Test fun adultUnderweightKeepsMaintenance() {
        // 175 cm, 55 kg = IMC 18 (OMS)
        assertEquals(2000.0 to Senior.Reason.NOT_OBESE, Senior.limitCut(30, 175.0, 55.0, 2000.0, 1600.0))
    }
    @Test fun minTargetByLossAndBmi() {
        // 65–74 ans : 10 % au plus ; 75 ans et plus : 5 % ; jamais sous l'IMC 23
        assertEquals(90.0, Senior.minTargetKg(70, 170.0, 100.0), 0.01)
        assertEquals(95.0, Senior.minTargetKg(78, 170.0, 100.0), 0.01)
        assertEquals(Senior.weightAtBmi(23.0, 170.0), Senior.minTargetKg(70, 170.0, 70.0), 0.01)
    }
    @Test fun suggestions() {
        // 70 ans, IMC 27,8 : maintien ; 75 ans, IMC 21 : prise (GLIM < 22 dès 70 ans) ; adulte : au choix
        assertEquals(GoalType.MAINTENANCE, Senior.suggestedGoal(70, 85.0, 175.0))
        assertEquals(GoalType.BULK, Senior.suggestedGoal(75, 64.3, 175.0))
        assertNull(Senior.suggestedGoal(40, 85.0, 175.0))
        // adulte IMC 35 : 10 % (NHLBI) ; 70 ans IMC 34,6 : 10 % ; 80 ans IMC 37,5 : 5 %
        assertEquals(97.2, Senior.suggestedTargetKg(40, 108.0, 175.0, GoalType.CUT)!!, 0.05)
        assertEquals(90.0, Senior.suggestedTargetKg(70, 100.0, 170.0, GoalType.CUT)!!, 0.05)
        assertEquals(85.5, Senior.suggestedTargetKg(80, 90.0, 155.0, GoalType.CUT)!!, 0.05)
        // 70 ans IMC 31 : 10 % mènerait sous 25 ? non (27,9) → 10 %
        assertEquals(Senior.weightAtBmi(31.0, 170.0) * 0.9, Senior.suggestedTargetKg(70, Senior.weightAtBmi(31.0, 170.0), 170.0, GoalType.CUT)!!, 0.05)
    }
    @Test fun smallCutUntouched() {
        assertNull(Senior.limitCut(70, 170.0, 100.0, 2500.0, 2200.0).second)
    }
    @Test fun maxRate() {
        // 500 kcal/j × 7 / 7700 = 0,455 kg/sem, sur 100 kg = 0,455 %
        assertEquals(0.4545, Senior.maxCutRatePct(70, 100.0)!!, 0.001)
        assertNull(Senior.maxCutRatePct(50, 100.0))
    }
    @Test fun adjustedWeight() {
        // homme 175 cm : idéal 70,6 kg ; 110 kg → 70,6 + 0,25 × 39,4
        assertEquals(80.45, Senior.adjustedKg(110.0, 175.0, false), 0.05)
    }
    @Test fun severeObesityCapsAt1000() {
        // 40 ans, 175 cm, 140 kg = IMC 45,7 : 1 %/sem. = 1 540 kcal/j → 1 000 au plus (NHLBI)
        val (k, r) = Senior.limitCut(40, 175.0, 140.0, 3200.0, 1660.0)
        assertEquals(2200.0, k, 0.01); assertEquals(Senior.Reason.SEVERE, r)
        // IMC 34 : pas de plafond chez l'adulte
        assertEquals(1660.0 to null, Senior.limitCut(40, 175.0, 104.0, 3200.0, 1660.0))
        // 70 ans IMC 45 : le plafond d'âge (500) reste le plus bas
        assertEquals(Senior.Reason.CAPPED, Senior.limitCut(70, 175.0, 140.0, 3000.0, 2000.0).second)
        assertEquals(1000.0 * 7 / 7700 / 140 * 100, Senior.maxCutRatePct(40, 140.0, 175.0)!!, 1e-9)
        assertNull(Senior.maxCutRatePct(40, 90.0, 175.0))
    }
}
