package app.superhealth.nutrition.domain

import app.superhealth.platform.*

/** Bilan énergétique : apports journalisés moins dépense estimée, jour par jour (déficit < 0 < surplus). */
object Balance {
    /** Écart de chaque jour des [n] derniers ; null si le jour n'est pas journalisé (un jeûne compte 0 kcal). */
    fun days(days: List<DayData>, tdee: (Long) -> Double, today: Long, n: Int = 7): List<Pair<Long, Double?>> {
        val byDay = days.associateBy { it.epochDay }
        return (today - n + 1..today).map { d ->
            val day = byDay[d]
            val intake = when {
                day == null -> null
                day.fasted -> day.intakeKcal ?: 0.0
                else -> day.intakeKcal
            }
            d to intake?.let { it - tdee(d) }
        }
    }

    /** Moyenne des jours journalisés, null s'il n'y en a aucun. */
    fun average(days: List<Pair<Long, Double?>>): Double? = days.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.average()
}
