package app.superhealth.nutrition.domain

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

    /** Ratio taille / hauteur et taille / hanches (métriques dérivées waistToHeight, waistToHip). */
    fun waistToHeight(waistCm: Double, heightCm: Double) = waistCm / heightCm
    fun waistToHip(waistCm: Double, hipsCm: Double) = waistCm / hipsCm
}
