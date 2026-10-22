/*  Copyright (C) 2026 SuperHealth

    This file is part of Gadgetbridge (SuperHealth fork), licensed under the GNU AGPL v3 or later.

    Methods follow published literature as documented by the NOOP project (docs/ANALYTICS.md): Task Force 1996 HRV
    (RMSSD), Malik 1989 ectopic rejection, gravity-stillness sleep detection with HR confirmation, percentile-band
    staging rules, 5-minute bin resting HR, Baevsky stress index. Independent implementation on one-minute epochs. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.whoop

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Analyse des secondes WHOOP : minutes (fréquence cardiaque, mouvement, pas, port), détection et stades du sommeil, VFC
 * de nuit, pouls au repos, respiration, température et stress. Fonctions pures, sans base de données ni horloge.
 */
object WhoopAnalysis {
    enum class Stage { WAKE, LIGHT, DEEP, REM }

    /** Une minute. [motion] : part des secondes en mouvement (0..1) ; [steps] : pas de la minute ; [worn] : pouls mesuré. */
    data class Minute(val start: Long, val hr: Int?, val hrSd: Double?, val motion: Double?, val steps: Int, val worn: Boolean,
                      val tempC: Double?, val rr: List<Int>, val resp: List<Int>, val activity: Int?)

    data class Session(val start: Long, val end: Long, val stages: List<Pair<Long, Stage>>, val restingHr: Int?, val hrv: List<Pair<Long, Int>>,
                       val respRate: Double?) {
        val minutes: Int get() = stages.size
        val asleepMin: Int get() = stages.count { it.second != Stage.WAKE }
        val efficiency: Double get() = if (stages.isEmpty()) 0.0 else asleepMin.toDouble() / stages.size
        val avgHrv: Double? get() = hrv.map { it.second.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        fun count(s: Stage) = stages.count { it.second == s }
    }

    data class Result(val minutes: List<Minute>, val sessions: List<Session>, val stress: List<Pair<Long, Int>>)

    // seuils (voir l'en-tête)
    const val STILL_G = 0.01
    const val STILL_WINDOW_MIN = 15
    const val STILL_FRACTION = 0.70
    const val MAX_GAP_MIN = 20
    const val MERGE_MIN = 15
    const val MIN_SLEEP_MIN = 60
    const val HR_SLEEP_MULT = 1.05
    const val HR_SLEEP_MULT_QUIET = 1.30
    const val RR_MIN = 300
    const val RR_MAX = 2000
    const val ECTOPIC = 0.20
    const val MIN_BEATS = 20

    fun analyze(seconds: List<WhoopSecond>): Result {
        val minutes = minutes(seconds)
        val sessions = sleep(minutes)
        val asleep = sessions.flatMap { s -> s.stages.map { it.first } }.toHashSet()
        return Result(minutes, sessions, stress(minutes, asleep))
    }

    // ---- Minutes ------------------------------------------------------------------------

    fun minutes(seconds: List<WhoopSecond>): List<Minute> {
        if (seconds.isEmpty()) return emptyList()
        val out = ArrayList<Minute>()
        var prev: WhoopSecond? = null
        var prevSteps: Int? = null
        seconds.groupBy { it.unix / 60 * 60 }.toSortedMap().forEach { (start, secs) ->
            val hrs = secs.mapNotNull { it.hr }
            var moving = 0; var measured = 0
            for (s in secs) {
                val p = prev
                if (p != null && s.hasGravity && p.hasGravity && s.unix - p.unix <= 5) {
                    val d = sqrt(sq(s.gx!! - p.gx!!) + sq(s.gy!! - p.gy!!) + sq(s.gz!! - p.gz!!))
                    measured++; if (d >= STILL_G) moving++
                }
                prev = s
            }
            // pas : différences du compteur (rebouclage à 65536 ; un saut invraisemblable, changement de source, est ignoré)
            var steps = 0
            for (s in secs) {
                val c = s.stepCounter ?: continue
                prevSteps?.let { p -> val d = (c - p + 65536) % 65536; if (d in 1..400) steps += d }
                prevSteps = c
            }
            val temps = secs.mapNotNull { it.skinTempC?.toDouble() }
            out += Minute(start, hrs.takeIf { it.isNotEmpty() }?.average()?.roundToInt(), sd(hrs.map { it.toDouble() }),
                if (measured >= 10) moving.toDouble() / measured else null, steps.coerceAtMost(300), hrs.isNotEmpty(),
                temps.takeIf { it.isNotEmpty() }?.average(), secs.flatMap { it.rr }, secs.mapNotNull { it.respRaw },
                secs.mapNotNull { it.activity }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key)
        }
        return out
    }

    // ---- Sommeil ------------------------------------------------------------------------

    /**
     * Sommeil : une minute est « calme » quand la fenêtre de 15 min centrée sur elle a au moins 70 % de secondes immobiles ;
     * séquences coupées par un trou de plus de 20 min, épisodes de moins de 15 min absorbés, nuits d'au moins 60 min,
     * confirmées par le pouls (moyenne ≤ 1,05 × médiane du jour, 1,30 × quand la nuit est presque immobile).
     */
    fun sleep(minutes: List<Minute>): List<Session> {
        if (minutes.size < MIN_SLEEP_MIN) return emptyList()
        val n = minutes.size
        val calm = BooleanArray(n)
        for (i in 0 until n) {
            var still = 0.0; var count = 0
            for (j in maxOf(0, i - STILL_WINDOW_MIN / 2)..minOf(n - 1, i + STILL_WINDOW_MIN / 2)) {
                if (abs(minutes[j].start - minutes[i].start) > STILL_WINDOW_MIN / 2 * 60) continue
                minutes[j].motion?.let { still += 1 - it; count++ }
            }
            calm[i] = count >= 5 && still / count >= STILL_FRACTION
        }
        // séquences de même classe
        data class Run(var from: Int, var to: Int, val sleep: Boolean)
        val runs = ArrayList<Run>()
        for (i in 0 until n) {
            val gap = i > 0 && minutes[i].start - minutes[i - 1].start > MAX_GAP_MIN * 60
            val last = runs.lastOrNull()
            if (last == null || gap || last.sleep != calm[i]) runs += Run(i, i, calm[i]) else last.to = i
        }
        // épisodes courts absorbés par leurs voisins (sans franchir un trou)
        var merged = true
        while (merged) {
            merged = false
            for (k in runs.indices) {
                val r = runs[k]
                if (r.to - r.from + 1 >= MERGE_MIN) continue
                val left = runs.getOrNull(k - 1)?.takeIf { minutes[r.from].start - minutes[it.to].start <= MAX_GAP_MIN * 60 }
                val right = runs.getOrNull(k + 1)?.takeIf { minutes[it.from].start - minutes[r.to].start <= MAX_GAP_MIN * 60 }
                val host = left ?: right ?: continue
                if (host === left) left.to = r.to else right!!.from = r.from
                runs.removeAt(k)
                // voisins de même classe fusionnés
                for (m in runs.size - 1 downTo 1) if (runs[m].sleep == runs[m - 1].sleep &&
                    minutes[runs[m].from].start - minutes[runs[m - 1].to].start <= MAX_GAP_MIN * 60) { runs[m - 1].to = runs[m].to; runs.removeAt(m) }
                merged = true
                break
            }
        }
        val dayHr = minutes.mapNotNull { it.hr }
        val medianHr = median(dayHr.map { it.toDouble() })
        return runs.filter { it.sleep && (minutes[it.to].start - minutes[it.from].start) / 60 + 1 >= MIN_SLEEP_MIN }.mapNotNull { r ->
            val slice = minutes.subList(r.from, r.to + 1)
            val hrs = slice.mapNotNull { it.hr }
            if (medianHr != null && dayHr.size >= 30 && hrs.size >= 30) {
                val quiet = slice.count { (it.motion ?: 1.0) < 0.10 }.toDouble() / slice.size >= 0.90
                if (hrs.average() > medianHr * (if (quiet) HR_SLEEP_MULT_QUIET else HR_SLEEP_MULT)) return@mapNotNull null
            }
            session(slice)
        }
    }

    private fun session(slice: List<Minute>): Session {
        val stages = stage(slice)
        return Session(slice.first().start, slice.last().start + 60, slice.map { it.start }.zip(stages), restingHr(slice), hrvWindows(slice), respiration(slice))
    }

    /**
     * Stades par minute, sur des percentiles propres à la nuit (fenêtre glissante de 5 min) :
     * éveil = mouvement soutenu (≥ 15 %) avec cœur activé (ou sans pouls pour en juger) ; profond = immobile, VFC haute
     * (≥ 70e pct) et pouls bas (≤ 25e pct) ; paradoxal = immobile et cœur activé (pouls ≥ 60e pct et pouls variable) ;
     * léger sinon. Puis filtre de mode sur 5 min, pas de paradoxal dans les 15 premières minutes, pas de profond après le
     * premier tiers de la nuit.
     */
    fun stage(slice: List<Minute>): List<Stage> {
        val n = slice.size
        val hr5 = DoubleArray(n) { Double.NaN }; val rm5 = DoubleArray(n) { Double.NaN }; val mv5 = DoubleArray(n) { Double.NaN }; val sd5 = DoubleArray(n) { Double.NaN }
        for (i in 0 until n) {
            val w = slice.subList(maxOf(0, i - 2), minOf(n, i + 3))
            w.mapNotNull { it.hr?.toDouble() }.takeIf { it.size >= 2 }?.let { hr5[i] = it.average(); sd5[i] = sd(it) ?: Double.NaN }
            w.mapNotNull { it.motion }.takeIf { it.isNotEmpty() }?.let { mv5[i] = it.average() }
            rmssd(cleanRr(w.flatMap { it.rr }))?.let { rm5[i] = it }
        }
        val hrP25 = pct(hr5, 25.0); val hrP60 = pct(hr5, 60.0); val hrP75 = pct(hr5, 75.0)
        val rmP70 = pct(rm5, 70.0); val sdP60 = pct(sd5, 60.0)
        val raw = Array(n) { i ->
            val mv = mv5[i]; val hr = hr5[i]
            val cardiacUp = !hr.isNaN() && hrP75 != null && hr >= hrP75
            when {
                !mv.isNaN() && mv >= 0.15 && (hr.isNaN() || cardiacUp) -> Stage.WAKE
                !mv.isNaN() && mv <= 0.10 && rmP70 != null && hrP25 != null && !rm5[i].isNaN() && rm5[i] >= rmP70 && !hr.isNaN() && hr <= hrP25 -> Stage.DEEP
                !mv.isNaN() && mv <= 0.10 && hrP60 != null && sdP60 != null && !hr.isNaN() && hr >= hrP60 && !sd5[i].isNaN() && sd5[i] >= sdP60 -> Stage.REM
                else -> Stage.LIGHT
            }
        }
        val smooth = Array(n) { i ->
            slice.indices.filter { abs(it - i) <= 2 }.map { raw[it] }.groupingBy { it }.eachCount().maxWith(compareBy({ it.value }, { it.key == raw[i] })).key
        }
        val third = n / 3
        for (i in 0 until n) {
            if (smooth[i] == Stage.REM && i < 15) smooth[i] = Stage.LIGHT
            if (smooth[i] == Stage.DEEP && i > third) smooth[i] = Stage.LIGHT
        }
        return smooth.toList()
    }

    /** Pouls au repos : la plus basse moyenne de tranches de 5 min (au moins 5 mesures et 25 bpm). */
    fun restingHr(slice: List<Minute>): Int? {
        val bins = slice.groupBy { (it.start - slice.first().start) / 300 }.values.map { b -> b.mapNotNull { it.hr } }
        val ok = bins.filter { it.size >= 5 && it.average() >= 25 }.map { it.average() }
        return (ok.minOrNull() ?: bins.filter { it.isNotEmpty() }.map { it.average() }.minOrNull())?.roundToInt()
    }

    /** VFC : RMSSD des tranches de 5 min (R-R nettoyés, au moins 20 battements), horodatée au début de la tranche. */
    fun hrvWindows(slice: List<Minute>): List<Pair<Long, Int>> =
        slice.groupBy { (it.start - slice.first().start) / 300 }.values.mapNotNull { b ->
            rmssd(cleanRr(b.flatMap { it.rr }))?.let { b.first().start to it.roundToInt() }
        }

    /**
     * Fréquence respiratoire (WHOOP 4) depuis le canal brut lié à la respiration : tendance retirée (moyenne glissante
     * de 30 s), maxima locaux espacés d'au moins 2 s, intervalles de 1,5 à 12 s, fréquence = 60 / intervalle médian.
     */
    fun respiration(slice: List<Minute>): Double? {
        val rates = slice.chunked(5).mapNotNull { w ->
            val x = w.flatMap { it.resp }.map { it.toDouble() }
            if (x.size < 200) return@mapNotNull null
            val d = DoubleArray(x.size) { i -> x[i] - x.subList(maxOf(0, i - 15), minOf(x.size, i + 16)).average() }
            val peaks = ArrayList<Int>()
            for (i in 1 until d.size - 1) if (d[i] > 0 && d[i] >= d[i - 1] && d[i] > d[i + 1] && (peaks.isEmpty() || i - peaks.last() >= 2)) peaks += i
            val iv = peaks.zipWithNext { a, b -> (b - a).toDouble() }.filter { it in 1.5..12.0 }
            if (iv.size < 10) null else 60.0 / median(iv)!!
        }
        return median(rates)?.takeIf { it in 6.0..30.0 }
    }

    /**
     * Stress (0–100) toutes les 5 min, éveillé et au calme (mouvement < 10 %) : indice de Baevsky des R-R
     * (SI = AMo / (2 · Mo · MxDMn), histogramme de 50 ms), ramené sur 0–100 par une échelle logarithmique de SI 25
     * (aucun stress) à SI 1000 (stress maximal) — échelle propre à SuperHealth.
     */
    fun stress(minutes: List<Minute>, asleep: Set<Long>): List<Pair<Long, Int>> =
        minutes.filter { it.start !in asleep }.groupBy { it.start / 300 * 300 }.mapNotNull { (t, w) ->
            if (w.any { (it.motion ?: 1.0) >= 0.10 }) return@mapNotNull null
            val si = baevsky(cleanRr(w.flatMap { it.rr })) ?: return@mapNotNull null
            t to (100 * ln(si / 25.0) / ln(1000.0 / 25.0)).roundToInt().coerceIn(0, 100)
        }

    fun baevsky(clean: List<Int>): Double? {
        if (clean.size < MIN_BEATS) return null
        val s = clean.map { it / 1000.0 }
        val lo = s.min(); val range = s.max() - lo
        if (range <= 0) return null
        val bins = IntArray((range / 0.05).toInt() + 1)
        s.forEach { bins[((it - lo) / 0.05).toInt().coerceIn(0, bins.lastIndex)]++ }
        val mode = bins.indices.maxBy { bins[it] * 10_000 - it }
        val mo = lo + (mode + 0.5) * 0.05
        return bins[mode].toDouble() / s.size * 100 / (2 * mo * range)
    }

    /**
     * Score de récupération du sommeil (0–100), pondérations de NOOP « Rest » : durée face au besoin (50 %), efficacité
     * (20 %), part réparatrice profond + paradoxal (20 %, pleine à 40 % du sommeil) et régularité de l'endormissement
     * (10 %, pleine à moins de 15 min de l'heure habituelle, nulle à 2 h). Sans nuits précédentes, la régularité est
     * retirée et les poids renormalisés. Échelles des composantes propres à SuperHealth.
     */
    fun restScore(s: Session, needMin: Int = 480, previousOnsets: List<Long> = emptyList()): Int {
        val duration = (s.asleepMin.toDouble() / needMin).coerceAtMost(1.0)
        val restorative = if (s.asleepMin == 0) 0.0 else ((s.count(Stage.DEEP) + s.count(Stage.REM)).toDouble() / s.asleepMin / 0.40).coerceAtMost(1.0)
        var score = 0.5 * duration + 0.2 * s.efficiency + 0.2 * restorative
        var weight = 0.9
        if (previousOnsets.isNotEmpty()) {
            // heure d'endormissement en minutes, ramenée autour de minuit pour comparer 23 h 30 et 0 h 15
            fun clock(t: Long) = ((t / 60 % 1440 + 720) % 1440).toDouble()
            val usual = previousOnsets.map(::clock).average()
            val dev = abs(clock(s.start) - usual)
            score += 0.1 * (1 - ((dev - 15) / 105).coerceIn(0.0, 1.0)); weight = 1.0
        }
        return (100 * score / weight).roundToInt().coerceIn(0, 100)
    }

    // ---- VFC ----------------------------------------------------------------------------

    /** Intervalles gardés : 300–2000 ms, puis rejet des battements à plus de 20 % de la médiane locale (5 battements). */
    fun cleanRr(rr: List<Int>): List<Int> {
        val r = rr.filter { it in RR_MIN..RR_MAX }
        return r.filterIndexed { i, v ->
            val w = r.subList(maxOf(0, i - 2), minOf(r.size, i + 3))
            if (w.size < 3) true else { val m = median(w.map { it.toDouble() })!!; abs(v - m) <= ECTOPIC * m }
        }
    }

    fun rmssd(clean: List<Int>): Double? {
        if (clean.size < MIN_BEATS) return null
        val d = clean.zipWithNext { a, b -> sq((b - a).toDouble()) }
        return sqrt(d.sum() / d.size)
    }

    // ---- Outils -------------------------------------------------------------------------

    private fun sq(x: Float) = (x * x).toDouble()
    private fun sq(x: Double) = x * x
    private fun sd(x: List<Double>): Double? { if (x.size < 2) return null; val m = x.average(); return sqrt(x.sumOf { sq(it - m) } / (x.size - 1)) }
    fun median(x: List<Double>): Double? = x.sorted().let { s -> if (s.isEmpty()) null else if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }
    private fun pct(a: DoubleArray, p: Double): Double? {
        val s = a.filter { !it.isNaN() }.sorted()
        if (s.isEmpty()) return null
        val k = (s.size - 1) * p / 100
        val lo = k.toInt(); val hi = minOf(lo + 1, s.lastIndex)
        return s[lo] + (s[hi] - s[lo]) * (k - lo)
    }
}
