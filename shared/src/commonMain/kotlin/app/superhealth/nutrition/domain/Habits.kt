package app.superhealth.nutrition.domain

import app.superhealth.platform.*

/**
 * Habitudes (enum Habit observé : foodLogging, weighIn, workout ; HabitStreakType).
 * Série = jours consécutifs remplis jusqu'à aujourd'hui ; un jour d'aujourd'hui encore vide ne casse pas la série.
 * [INFÉRÉ] règle exacte de MacroFactor non décodée (jour de jeûne compté comme journalisé, journal incomplet aussi).
 */
object Habits {
    data class Streak(val current: Int, val longest: Int, val done30: Int)

    fun streak(doneDays: Set<Long>, today: Long): Streak {
        var cur = 0
        var d = if (today in doneDays) today else today - 1
        while (d in doneDays) { cur++; d-- }
        var longest = 0; var run = 0; var prev = Long.MIN_VALUE
        doneDays.filter { it <= today }.sorted().forEach { x ->
            run = if (x == prev + 1) run + 1 else 1
            longest = maxOf(longest, run); prev = x
        }
        return Streak(cur, longest, (today - 29..today).count { it in doneDays })
    }

    /** Lundi de la semaine d'un jour (epochDay ; le 1er janvier 1970 était un jeudi). */
    fun weekStart(day: Long): Long = day - Math.floorMod(day + 3, 7L)

    /**
     * Semaines actives de suite (au moins un jour fait dans la semaine), comme l'habitude d'entraînement de MacroFactor
     * Workouts ; la semaine en cours encore vide ne casse pas la série.
     */
    fun weekStreak(doneDays: Set<Long>, today: Long): Int {
        val weeks = doneDays.filter { it <= today }.map(::weekStart).toSet()
        var w = weekStart(today)
        if (w !in weeks) w -= 7
        var n = 0
        while (w in weeks) { n++; w -= 7 }
        return n
    }

    /** Ratio taille / hauteur et taille / hanches (métriques dérivées waistToHeight, waistToHip). */
    fun waistToHeight(waistCm: Double, heightCm: Double) = waistCm / heightCm
    fun waistToHip(waistCm: Double, hipsCm: Double) = waistCm / hipsCm
}
