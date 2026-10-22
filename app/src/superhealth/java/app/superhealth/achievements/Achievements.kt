package app.superhealth.achievements

import java.time.LocalDate
import kotlin.math.min

/**
 * Succès et progression ludique, facultatifs (Réglages > Tableau de bord). Tout se recalcule à partir des données
 * (journal, pesées, pas, séances, sommeil, hydratation) : rien à tenir à jour à la main, rien de perdu à la restauration.
 *
 * - **XP** par jour : objectifs atteints (pas, journal, calories, protéines, séance, sommeil, pesée, records), 100 au plus.
 * - **Voyage** : l'XP fait avancer un petit bonhomme autour de la Terre, puis une fusée vers la Lune, Mars et au-delà.
 * - **Trophées** : bronze, argent et or pour des totaux et des séries.
 */
object Achievements {
    /** Ce qui s'est passé un jour donné. Null : pas de donnée ce jour-là (rien de compté, rien de pénalisé). */
    data class Day(
        val date: LocalDate,
        val steps: Double? = null, val stepGoal: Int = 0,
        val logged: Boolean = false, val kcal: Double? = null, val kcalTarget: Double? = null,
        val protein: Double? = null, val proteinTarget: Double? = null,
        val workouts: Int = 0, val runKm: Double = 0.0,
        val sleepMin: Double? = null, val sleepNeed: Double = 480.0,
        val weighIn: Boolean = false, val records: Int = 0,
        val waterMl: Double? = null, val waterGoal: Double = 2000.0,
    ) {
        val stepsHit get() = stepGoal > 0 && (steps ?: 0.0) >= stepGoal
        val kcalHit get() = logged && kcal != null && kcalTarget != null && kcalTarget > 0 && kotlin.math.abs(kcal / kcalTarget - 1) <= 0.10
        val proteinHit get() = logged && protein != null && proteinTarget != null && proteinTarget > 0 && protein >= 0.9 * proteinTarget
        val sleepHit get() = sleepMin != null && sleepMin >= sleepNeed
        val waterHit get() = waterMl != null && waterMl >= waterGoal
    }

    fun xp(d: Day): Int {
        var x = 0
        if (d.stepsHit) x += 20
        if (d.stepGoal > 0 && (d.steps ?: 0.0) >= 1.5 * d.stepGoal) x += 10
        if (d.logged) x += 10
        if (d.kcalHit) x += 10
        if (d.proteinHit) x += 10
        if (d.workouts > 0) x += 30
        if (d.sleepHit) x += 10
        if (d.weighIn) x += 5
        if (d.waterHit) x += 5
        x += 15 * min(d.records, 3)
        return min(x, 100)
    }

    /** Niveau : il faut 100 · n XP pour passer du niveau n au suivant (100, 200, 300…). */
    fun level(xp: Int): Triple<Int, Int, Int> {
        var lvl = 1; var left = xp
        while (left >= 100 * lvl) { left -= 100 * lvl; lvl++ }
        return Triple(lvl, left, 100 * lvl)
    }

    // ---- Voyage -------------------------------------------------------------------------

    /** Étape du voyage : l'XP nécessaire pour la finir et la distance réelle qu'elle représente (km). */
    enum class Leg(val label: String, val xp: Int, val km: Double, val rocket: Boolean) {
        EARTH("Le tour de la Terre", 1500, 40_075.0, false),
        MOON("Cap sur la Lune", 2500, 384_400.0, true),
        MARS("Cap sur Mars", 5000, 225_000_000.0, true),
        JUPITER("Cap sur Jupiter", 10000, 628_700_000.0, true),
        SATURN("Cap sur Saturne", 15000, 1_275_000_000.0, true),
        BEYOND("Vers les étoiles", 25000, 4.2 * 9.461e12, true),
    }

    data class Journey(val leg: Leg, val fraction: Float, val done: List<Leg>) {
        val km: Double get() = leg.km * fraction
    }

    fun journey(xp: Int): Journey {
        var left = xp
        val done = ArrayList<Leg>()
        for (l in Leg.entries) {
            if (left < l.xp || l == Leg.BEYOND) return Journey(l, (left.toFloat() / l.xp).coerceIn(0f, 1f), done)
            left -= l.xp; done += l
        }
        return Journey(Leg.BEYOND, 1f, done)
    }

    // ---- Trophées -----------------------------------------------------------------------

    enum class Tier { BRONZE, SILVER, GOLD }

    enum class Trophy(val label: String, val unit: String, val tiers: List<Int>) {
        WALKER("Marcheur", "pas", listOf(100_000, 1_000_000, 5_000_000)),
        STEP_GOAL("Objectif de pas", "jours", listOf(10, 50, 200)),
        STEP_STREAK("Série de pas", "jours d'affilée", listOf(7, 30, 100)),
        LOG_STREAK("Journal assidu", "jours d'affilée", listOf(7, 30, 100)),
        ON_TARGET("Dans la cible", "jours", listOf(10, 50, 150)),
        PROTEIN("Protéines", "jours", listOf(7, 30, 100)),
        WORKOUTS("Séances", "séances", listOf(10, 50, 200)),
        RECORDS("Records", "records", listOf(5, 25, 100)),
        RUNNER("Coureur", "km", listOf(50, 250, 1000)),
        SLEEPER("Bien dormir", "nuits", listOf(7, 30, 100)),
        WEIGH_IN("Pesées", "pesées", listOf(10, 50, 200)),
        WATER("Hydratation", "jours", listOf(7, 30, 100)),
        TRAVELLER("Voyageur", "étapes", listOf(1, 3, 5)),
        LOYAL("Fidèle", "jours suivis", listOf(30, 100, 365)),
        LEVEL("Niveau", "niveau", listOf(5, 10, 20)),
    }

    data class Progress(val trophy: Trophy, val value: Int) {
        val tier: Tier? get() = trophy.tiers.indexOfLast { value >= it }.takeIf { it >= 0 }?.let { Tier.entries[it] }
        val next: Int? get() = trophy.tiers.firstOrNull { value < it }
        val fraction: Float get() = next?.let { n -> val prev = trophy.tiers.lastOrNull { it <= value } ?: 0; (value - prev).toFloat() / (n - prev) } ?: 1f
    }

    data class Summary(val xp: Int, val today: Int, val days: Int, val journey: Journey, val trophies: List<Progress>, val xpByDay: List<Pair<LocalDate, Int>>) {
        val level: Triple<Int, Int, Int> get() = level(xp)
        /** Trophées et paliers obtenus, sous forme de clés « TROPHÉE:palier ». */
        val unlocked: Set<String> get() = trophies.flatMap { p -> p.trophy.tiers.indices.filter { p.value >= p.trophy.tiers[it] }.map { "${p.trophy.name}:$it" } }.toSet()
    }

    /** Série la plus longue de jours consécutifs vérifiant [ok] (les jours sans donnée coupent la série). */
    fun bestStreak(days: List<Day>, ok: (Day) -> Boolean): Int {
        var best = 0; var cur = 0; var prev: LocalDate? = null
        days.sortedBy { it.date }.forEach { d ->
            cur = if (ok(d) && prev != null && d.date == prev!!.plusDays(1) && cur > 0) cur + 1 else if (ok(d)) 1 else 0
            prev = d.date
            best = maxOf(best, cur)
        }
        return best
    }

    fun summarize(days: List<Day>, today: LocalDate = LocalDate.now()): Summary {
        val sorted = days.sortedBy { it.date }
        val byDay = sorted.map { it.date to xp(it) }
        val xp = byDay.sumOf { it.second }
        val j = journey(xp)
        val t = listOf(
            Progress(Trophy.WALKER, sorted.sumOf { it.steps ?: 0.0 }.toInt()),
            Progress(Trophy.STEP_GOAL, sorted.count { it.stepsHit }),
            Progress(Trophy.STEP_STREAK, bestStreak(sorted) { it.stepsHit }),
            Progress(Trophy.LOG_STREAK, bestStreak(sorted) { it.logged }),
            Progress(Trophy.ON_TARGET, sorted.count { it.kcalHit }),
            Progress(Trophy.PROTEIN, sorted.count { it.proteinHit }),
            Progress(Trophy.WORKOUTS, sorted.sumOf { it.workouts }),
            Progress(Trophy.RECORDS, sorted.sumOf { it.records }),
            Progress(Trophy.RUNNER, sorted.sumOf { it.runKm }.toInt()),
            Progress(Trophy.SLEEPER, sorted.count { it.sleepHit }),
            Progress(Trophy.WEIGH_IN, sorted.count { it.weighIn }),
            Progress(Trophy.WATER, sorted.count { it.waterHit }),
            Progress(Trophy.TRAVELLER, j.done.size),
            Progress(Trophy.LOYAL, sorted.count { it.logged || it.steps != null || it.workouts > 0 }),
            Progress(Trophy.LEVEL, level(xp).first),
        )
        return Summary(xp, byDay.lastOrNull { it.first == today }?.second ?: 0, sorted.size, j, t, byDay)
    }
}
