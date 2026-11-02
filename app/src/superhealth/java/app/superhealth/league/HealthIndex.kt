package app.superhealth.league

/** Indice d'habitudes SuperHealth v2, sur sept jours terminés. Pas une estimation médicale. */
object HealthIndex {
    const val MODEL = 2
    const val MIN_DOMAINS = 3
    val weights = listOf(20, 20, 15, 25, 15, 5)

    fun total(parts: List<Int?>): Int? {
        if (parts.count { it != null } < MIN_DOMAINS) return null
        val used = parts.mapIndexedNotNull { i, p -> p?.let { it.coerceIn(0, 100) to weights[i] } }
        return used.sumOf { it.first * it.second * 10 } / used.sumOf { it.second }
    }

    /** Les jours absents ne deviennent pas des zéros. Au moins quatre observations pour une partie quotidienne. */
    fun daily(values: List<Double?>, target: Double): Int? {
        val observed = values.filterNotNull().filter { it.isFinite() && it >= 0 }
        if (observed.size < 4 || target <= 0) return null
        return (observed.sumOf { (it / target).coerceIn(0.0, 1.0) } / observed.size * 100).toInt()
    }
}
