package app.superhealth.ui

import app.superhealth.platform.*

/** Libellés ajoutés en 1.6 (repas avec mes aliments, bilan de la semaine, contrôle des mises à jour) : français → anglais. */
object Strings16 {
    val map: Map<String, String> = hashMapOf(
        // repas
        "Avec mes aliments" to "With my foods",
        "Composer avec mes aliments" to "Build with my foods",
        "Composer un repas" to "Build a meal",
        "Chercher un aliment" to "Search a food",
        "Récents" to "Recent",
        "Aucun aliment trouvé." to "No food found.",
        "Calculer les portions" to "Work out portions",
        "Cible : %1\$s" to "Target: %1\$s",
        "100 g" to "100 g",
        "Ces aliments ne suffisent pas pour la cible : portions au plus près." to "These foods can't reach the target: closest portions.",
        // bilan de la semaine
        "Ma semaine" to "My week",
        "7 jours" to "7 days",
        "%1\$s/7 jours notés" to "%1\$s/7 days logged",
        "Calcul…" to "Calculating…",
        "Calories / jour" to "Calories / day",
        "Protéines / jour" to "Protein / day",
        "cible %1\$s %2\$s" to "target %1\$s %2\$s",
        "cible %1\$s g" to "target %1\$s g",
        "Poids tendance" to "Trend weight",
        "sur 7 jours" to "over 7 days",
        "%1\$s t soulevées" to "%1\$s t lifted",
        "%1\$s min de cardio" to "%1\$s min of cardio",
        "par nuit" to "per night",
        "par jour" to "per day",
        "Fais le bilan de ma semaine" to "Review my week",
        // mises à jour
        "téléchargement incomplet, réessayez" to "incomplete download, try again",
        "fichier de mise à jour illisible" to "unreadable update file",
        "ce fichier n'est pas SuperHealth" to "this file is not SuperHealth",
        "signature différente de l'app installée" to "signature differs from the installed app",
        "cette version n'est pas plus récente" to "this version is not newer",
    )
}
