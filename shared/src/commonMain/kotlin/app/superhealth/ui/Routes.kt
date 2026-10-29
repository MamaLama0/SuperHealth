package app.superhealth.ui

import app.superhealth.platform.*

/**
 * Routes acceptées depuis l'extérieur de l'app (widgets, raccourcis, notification) : l'activité principale est exportée,
 * n'importe quelle app peut donc lui envoyer une route. Seuls ces écrans d'entrée sont ouverts, jamais un écran
 * d'édition ou de suppression désigné par identifiant.
 */
object Routes {
    private val exact = setOf(
        "home", "log", "weight", "settings", "activities",
        "training:startnext", "training:startempty", "training:workout", "training:session",
        // écrans de lecture ouverts par le widget Insights
        "nutrition", "macros", "t/insights",
        // notification de mise à jour
        "coach", "modules", "h/apple", "h/heart",
        // notification du rappel médication
        "meds",
        // 1.8 : rappels et raccourcis du bien-être
        "journal", "cycle", "breathe", "form", "checkin",
        // 2.1 : écran Constantes (widget)
        "h/vitals",
    )
    private val patterns = listOf(Regex("search/[01]/-1"), Regex("h/metric/[A-Z0-9_]{1,32}"), Regex("exercise/[A-Za-z0-9_-]{1,64}"))

    fun external(route: String?): String? = route?.takeIf { r -> r in exact || patterns.any { it.matches(r) } }
}
