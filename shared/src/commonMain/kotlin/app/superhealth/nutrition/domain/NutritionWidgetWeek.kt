package app.superhealth.nutrition.domain

/** Fenêtre glissante de sept jours. Une absence de saisie reste distincte d'une vraie valeur nulle. */
data class NutritionWidgetWeek(val days: List<Day>) {
    data class Day(val epochDay: Long, val eaten: Macros?, val target: Macros)
    val loggedDays get() = days.count { it.eaten != null }
    val average: Macros? get() = average(days.mapNotNull { it.eaten })
    val averageTarget: Macros? get() = average(days.filter { it.eaten != null }.map { it.target })

    companion object {
        fun of(today: Long, eaten: Map<Long, Macros>, target: (Long) -> Macros) = NutritionWidgetWeek(
            (today - 6..today).map { Day(it, eaten[it], target(it)) })

        private fun average(values: List<Macros>): Macros? {
            if (values.isEmpty()) return null
            val sum = values.fold(Macros.ZERO) { a, b -> a + b }
            val n = values.size.toDouble()
            return Macros(sum.kcal / n, sum.protein / n, sum.fat / n, sum.carbs / n)
        }
    }
}
