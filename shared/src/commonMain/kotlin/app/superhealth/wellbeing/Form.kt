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
import kotlinx.coroutines.withContext
import app.superhealth.platform.LocalDate

/**
 * Forme du jour : pousser, faire comme prévu, alléger ou se reposer, en recoupant quatre signaux — la Charge du matin,
 * la nuit rapportée à votre besoin, la charge d'entraînement des 4 dernières semaines et votre humeur du jour. Règle
 * SuperHealth, inspirée de l'entraînement guidé par la VFC (Vesterinen 2016 : séances dures quand la récupération est dans
 * ou au-dessus de la normale, plus légères sinon) et du rapport charge aiguë / chronique (Gabbett 2016, contesté : il ne
 * sert ici qu'à repérer les extrêmes). Sans les quatre, aucun conseil : une recommandation à moitié informée est pire
 * que rien.
 */
object Form {
    enum class Verdict(val label: String, val short: String) {
        PUSH("Poussez aujourd'hui", "Pousser"),
        NORMAL("Comme prévu", "Normal"),
        EASY("Allégez un peu", "Léger"),
        REST("Repos ou activité douce", "Repos"),
    }

    enum class Kind(val label: String) { CHARGE("Charge"), SLEEP("Sommeil"), LOAD("Entraînement"), MOOD("Moral") }

    /** Un signal : sa valeur lisible et son sens (−1 fatigue, 0 neutre, +1 prêt). */
    data class Signal(val kind: Kind, val value: String, val level: Int)

    sealed interface Result
    data class Advice(val verdict: Verdict, val signals: List<Signal>) : Result
    /** Ce qui manque encore pour conseiller (aucun conseil partiel). */
    data class Missing(val what: List<Kind>) : Result

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

        if (missing.isNotEmpty()) return@withContext Missing(missing)

        val signals = ArrayList<Signal>()
        signals += Signal(Kind.CHARGE, "${charge!!.score} %", when (Scores.band(charge.score)) { 2 -> 1; 0 -> -1; else -> 0 })
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
        Advice(verdict(signals, acwr), signals)
    }

    /**
     * Repos : Charge basse avec un autre signal bas, ou trois signaux bas. Alléger : un seul signal bas. Pousser : aucun
     * signal bas, Charge au vert, au moins un autre signal favorable et pas de pic de charge (rapport ≤ 1,3). Sinon : comme prévu.
     */
    fun verdict(signals: List<Signal>, acwr: Double): Verdict {
        val low = signals.count { it.level < 0 }
        val chargeLevel = signals.first { it.kind == Kind.CHARGE }.level
        return when {
            (chargeLevel < 0 && low >= 2) || low >= 3 -> Verdict.REST
            low >= 1 -> Verdict.EASY
            chargeLevel > 0 && signals.count { it.kind != Kind.CHARGE && it.level > 0 } >= 1 && acwr <= 1.3 -> Verdict.PUSH
            else -> Verdict.NORMAL
        }
    }

    private fun hm(min: Double): String = "${(min / 60).toInt()} h ${(min % 60).toInt().toString().padStart(2, '0')}"
}
