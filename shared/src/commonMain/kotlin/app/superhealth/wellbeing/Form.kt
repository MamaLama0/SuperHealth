package app.superhealth.wellbeing

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Scores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext
import app.superhealth.platform.LocalDate

/**
 * Forme du jour : pousser, faire comme prévu, alléger ou se reposer, en recoupant quatre signaux — la Charge du matin,
 * la nuit rapportée à votre besoin, la charge d'entraînement des 4 dernières semaines et votre humeur du jour. Règle
 * SuperHealth, inspirée de l'entraînement guidé par la VFC (Vesterinen 2016 : séances dures quand la récupération est dans
 * ou au-dessus de la normale, plus légères sinon) et du rapport charge aiguë / chronique (Gabbett 2016, contesté : il ne
 * sert ici qu'à repérer les extrêmes). Sans les quatre, aucun conseil : une recommandation à moitié informée est pire
 * que rien.
 *
 * Signaux facultatifs, quand ils ont quelque chose à dire : surmenage repéré sur la VFC de 3 semaines, alerte des signes
 * de la nuit (impose le repos) et ressenti du matin. Le ressenti pèse autant qu'un signal mesuré : les mesures subjectives
 * suivent la charge d'entraînement mieux que les objectives (Saw et al. 2016), Polar Recovery Pro croise de même VFC et
 * questions de récupération, et adapter l'entraînement à la VFC nocturne et à la récupération perçue a doublé le progrès
 * sur 10 km chez des coureurs amateurs (Nuuttila et al. 2022).
 *
 * Forme adaptative (règle SuperHealth) : le conseil vient d'un score 0–100, somme pondérée des écarts à votre normale
 * (voir [Model]). Les poids de départ suivent la littérature ; dès [Model.MIN_DAYS] matins de ressenti, ceux des mesures
 * s'ajustent à ce qui prédit le mieux votre propre ressenti. Fatigue : attribuée à l'entraînement quand la charge
 * récente est haute, à autre chose sinon (même distinction que Polar Recovery Pro, fatigue d'entraînement ou d'autre origine).
 */
object Form {
    enum class Verdict(val label: String, val short: String) {
        PUSH("Poussez aujourd'hui", "Pousser"),
        NORMAL("Comme prévu", "Normal"),
        EASY("Allégez un peu", "Léger"),
        REST("Repos ou activité douce", "Repos"),
    }

    enum class Kind(val label: String) {
        CHARGE("Charge"), SLEEP("Sommeil"), LOAD("Entraînement"), MOOD("Moral"),
        /** Facultatifs : n'apparaissent que s'ils ont quelque chose à dire. */
        TREND("VFC sur 3 semaines"), BODY("Signes de la nuit"), FEEL("Ressenti"),
    }

    /** Un signal : sa valeur lisible et son sens (−1 fatigue, 0 neutre, +1 prêt). */
    data class Signal(val kind: Kind, val value: String, val level: Int)

    /** [body] : alerte des signes de la nuit, affichée même sans conseil complet. */
    sealed interface Result { val body: BodyAlert.Status? }
    /** [score] : Forme adaptative 0–100 ; [learned] : matins de ressenti appris (0 : poids de départ) ; [cause] : origine de la fatigue. */
    data class Advice(val verdict: Verdict, val signals: List<Signal>, override val body: BodyAlert.Status?,
                      val score: Int = 50, val learned: Int = 0, val cause: Cause? = null) : Result

    enum class Cause(val label: String) { TRAINING("Fatigue liée à l'entraînement"), OTHER("Fatigue sans lien avec l'entraînement") }
    /** Ce qui manque encore pour conseiller (aucun conseil partiel). */
    data class Missing(val what: List<Kind>, override val body: BodyAlert.Status?) : Result

    /** Nuits de référence de la Charge avant de s'y fier pour pousser. */
    const val MIN_CHARGE_NIGHTS = 14
    /** Jours notés dans le journal sur 4 semaines pour connaître votre humeur habituelle. */
    const val MIN_MOOD_DAYS = 7

    suspend fun today(ctx: Context, journalOn: Boolean): Result = withContext(Dispatchers.Default) {
        val today = LocalDate.now()
        val missing = ArrayList<Kind>()

        val charge = runCatching { Scores.chargeOf(ctx, today) }.getOrNull()?.takeIf { it.nights >= MIN_CHARGE_NIGHTS }
        if (charge == null) missing += Kind.CHARGE

        val slept = Bridge.healthValue(ctx, Metric.SLEEP, today)?.takeIf { it > 60 }
        val nights = (60L downTo 1L).count { (Bridge.healthValue(ctx, Metric.SLEEP, today.minusDays(it)) ?: 0.0) > 60 }
        val need = Scores.sleepHabits(ctx).needMin
        val debt = Scores.sleepDebtOf(ctx, today)
        if (slept == null || debt == null || nights < Scores.NEED_MIN_NIGHTS) missing += Kind.SLEEP

        // Effort des 28 jours complets avant aujourd'hui (aujourd'hui n'est pas fini)
        val efforts = coroutineScope {
            (28L downTo 1L).map { d -> async { runCatching { Scores.effortOf(ctx, today.minusDays(d)) }.getOrNull() } }.awaitAll()
        }
        val load = Scores.load(efforts)?.takeIf { it.second > 0 }
        if (load == null) missing += Kind.LOAD

        val entry = if (journalOn) Journal.today() else null
        val base = Journal.moodBaseline(today.toEpochDay())?.takeIf { it.second >= MIN_MOOD_DAYS }
        if (entry == null || base == null) missing += Kind.MOOD

        val body = runCatching { BodyAlert.today(ctx) }.getOrNull()
        if (missing.isNotEmpty()) return@withContext Missing(missing, body)

        val signals = ArrayList<Signal>()
        signals += Signal(Kind.CHARGE, "${charge!!.score} %", when (Scores.band(charge.score)) { 2 -> 1; 0 -> -1; else -> 0 })
        // surmenage : VFC de la semaine sous la zone, moyenne et variation en baisse depuis 3 semaines
        if (charge.hrv?.overreaching == true) signals += Signal(Kind.TREND, "↓", -1)
        val gap = slept!! - need
        signals += Signal(Kind.SLEEP, hm(slept) + " / " + hm(need), when {
            gap >= -15 && debt!! < 30 -> 1
            gap < -90 || debt!! >= 90 -> -1
            else -> 0
        })
        val acwr = load!!.first / load.second
        signals += Signal(Kind.LOAD, "×" + String.format(app.superhealth.ui.Lang.locale, "%.2f", acwr), when {
            acwr < 0.8 -> 1
            acwr > 1.5 -> -1
            else -> 0
        })
        val mood = entry!!.mood
        signals += Signal(Kind.MOOD, app.superhealth.ui.tr(Journal.MOODS[mood - 1]), when {
            mood >= 4 && mood >= base!!.first -> 1
            mood <= 2 || mood <= base!!.first - 1.5 -> -1
            else -> 0
        })
        // ressenti du matin (facultatif) : moyenne sommeil + énergie
        Checkin.today()?.let { f ->
            val avg = (f.sleep + f.energy) / 2.0
            signals += Signal(Kind.FEEL, app.superhealth.ui.tr(Checkin.ENERGY[f.energy - 1]), if (avg >= 4) 1 else if (avg <= 2) -1 else 0)
        }
        // une alerte des signes de la nuit impose le repos ; à surveiller, elle s'affiche sans compter
        if (body?.level == BodyAlert.Level.ALERT) signals += Signal(Kind.BODY, "!", -1)

        val feel = Checkin.today()
        val x = Model.features(charge, acwr, mood, base!!.first)
        val weights = runCatching { Model.weights(ctx, today) }.getOrNull() ?: (Model.PRIOR to 0)
        val score = Model.score(x, feel?.let { (it.sleep + it.energy) / 2.0 }, weights.first)
        val v = verdict(score, signals, acwr, charge.hrv?.overreaching == true, body?.level == BodyAlert.Level.ALERT)
        Advice(v, signals, body, score, weights.second, if (v == Verdict.EASY || v == Verdict.REST) cause(acwr, x) else null)
    }

    /**
     * Repos : alerte des signes de la nuit, ou score sous 35. Alléger : sous 50, ou à partir de 50 avec un pic de charge
     * (rapport > 1,5) ou un surmenage repéré sur la VFC. Pousser : 70 et plus, aucun signal bas et pas de hausse de charge
     * (rapport ≤ 1,3). Sinon : comme prévu.
     */
    fun verdict(score: Int, signals: List<Signal>, acwr: Double, overreaching: Boolean, alert: Boolean): Verdict = when {
        alert || score < 35 -> Verdict.REST
        score < 50 || acwr > 1.5 || overreaching -> Verdict.EASY
        score >= 70 && acwr <= 1.3 && signals.none { it.level < 0 } -> Verdict.PUSH
        else -> Verdict.NORMAL
    }

    /**
     * Effort à viser aujourd'hui (comme l'objectif d'effort que WHOOP tire de la récupération), dans l'échelle de l'Effort :
     * charge chronique (moyenne des zones × minutes des 28 jours d'avant, au moins 14 mesurés) multipliée selon le conseil —
     * pousser 1,1 à 1,4, comme prévu 0,8 à 1,1, alléger 0,4 à 0,7, repos 0 à 0,3 — puis ramenée à l'Effort 0–100. Au-dessus
     * de 1 la charge monte doucement (rapport aigu / chronique tenu sous 1,3 en une semaine). Règle SuperHealth.
     */
    fun effortRange(v: Verdict, past: List<Double?>): Pair<Double, Double>? {
        val days = past.takeLast(28)
        if (days.count { it != null } < 14) return null
        val chronic = days.map { e -> e?.let { exp(it * kotlin.math.ln(Scores.STRAIN_D) / 100) - 1 } ?: 0.0 }.average()
        if (chronic <= 0) return null
        val (lo, hi) = when (v) { Verdict.PUSH -> 1.1 to 1.4; Verdict.NORMAL -> 0.8 to 1.1; Verdict.EASY -> 0.4 to 0.7; Verdict.REST -> 0.0 to 0.3 }
        return Scores.effortFromTrimp(chronic * lo) to Scores.effortFromTrimp(chronic * hi)
    }

    /** Fatigue d'entraînement : charge aiguë au-dessus de 1,3 fois la chronique ; sinon d'autre origine (sommeil, stress, maladie). */
    fun cause(acwr: Double, x: Map<Model.Term, Double>): Cause =
        if (acwr > 1.3 || (x[Model.Term.LOAD] ?: 0.0) < -1) Cause.TRAINING else Cause.OTHER

    /**
     * Forme adaptative. Termes (écarts à votre normale, bornés à ±3, positif = prêt) : VFC de la semaine en unités de
     * zone normale (Plews), pouls au repos (écart réduit, signe inversé), Repos ((Repos − 85) / 12, comme la Charge),
     * charge d'entraînement ((1,3 − rapport aigu / chronique) / 0,25, plafonnée à +1 : moins s'entraîner ne rend pas plus
     * prêt), moral (note − votre moyenne de 4 semaines) et ressenti ((sommeil + énergie) / 2 − 3).
     *
     * Score = 100 / (1 + e^(−1,7 × Σ wᵢxᵢ / Σ wᵢ)) sur les termes présents (1,7 rapproche la logistique de la loi normale).
     * Poids de départ : VFC 30 %, ressenti 25 %, pouls 15 %, Repos 15 %, charge 10 %, moral 5 % — la VFC en tête comme
     * dans l'entraînement guidé par la VFC (Vesterinen 2016), le ressenti juste derrière (Saw 2016).
     *
     * Apprentissage, dès [MIN_DAYS] matins où ressenti et mesures existent : régression ridge du ressenti sur les termes
     * mesurés (λ = 0,1 × n), coefficients négatifs ramenés à 0, normalisés à la part des mesures (75 %), puis mêlés aux
     * poids de départ en proportion n / (n + 60) — à 60 matins, moitié appris, moitié de départ. Le ressenti garde 25 %.
     */
    object Model {
        enum class Term { HRV, RHR, SLEEP, LOAD, MOOD, FEEL }

        val PRIOR = mapOf(Term.HRV to 0.30, Term.FEEL to 0.25, Term.RHR to 0.15, Term.SLEEP to 0.15, Term.LOAD to 0.10, Term.MOOD to 0.05)
        val MEASURED = listOf(Term.HRV, Term.RHR, Term.SLEEP, Term.LOAD, Term.MOOD)
        const val MIN_DAYS = 30
        private const val SHRINK = 60.0
        private const val HISTORY = 120

        fun features(charge: Scores.Charge, acwr: Double?, mood: Int?, moodBase: Double?): Map<Term, Double> = buildMap {
            charge.drivers["hrv"]?.let { put(Term.HRV, it) }
            charge.drivers["rhr"]?.let { put(Term.RHR, it) }
            charge.drivers["sleep"]?.let { put(Term.SLEEP, it) }
            acwr?.let { put(Term.LOAD, ((1.3 - it) / 0.25).coerceAtMost(1.0)) }
            if (mood != null && moodBase != null) put(Term.MOOD, mood - moodBase)
        }.mapValues { it.value.coerceIn(-3.0, 3.0) }

        fun score(x: Map<Term, Double>, feel: Double?, w: Map<Term, Double>): Int {
            val all = if (feel != null) x + (Term.FEEL to (feel - 3).coerceIn(-2.0, 2.0)) else x
            val sw = all.keys.sumOf { w[it] ?: 0.0 }
            if (sw <= 0) return 50
            val z = all.entries.sumOf { (w[it.key] ?: 0.0) * it.value } / sw
            return (100 / (1 + exp(-1.7 * z))).roundToInt().coerceIn(0, 100)
        }

        /** Poids appris sur [rows] (termes mesurés, ressenti 1–5) ; les poids de départ sous [MIN_DAYS] lignes. */
        fun fit(rows: List<Pair<Map<Term, Double>, Double>>): Map<Term, Double> {
            val n = rows.size
            if (n < MIN_DAYS) return PRIOR
            val k = MEASURED.size
            val a = Array(k) { DoubleArray(k) }; val b = DoubleArray(k)
            for ((x, feel) in rows) {
                val v = MEASURED.map { x[it] ?: 0.0 }
                val y = feel - 3
                for (i in 0 until k) { b[i] += v[i] * y; for (j in 0 until k) a[i][j] += v[i] * v[j] }
            }
            for (i in 0 until k) a[i][i] += 0.1 * n
            val beta = solve(a, b) ?: return PRIOR
            val pos = beta.map { max(0.0, it) }
            val share = 1 - PRIOR.getValue(Term.FEEL)
            val learned = if (pos.sum() <= 0) MEASURED.associateWith { PRIOR.getValue(it) }
                else MEASURED.withIndex().associate { (i, t) -> t to pos[i] / pos.sum() * share }
            val f = n / (n + SHRINK)
            return MEASURED.associateWith { f * learned.getValue(it) + (1 - f) * PRIOR.getValue(it) } + (Term.FEEL to PRIOR.getValue(Term.FEEL))
        }

        /** Élimination de Gauss avec pivot partiel ; null si la matrice est singulière. */
        private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
            val n = b.size
            val m = Array(n) { i -> a[i].copyOf() + b[i] }
            for (c in 0 until n) {
                val p = (c until n).maxBy { abs(m[it][c]) }
                if (abs(m[p][c]) < 1e-9) return null
                val t = m[c]; m[c] = m[p]; m[p] = t
                for (r in 0 until n) if (r != c) {
                    val f = m[r][c] / m[c][c]
                    for (j in c..n) m[r][j] -= f * m[c][j]
                }
            }
            return DoubleArray(n) { m[it][n] / m[it][it] }
        }

        @kotlin.concurrent.Volatile private var cache: Triple<LocalDate, Map<Term, Double>, Int>? = null

        /** Poids du jour et nombre de matins appris (recalculés une fois par jour). */
        suspend fun weights(ctx: Context, today: LocalDate): Pair<Map<Term, Double>, Int> {
            cache?.takeIf { it.first == today }?.let { return it.second to it.third }
            val feels = Checkin.days.value
            val start = today.minusDays(HISTORY.toLong()).toEpochDay()
            val result = if (feels.keys.count { it in start until today.toEpochDay() } < MIN_DAYS) PRIOR to 0 else {
                val charges = Scores.chargeSeries(ctx, today.minusDays(1), HISTORY)
                val efforts = coroutineScope {
                    (HISTORY + 28L downTo 1L).map { d -> async { runCatching { Scores.effortOf(ctx, today.minusDays(d)) }.getOrNull() } }.awaitAll()
                }
                val rows = charges.indices.mapNotNull { i ->
                    val day = today.minusDays((HISTORY - i).toLong())
                    val f = feels[day.toEpochDay()] ?: return@mapNotNull null
                    val c = charges[i] ?: return@mapNotNull null
                    val acwr = Scores.load(efforts.subList(i, i + 28))?.takeIf { it.second > 0 }?.let { it.first / it.second }
                    val e = Journal.days.value[day.toEpochDay()]
                    features(c, acwr, e?.mood, Journal.moodBaseline(day.toEpochDay())?.first) to (f.sleep + f.energy) / 2.0
                }
                fit(rows) to (if (rows.size >= MIN_DAYS) rows.size else 0)
            }
            cache = Triple(today, result.first, result.second)
            return result
        }

        fun invalidate() { cache = null }
    }

    private fun hm(min: Double): String = "${(min / 60).toInt()} h ${(min % 60).toInt().toString().padStart(2, '0')}"
}
