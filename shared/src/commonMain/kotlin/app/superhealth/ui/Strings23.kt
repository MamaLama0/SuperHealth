package app.superhealth.ui

import app.superhealth.platform.*

/** Libellés ajoutés en 2.3 : français → anglais. */
object Strings23 {
    val map: Map<String, String> = hashMapOf(
        // carte Scores du jour
        "À compléter" to "To complete",
        // obésité sévère, masse grasse estimée
        "Déficit limité à 1 000 kcal/j en obésité sévère (NHLBI) : environ 1 kg par semaine au plus." to
            "Deficit limited to 1,000 kcal/day with severe obesity (NHLBI): about 1 kg per week at most.",
        "Perte limitée à %1\$s %%/sem. (déficit de %2\$s kcal/j au plus) pour votre âge et votre IMC." to
            "Loss limited to %1\$s %%/wk (deficit of %2\$s kcal/day at most) for your age and BMI.",
        "Sans réponse : %1\$d %% estimés" to "No answer: %1\$d %% estimated",
        "Obésité sévère : environ 1 kg par semaine au plus, 10 % en 6 mois pour commencer (NHLBI)." to
            "Severe obesity: about 1 kg per week at most, 10 % in 6 months to start (NHLBI).",
        // onglet Activités
        "Séance libre" to "Free workout",
        "Mes programmes" to "My programs",
        "Archivés (%1\$s)" to "Archived (%1\$s)",
        "activités" to "activities",
        "activité" to "activity",
        "Récentes" to "Recent",
        "Vos séances terminées apparaîtront ici." to "Your finished workouts will show up here.",
        "Import d'activités" to "Activity import",
        "Retrouvez vos anciennes séances : elles rejoignent l'onglet Activités, les records et la charge d'entraînement." to
            "Bring back your past workouts: they join the Activities tab, records and training load.",
        "Fichiers GPX, TCX, FIT ou ZIP, historique de la montre" to "GPX, TCX, FIT or ZIP files, watch history",
    )
}
