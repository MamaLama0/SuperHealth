package app.superhealth.health.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.superhealth.wellbeing.Form
import app.superhealth.wellbeing.Form.Model.Term
import kotlin.math.ln

class ScoresTest {
    private fun nights(n: Int) = List(n) { i -> Scores.Night(50.0 + (i % 3 - 1) * 4, 55.0 + (i % 2), 0.85, 14.0, null) }

    @Test fun chargeFollowsHrvAgainstBaseline() {
        assertNull(Scores.charge(nights(3), Scores.Night(60.0, 55.0, 0.85, 14.0, null)))
        val neutral = Scores.charge(nights(20), Scores.Night(50.0, 55.5, 0.85, 14.0, null))!!
        assertEquals(58.0, neutral.score.toDouble(), 3.0)       // z ≈ 0 → ancre à 58 %
        val high = Scores.charge(nights(20), Scores.Night(65.0, 54.0, 0.95, 14.0, null))!!.score
        val low = Scores.charge(nights(20), Scores.Night(35.0, 60.0, 0.6, 16.0, null))!!.score
        assertTrue("high=$high low=$low", high > 75 && low < 30)
        assertEquals(2, Scores.band(high)); assertEquals(0, Scores.band(low))
    }

    @Test fun effortEdwardsLogScale() {
        // 60 min à 95 % de la réserve (zone 5) : TRIMP 300 → 100·ln(301)/ln(7201)
        val hr = List(61) { (it * 60L) to 188 }
        val e = Scores.effort(hr, rest = 50.0, max = 195.0)!!
        assertEquals(100 * ln(301.0) / ln(7201.0), e, 0.6)
        assertNull(Scores.effort(hr.take(30), 50.0, 195.0))
        assertEquals(61.0, Scores.zones(hr, 50.0, 195.0)[5].toDouble(), 1.0)
    }

    @Test fun sleepDebtConvergesWithoutCredit() {
        // 7 h chaque nuit pour 8 h de besoin : d = 0,55 (60 + d) → 73 min
        assertEquals(73.3, Scores.sleepDebt(List(14) { 420.0 }, 480.0), 0.5)
        assertEquals(0.0, Scores.sleepDebt(List(14) { 600.0 }, 480.0), 0.0)
    }

    @Test fun hrMaxGulatiForWomen() {
        // Gulati 2010 : 206 − 0,88 × âge ; à 50 ans 162 contre 173 (Tanaka)
        assertEquals(162.0, Scores.hrMax(50, emptyList(), female = true), 1e-9)
        assertEquals(173.0, Scores.hrMax(50, emptyList()), 1e-9)
    }
    @Test fun loadIsExponential() {
        // charge stable : aiguë = chronique ; hausse la dernière semaine : l'aiguë réagit plus que la moyenne glissante
        val flat = List(28) { 50.0 }
        val (a, c) = Scores.load(flat)!!
        assertEquals(50.0, a, 1e-9); assertEquals(50.0, c, 1e-9)
        val rise = List(21) { 40.0 } + List(7) { 80.0 }
        val (a2, c2) = Scores.load(rise)!!
        assertEquals(40 + 40 * (1 - Math.pow(0.75, 7.0)), a2, 1e-6)
        assertTrue(c2 > 40 && c2 < a2)
        assertNull(Scores.load(List(28) { if (it < 15) null else 30.0 }))
    }
    @Test fun hrMaxPrefersObservedWhenHigher() {
        assertEquals(208 - 0.7 * 30, Scores.hrMax(30, emptyList()), 1e-9)
        assertEquals(199.0, Scores.hrMax(30, List(1000) { if (it > 990) 199 else 120 }), 1e-9)
    }

    @Test fun chargeWithoutHrvUsesRestingHrAndSleep() {
        val h = nights(20).map { it.copy(hrv = null) }
        val c = Scores.charge(h, Scores.Night(null, 55.5, 0.85, 14.0, null))!!
        assertTrue(c.withoutHrv)
        assertEquals(58.0, c.score.toDouble(), 3.0)
        assertNull(Scores.charge(h, Scores.Night(null, 55.5, null, null, null)))   // pouls seul : pas assez
        assertTrue(Scores.charge(h, Scores.Night(null, 62.0, 0.6, 14.0, null))!!.score < 30)
    }

    @Test fun restCompositeMatchesNoopWeights() {
        // 7 h endormi / 7 h 30 au lit, 15 % profond, 25 % paradoxal, besoin 8 h, régularité 0,9
        assertEquals(87.42, Scores.rest(420.0, 450.0, 63.0, 105.0, 480.0, 0.9)!!, 0.01)
        // presque pas de profond : terme réparateur divisé par deux au plus
        val noDeep = Scores.rest(420.0, 450.0, 0.0, 168.0, 480.0, 0.9)!!
        assertEquals(87.42 - 0.2 * 80 * 0.5, noDeep, 0.01)
        assertNull(Scores.rest(0.0, 0.0, 0.0, 0.0, 480.0, null))
    }

    @Test fun personalNeedIsUpperQuartileFlooredAtGoal() {
        val n = listOf(400.0, 420.0, 440.0, 460.0, 480.0, 500.0, 520.0)
        assertEquals(490.0, Scores.personalNeed(n, 480.0), 1e-9)
        assertEquals(500.0, Scores.personalNeed(n, 500.0), 1e-9)
        assertEquals(480.0, Scores.personalNeed(n.take(6), 480.0), 1e-9)
        assertEquals(570.0, Scores.personalNeed(List(10) { 700.0 }, 480.0), 1e-9)
    }

    @Test fun fitnessAgeNes2011() {
        assertEquals(40.0, Scores.fitnessAge(40.0, false, 65.0, 5.0), 1e-9)
        assertEquals(40 - 0.155 * 10 / 0.296, Scores.fitnessAge(40.0, false, 55.0, 5.0), 1e-9)
        assertEquals(40 - 0.198 * 2 / 0.247, Scores.fitnessAge(40.0, true, 65.0, 7.0), 1e-9)
        assertEquals(20.0, Scores.fitnessAge(22.0, false, 40.0, 15.0), 1e-9)
        assertEquals(5 * 40.0 / 30, Scores.activityIndex(listOf(40.0, 40.0, 40.0, 40.0, 40.0, 10.0, 0.0)), 1e-9)
        assertEquals(0.0, Scores.activityIndex(listOf(10.0, 20.0)), 1e-9)
        assertEquals(2.5 * 3, Scores.activityIndex(listOf(95.0, 95.0, 95.0)), 1e-9)
    }

    // ---- 2.0 : VFC sur 7 jours, signes de la nuit, régularité ----

    @Test fun hrvTrendZoneFromSevenDayMean() {
        val stable = List(60) { 50.0 + (it % 5 - 2) * 3 }
        val t = Scores.hrvTrend(stable)!!
        assertEquals(0, t.zone)
        assertTrue(t.lowMs < 50 && t.highMs > 49)
        // une semaine nettement plus basse : sous la zone
        assertEquals(-1, Scores.hrvTrend(stable.dropLast(7) + List(7) { 38.0 })!!.zone)
        // une seule nuit basse ne sort pas la semaine de la zone
        assertEquals(0, Scores.hrvTrend(stable.dropLast(1) + 38.0)!!.zone)
        // trop peu d'historique ou de mesures cette semaine
        assertNull(Scores.hrvTrend(stable.takeLast(20)))
        assertNull(Scores.hrvTrend(stable.dropLast(7) + listOf(50.0, 50.0) + List<Double?>(5) { null }))
    }

    @Test fun chargeUsesSamePhaseBaseline() {
        // pouls 5 bpm plus haut en phase lutéale : comparé à la même phase, il n'est plus « haut »
        val phases = List(40) { (it / 7) % 2 == 1 } + true
        val h = phases.dropLast(1).map { l -> Scores.Night(null, if (l) 60.0 else 55.0, 0.85, 14.0, null) }
        val mixed = Scores.charge(h, Scores.Night(null, 60.0, 0.85, 14.0, null))!!
        val matched = Scores.charge(h, Scores.Night(null, 60.0, 0.85, 14.0, null), phases)!!
        assertEquals(true, matched.luteal)
        assertTrue("mixed=${mixed.score} matched=${matched.score}", matched.score > mixed.score + 10)
    }

    @Test fun sriIdenticalDaysIs100() {
        val night = BooleanArray(1440) { it in 660..1110 }    // 23 h → 6 h 30 chaque jour
        assertEquals(100.0, Scores.sri(List(8) { night })!!, 1e-9)
        val shifted = BooleanArray(1440) { it in 780..1230 }  // 2 h plus tard un jour sur deux
        assertTrue(Scores.sri(List(8) { if (it % 2 == 0) night else shifted })!! < 80)
        assertNull(Scores.sri(List(5) { night }))
        assertNull(Scores.sri(listOf(night, null, night, null, night, null, night, null, night)))
    }

    @Test fun bodyAlertNeedsTwoUnexplainedNights() {
        val rhr = app.superhealth.wellbeing.BodyAlert.Sign(Metric.RESTING_HR, 7.0)
        val resp = app.superhealth.wellbeing.BodyAlert.Sign(Metric.RESPIRATORY_RATE, 1.8)
        val hrv = app.superhealth.wellbeing.BodyAlert.Sign(Metric.HRV, -15.0)
        val late = listOf(app.superhealth.wellbeing.BodyAlert.Cause.LATE)

        assertEquals(app.superhealth.wellbeing.BodyAlert.Level.ALERT, app.superhealth.wellbeing.BodyAlert.level(listOf(rhr, resp), emptyList(), listOf(rhr, resp), emptyList()))
        // pouls et VFC expliqués par l'effort en soirée : à surveiller seulement
        assertEquals(app.superhealth.wellbeing.BodyAlert.Level.WATCH, app.superhealth.wellbeing.BodyAlert.level(listOf(rhr, hrv), late, listOf(rhr, hrv), emptyList()))
        // une seule nuit : à surveiller
        assertEquals(app.superhealth.wellbeing.BodyAlert.Level.WATCH, app.superhealth.wellbeing.BodyAlert.level(listOf(rhr, resp), emptyList(), listOf(rhr), emptyList()))
        assertEquals(app.superhealth.wellbeing.BodyAlert.Level.NONE, app.superhealth.wellbeing.BodyAlert.level(listOf(rhr), emptyList(), listOf(rhr, resp), emptyList()))
        // une nuit hors norme et une énergie ressentie basse : alerte
        assertEquals(app.superhealth.wellbeing.BodyAlert.Level.ALERT, app.superhealth.wellbeing.BodyAlert.level(listOf(rhr, resp), emptyList(), emptyList(), emptyList(), feelLow = true))
        val r = app.superhealth.wellbeing.BodyAlert.range(List(10) { 55.0 }, Metric.RESTING_HR)!!
        assertEquals(52.0, r.first, 1e-9); assertEquals(58.0, r.third, 1e-9)
    }

    @Test fun restWordsHideTheNumber() {
        assertEquals(Scores.RestWord.SHORT, Scores.restWord(70.0, 360.0, 480.0))
        assertEquals(Scores.RestWord.RESTFUL, Scores.restWord(80.0, 360.0, 480.0))
        assertEquals(Scores.RestWord.FAIR, Scores.restWord(60.0, 470.0, 480.0))
        assertEquals(Scores.RestWord.RESTLESS, Scores.restWord(50.0, 470.0, 480.0))
    }

    @Test fun adaptiveFormScoreAndVerdict() {
        val m = Form.Model
        val t = Term.entries
        assertEquals(50, m.score(emptyMap(), null, m.PRIOR))
        assertEquals(50, m.score(t.filter { it in m.MEASURED }.associateWith { 0.0 }, 3.0, m.PRIOR))
        val good = m.score(mapOf(t[0] to 1.5, t[1] to 1.0, t[2] to 1.0), 4.5, m.PRIOR)
        val bad = m.score(mapOf(t[0] to -2.0, t[1] to -1.0, t[2] to -1.5), 1.5, m.PRIOR)
        assertTrue("good=$good bad=$bad", good > 80 && bad < 20)
        assertEquals(Form.Verdict.REST, Form.verdict(80, emptyList(), 1.0, overreaching = false, alert = true))
        assertEquals(Form.Verdict.REST, Form.verdict(30, emptyList(), 1.0, false, false))
        assertEquals(Form.Verdict.EASY, Form.verdict(65, emptyList(), 1.6, false, false))
        assertEquals(Form.Verdict.EASY, Form.verdict(65, emptyList(), 1.0, overreaching = true, alert = false))
        assertEquals(Form.Verdict.PUSH, Form.verdict(75, emptyList(), 1.1, false, false))
        assertEquals(Form.Verdict.NORMAL, Form.verdict(75, emptyList(), 1.4, false, false))
        assertEquals(Form.Cause.TRAINING, Form.cause(1.4, emptyMap()))
        assertEquals(Form.Cause.OTHER, Form.cause(1.0, mapOf(t[3] to 1.0)))
    }

    @Test fun adaptiveWeightsLearnWhatPredictsFeeling() {
        val m = Form.Model
        assertEquals(m.PRIOR, m.fit(List(10) { mapOf(Term.HRV to 1.0) to 4.0 }))
        // ressenti qui suit le sommeil et pas la VFC : le poids du Repos monte, celui de la VFC baisse
        val rnd = java.util.Random(7)
        val rows = List(120) {
            val sleep = rnd.nextGaussian(); val hrv = rnd.nextGaussian()
            mapOf(Term.HRV to hrv, Term.RHR to rnd.nextGaussian(), Term.SLEEP to sleep, Term.LOAD to 0.0, Term.MOOD to 0.0) to (3 + sleep).coerceIn(1.0, 5.0)
        }
        val w = m.fit(rows)
        assertTrue("w=$w", w.getValue(Term.SLEEP) > 0.4 && w.getValue(Term.HRV) < m.PRIOR.getValue(Term.HRV))
        assertEquals(1.0, w.values.sum(), 1e-9)
        assertEquals(0.25, w.getValue(Term.FEEL), 1e-9)
    }

    @Test fun sleepGateAcrossMidnight() {
        val B = app.superhealth.wellbeing.Bedtime
        assertNull(B.gate(listOf(1380, 1400, 10)))
        val g = B.gate(listOf(1410, 1430, 0, 20, 1420, 10))!!     // 23:30 → 00:20
        assertEquals(1420.0, g.first.toDouble(), 5.0); assertEquals(5.0, g.second.toDouble(), 5.0)
        val p = app.superhealth.wellbeing.Bedtime.Plan(bedMinute = 21 * 60, wakeMinute = 7 * 60, needMin = 480.0, debtMin = 0.0, efficiency = 0.9, nights = 10, gate = g)
        assertTrue(p.tooEarly)
    }

    @Test fun nightComparedToUsual() {
        val usual = List(20) { i -> Scores.NightParts(440.0 + (i % 5) * 4, 0.9, 0.42, 40.0 + i % 3) }
        assertNull(Scores.compareToUsual(usual[0], usual.take(10)))
        val c = Scores.compareToUsual(Scores.NightParts(360.0, 0.9, 0.30, 90.0), usual)!!
        assertEquals(-1, c[Scores.SleepPart.DURATION]); assertEquals(0, c[Scores.SleepPart.EFFICIENCY])
        assertEquals(-1, c[Scores.SleepPart.RESTORATIVE]); assertEquals(1, c[Scores.SleepPart.AWAKE])
    }

    @Test fun effortTargetAndPerceivedLoad() {
        assertEquals(385.0 / 2, Scores.perceivedTrimp(7, 55.0), 1e-9)
        val past = List(28) { 50.0 }
        assertNull(Form.effortRange(Form.Verdict.NORMAL, List(28) { if (it < 20) null else 50.0 }))
        val (lo, hi) = Form.effortRange(Form.Verdict.NORMAL, past)!!
        assertTrue("lo=$lo hi=$hi", lo < 50 && hi > 50)
        val push = Form.effortRange(Form.Verdict.PUSH, past)!!
        val rest = Form.effortRange(Form.Verdict.REST, past)!!
        assertTrue(push.first > 50 && rest.second < lo)
    }
}
