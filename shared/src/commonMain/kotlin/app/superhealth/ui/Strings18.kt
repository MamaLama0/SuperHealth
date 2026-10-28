package app.superhealth.ui

import app.superhealth.platform.*

/** Libellés ajoutés en 1.8 : français → anglais. */
object Strings18 {
    val map: Map<String, String> = hashMapOf(
        // place du « + »
        "Position" to "Position",
        "Gauche" to "Left",
        "Milieu" to "Middle",
        "Droite" to "Right",
        // annuler une suppression du journal
        "Aliment supprimé" to "Food deleted",
        "%1\$s aliments supprimés" to "%1\$s foods deleted",
        // séance vide
        "Aucune série faite" to "No sets done",
        "Fermer la séance" to "Close workout",
        "Ajoutez un premier exercice." to "Add a first exercise.",
        // à propos des mesures
        "Température du poignet la nuit. Seuls ses écarts à votre normale ont un sens." to "Wrist temperature at night. Only its changes from your normal matter.",
        "Temps passé endormi la nuit. La plupart des adultes ont besoin de 7 à 9 h." to "Time asleep at night. Most adults need 7 to 9 h.",
        "Distance parcourue à pied et en courant, mesurée par la montre ou le téléphone." to "Distance walked and run, measured by the watch or the phone.",
        "Minutes passées à bouger à intensité au moins modérée, selon la montre." to "Minutes spent moving at moderate intensity or more, according to the watch.",
        "Votre pouls au fil de la journée, moyenné par tranches de 10 minutes." to "Your heart rate through the day, averaged over 10-minute slots.",
        "Qualité de la nuit (0–100) : celle de la montre, sinon calculée à partir de la durée et des phases." to "Night quality (0–100): the watch's own, otherwise computed from duration and stages.",
        "Réserve d'énergie estimée par la montre (0–100) : elle remonte au repos et baisse avec l'effort et le stress." to "Energy reserve estimated by the watch (0–100): it rises with rest and drops with effort and stress.",
        "Stress estimé par la montre à partir de la variabilité cardiaque, hors activité physique (0–100)." to "Stress estimated by the watch from heart rate variability, outside exercise (0–100).",
        "Oxygène dans le sang mesuré au poignet, surtout la nuit. En général entre 95 et 100 %." to "Blood oxygen measured at the wrist, mostly at night. Usually between 95 and 100 %.",
        "Respirations par minute pendant le sommeil. Comparez-la à votre propre moyenne." to "Breaths per minute during sleep. Compare it to your own average.",
        "Oxygène que vous pouvez utiliser à l'effort maximal (ml/kg/min), estimé par la montre. Plus il est haut, meilleure est la forme cardio." to "Oxygen you can use at maximal effort (ml/kg/min), estimated by the watch. Higher means better cardio fitness.",
        "Vos pesées. L'écran Poids montre la tendance, qui lisse l'eau et les repas." to "Your weigh-ins. The Weight screen shows the trend, which smooths out water and meals.",
        "Masse grasse donnée par une balance ou saisie à la main." to "Body fat from a scale or entered by hand.",
        "Eau bue dans la journée, ajoutée à la main ou par le bouton +." to "Water drunk during the day, added by hand or with the + button.",
        "Glycémie saisie à la main." to "Blood glucose entered by hand.",
        "Température corporelle saisie à la main." to "Body temperature entered by hand.",
    )
}
