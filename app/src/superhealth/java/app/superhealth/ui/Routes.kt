package app.superhealth.ui

/**
 * Routes acceptées depuis l'extérieur de l'app (widgets, raccourcis, notification) : l'activité principale est exportée,
 * n'importe quelle app peut donc lui envoyer une route. Seuls ces écrans d'entrée sont ouverts, jamais un écran
 * d'édition ou de suppression désigné par identifiant.
 */
object Routes {
    private val exact = setOf(
        "home", "log", "weight", "settings", "activities",
        "training:startnext", "training:startempty", "training:workout", "training:session",
    )
    private val patterns = listOf(Regex("search/[01]/-1"), Regex("h/metric/[A-Z0-9_]{1,32}"))

    fun external(route: String?): String? = route?.takeIf { r -> r in exact || patterns.any { it.matches(r) } }
}
