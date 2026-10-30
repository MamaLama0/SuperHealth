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
        // 2.31 : journal du moral (humeur et note, l'année en couleurs)
        "Une note sur la journée…" to "A note about your day…",
        "Effacer ce jour" to "Clear this day",
        "Choisissez une humeur pour écrire une note." to "Pick a mood to write a note.",
        "%1\$s jours notés" to "%1\$s days logged",
        "1 jour noté" to "1 day logged",
        "Notes" to "Notes",
        "Touchez une case pour noter un jour passé." to "Tap a square to log a past day.",
        "moy." to "avg",
        "Humeur, notes et ressenti du matin" to "Mood, notes and morning check-in",
        "Humeur et notes" to "Mood and notes",
        // 2.31 : sécurité (verrouillage par le système)
        "Sécurité" to "Security",
        "Verrouillage par empreinte ou code" to "Locked by fingerprint or code",
        "Verrouillage désactivé" to "Lock off",
        "Verrouiller SuperHealth" to "Lock SuperHealth",
        "Empreinte, visage ou code du téléphone à l'ouverture" to "Fingerprint, face or phone code when opening",
        "Définissez d'abord un verrouillage d'écran dans les réglages du téléphone." to "First set a screen lock in the phone's settings.",
        "Redemander après" to "Ask again after",
        "Aussitôt" to "Immediately",
        "Masquer dans les apps récentes" to "Hide in recent apps",
        "Aperçu caché et captures d'écran bloquées" to "Preview hidden and screenshots blocked",
        "C'est le téléphone qui vérifie : SuperHealth ne voit ni votre empreinte ni votre code." to
            "Your phone does the check: SuperHealth never sees your fingerprint or code.",
        "SuperHealth est verrouillé" to "SuperHealth is locked",
        "Empreinte, visage ou code du téléphone" to "Fingerprint, face or phone code",
        "Déverrouiller" to "Unlock",
        "Déverrouiller SuperHealth" to "Unlock SuperHealth",
        // 2.31 : sécurité sur iPhone
        "Face ID, Touch ID ou code de l'iPhone à l'ouverture" to "Face ID, Touch ID or iPhone passcode when opening",
        "Définissez d'abord un code dans les réglages de l'iPhone." to "First set a passcode in the iPhone's settings.",
        "Masquer dans le sélecteur d'apps" to "Hide in the app switcher",
        "Aperçu caché quand l'app passe en arrière-plan" to "Preview hidden when the app goes to the background",
        "C'est l'iPhone qui vérifie : SuperHealth ne voit ni votre visage ni votre code." to
            "Your iPhone does the check: SuperHealth never sees your face or passcode.",
        "Face ID, Touch ID ou code de l'iPhone" to "Face ID, Touch ID or iPhone passcode",
        "Verrouillage par Face ID ou code" to "Locked by Face ID or passcode",
    )
}
