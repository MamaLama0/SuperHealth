package app.superhealth.ui

import app.superhealth.platform.*

/** Libellés ajoutés en 1.4 (objectif poids + durée, import de courses, planificateur de repas, plugins) : français → anglais. */
object Strings14 {
    val map: Map<String, String> = hashMapOf(
        // Questionnaire : poids visé puis durée
        "Quel poids visez-vous ?" to "What weight are you aiming for?",
        "Poids actuel : %1\$s" to "Current weight: %1\$s",
        "Choisissez un poids plus bas que le poids actuel." to "Pick a weight below your current weight.",
        "Choisissez un poids plus haut que le poids actuel." to "Pick a weight above your current weight.",
        "En combien de temps ?" to "Over how long?",
        "Le rythme et votre budget calorique en découlent." to "Your pace and calorie budget follow from it.",
        "%1\$s sem. · %2\$s" to "%1\$s wk · %2\$s",
        // Import de courses
        "Strava, Garmin, Polar, Suunto, Coros, Nike… (GPX, TCX, FIT ou ZIP)" to "Strava, Garmin, Polar, Suunto, Coros, Nike… (GPX, TCX, FIT or ZIP)",
        "Import" to "Import",
        "Supprimer cette activité importée ?" to "Delete this imported activity?",
        // Plugins
        "Coach IA, repas…" to "AI coach, meals…",
        // Planificateur de repas
        "Repas prévus" to "Planned meals",
        "Autres repas" to "Other meals",
        "Retirer" to "Remove",
        "Des repas pour finir la journée pile sur vos cibles." to "Meals to finish the day right on your targets.",
        "Préparation…" to "Preparing…",
        "Proposer mes repas" to "Suggest my meals",
        "Recettes : spoonacular" to "Recipes: spoonacular",
        "%1\$s min" to "%1\$s min",
        "P %1\$s · L %2\$s · G %3\$s" to "P %1\$s · F %2\$s · C %3\$s",
        "Ajouté" to "Added",
        "Ajouter au journal" to "Add to log",
        "%1\$s portion(s)" to "%1\$s serving(s)",
        "portion" to "serving",
        "%1\$s g " to "%1\$s g ",
        "Voir la recette" to "View recipe",
        "Repas composés par votre IA à partir de la base d'aliments." to "Meals composed by your AI from the food database.",
        "Vraies recettes Spoonacular (photo, temps, lien)." to "Real Spoonacular recipes (photo, time, link).",
        "Clé Spoonacular (gratuite, facultative)" to "Spoonacular key (free, optional)",
        "Créer une clé gratuite" to "Get a free key",
        "Aliments à éviter" to "Foods to avoid",
        "ex. champignons, porc" to "e.g. mushrooms, pork",
        "Vos cibles du jour sont déjà atteintes." to "Today's targets are already reached.",
        "Ajoutez une clé Spoonacular (gratuite) ou configurez l'IA (Nutrition > Intelligence artificielle)." to "Add a (free) Spoonacular key or set up AI (Nutrition > Artificial intelligence).",
        "Réponse du modèle illisible." to "Unreadable model answer.",
        "Spoonacular n'a proposé aucun repas." to "Spoonacular suggested no meals.",
        "Clé Spoonacular refusée." to "Spoonacular key rejected.",
        "Quota gratuit de Spoonacular atteint pour aujourd'hui." to "Spoonacular free quota reached for today.",
    )
}
