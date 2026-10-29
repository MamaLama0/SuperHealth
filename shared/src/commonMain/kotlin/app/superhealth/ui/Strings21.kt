package app.superhealth.ui

import app.superhealth.platform.*

/** Libellés ajoutés en 2.1 : français → anglais. */
object Strings21 {
    val map: Map<String, String> = hashMapOf(
        // cartes du sommeil et des constantes
        "Horaires de sommeil" to "Sleep schedule",
        "Qualité du sommeil" to "Sleep quality",
        "Cœur la nuit" to "Overnight heart",
        "Signes vitaux de la nuit" to "Overnight vitals",
        "Tendances des constantes" to "Vitals trends",
        "Qualité" to "Quality",
        "Hors de l'habituel" to "Outside usual",
        "14 nuits" to "14 nights",
        "30 j" to "30 d",
        "7 j" to "7 d",
        "90 j" to "90 d",
        "Régularité %1\$s" to "Regularity %1\$s",
        "Coucher et lever habituels" to "Usual bedtime and wake-up",
        "Il faut une nuit avec ses phases (léger, profond, paradoxal)." to "Needs a night with sleep stages (light, deep, REM).",
        "Pas de pouls cette nuit" to "No heart rate last night",
        // phases et parties de Repos
        "Éveil" to "Awake",
        "Paradoxal" to "REM",
        "Profond" to "Deep",
        "Endormi" to "Asleep",
        "Optimal" to "Optimal",
        "À soigner" to "Needs care",
        "hab. %1\$s" to "usual %1\$s",
        "Au plus bas en première moitié de nuit" to "Lowest in the first half of the night",
        "Au plus bas en fin de nuit" to "Lowest late in the night",
        // écran Sommeil
        "Nuit" to "Night",
        "Nuit du %1\$s au %2\$s" to "Night of %1\$s – %2\$s",
        "Aucune nuit enregistrée ce jour-là." to "No night recorded that day.",
        "Aucune nuit enregistrée sur cette période." to "No night recorded in this period.",
        "Repos %1\$s / 100" to "Rest %1\$s / 100",
        "%1\$s au lit" to "%1\$s in bed",
        "Phases" to "Stages",
        "Éveillé" to "Awake",
        "Réveils" to "Awakenings",
        "Besoin %1\$s" to "Need %1\$s",
        "Score de la montre : %1\$s" to "Watch score: %1\$s",
        "bpm au plus bas" to "bpm at lowest",
        "Moyenne endormi" to "Average asleep",
        "Nuits" to "Nights",
        "La plus courte" to "Shortest",
        "La plus longue" to "Longest",
        "Moyenne par nuit" to "Average per night",
        "Pointillés : votre besoin, %1\$s" to "Dotted line: your need, %1\$s",
        "Horaires" to "Schedule",
        "Pointillés : coucher et lever habituels" to "Dotted lines: usual bedtime and wake-up",
        "en moyenne" to "on average",
        // écran Constantes
        "Portez la montre la nuit : vos signes vitaux apparaissent après quelques nuits." to "Wear your watch at night: your vitals show up after a few nights.",
        "Dans votre plage habituelle" to "Within your usual range",
        "1 mesure hors de l'habituel" to "1 measure outside usual",
        "%1\$s mesures hors de l'habituel" to "%1\$s measures outside usual",
        "Dernière nuit" to "Last night",
        "Tendances" to "Trends",
        "Plage habituelle : médiane de vos 28 nuits précédentes ± 2 écarts robustes (méthode SuperHealth). Ce ne sont pas des diagnostics médicaux." to
            "Usual range: median of your previous 28 nights ± 2 robust deviations (SuperHealth method). These are not medical diagnoses.",
        "Habituel %1\$s–%2\$s" to "Usual %1\$s–%2\$s",
        "Moy." to "Avg",
    )
}
