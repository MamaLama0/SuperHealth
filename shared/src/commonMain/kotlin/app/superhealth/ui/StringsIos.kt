package app.superhealth.ui

/** Textes propres à la version iOS (Apple Santé à la place de la montre Gadgetbridge et de Health Connect). */
object StringsIos {
    val map: Map<String, String> = mapOf(
        "Apple Santé" to "Apple Health",
        "Repos terminé" to "Rest over",
        "Série suivante" to "Next set",
        "Votre Apple Watch, ou votre montre via Apple Santé" to "Your Apple Watch, or your watch through Apple Health",
        "Montre et Apple Santé" to "Watch and Apple Health",
        "Données de santé" to "Health data",
        "Pas, cœur, sommeil et séances lus dans Apple Santé" to "Steps, heart, sleep and workouts read from Apple Health",
        "Désactivé : les cartes de la montre disparaissent du tableau de bord." to "Off: watch cards leave the dashboard.",
        "Apple Santé n'est pas disponible sur cet appareil." to "Apple Health is not available on this device.",
        "Sources" to "Sources",
        "Autorisations d'Apple Santé" to "Apple Health permissions",
        "Autoriser l'accès à Apple Santé" to "Allow access to Apple Health",
        "Lecture des mesures, écriture des pesées, repas et séances" to "Reads measurements, writes weigh-ins, meals and workouts",
        "L'Apple Watch écrit directement dans Santé. Pour une autre montre (Garmin, Polar, Withings…), activez dans son app le partage vers Apple Santé. Pour revoir les accès : Réglages > Santé > Accès aux données > SuperHealth." to
            "Apple Watch writes straight to Health. For another watch (Garmin, Polar, Withings…), turn on sharing to Apple Health in its app. To review access: Settings > Health > Data Access > SuperHealth.",
        "Connecté" to "Connected",
        "Apple Santé désactivé" to "Apple Health off",
        "Apple Santé indisponible" to "Apple Health unavailable",
        "Réglages > Santé > Accès aux données > SuperHealth" to "Settings > Health > Data Access > SuperHealth",
        "Apple Watch, ou toute montre dont l'app partage ses données avec Apple Santé (Garmin, Polar, Withings…)." to
            "Apple Watch, or any watch whose app shares its data with Apple Health (Garmin, Polar, Withings…).",
        "Propre à cet iPhone, il reste le même après une réinstallation. Notez-le pour restaurer sur un autre appareil." to
            "Unique to this iPhone, it stays the same after a reinstall. Write it down to restore on another device.",
        "Cette sauvegarde vient d'un autre appareil (Android compris) : saisissez son code." to "This backup comes from another device (Android included): enter its code.",
        "Fichier chiffré : nutrition, musculation, réglages" to "Encrypted file: nutrition, training, settings",
        "iOS" to "iOS",
        "archive invalide" to "invalid archive",
        "Appareil photo indisponible" to "Camera unavailable",
        // Apple Watch
        "Bouger" to "Move", "M'entraîner" to "Exercise", "Me lever" to "Stand",
        "Heures debout" to "Stand hours", "Étages montés" to "Flights climbed", "FC moyenne à la marche" to "Walking heart rate average",
        "Récupération cardiaque" to "Cardio recovery", "Historique de FA" to "AFib history", "Stabilité de la marche" to "Walking steadiness",
        "Vitesse de marche" to "Walking speed", "Lumière du jour" to "Time in daylight", "Pleine conscience" to "Mindfulness",
        "Bruit ambiant" to "Environmental sound", "Troubles respiratoires" to "Breathing disturbances",
        "Heures où vous vous êtes levé et avez bougé au moins une minute (anneau « Me lever » de l'Apple Watch)." to
            "Hours in which you stood and moved for at least a minute (Apple Watch Stand ring).",
        "Pouls moyen pendant vos marches de la journée, calculé par l'Apple Watch. Il baisse souvent quand la forme progresse." to
            "Average heart rate while walking during the day, from Apple Watch. It often drops as fitness improves.",
        "Baisse du pouls une minute après l'arrêt d'un effort intense enregistré avec l'Apple Watch. Plus elle est grande, meilleure est la forme cardiovasculaire." to
            "Drop in heart rate one minute after stopping an intense Apple Watch workout. The bigger, the better your cardio fitness.",
        "Part du temps passé en fibrillation auriculaire chaque semaine, estimée par l'Apple Watch (fonction à activer dans Santé, réservée aux personnes diagnostiquées). Ce n'est pas un avis médical." to
            "Share of time spent in atrial fibrillation each week, estimated by Apple Watch (enable it in Health; for people with a diagnosis). Not medical advice.",
        "Équilibre et régularité de votre pas, estimés par l'iPhone porté en poche : au-dessus de 70 %, le risque de chute est faible." to
            "Balance and regularity of your walk, estimated by the iPhone in your pocket: above 70 %, fall risk is low.",
        "Temps passé à la lumière du jour, mesuré par le capteur de luminosité de l'Apple Watch. Une à deux heures par jour aident le sommeil et l'humeur." to
            "Time spent in daylight, measured by the Apple Watch light sensor. One to two hours a day help sleep and mood.",
        "Minutes de respiration et de réflexion (app Pleine conscience de l'Apple Watch ou autres apps de méditation)." to
            "Breathe and reflect minutes (Apple Watch Mindfulness app or other meditation apps).",
        "Niveau sonore moyen autour de vous, mesuré par le micro de l'Apple Watch. Au-delà de 80 dB pendant des heures, l'audition peut en souffrir." to
            "Average sound level around you, from the Apple Watch microphone. Hours above 80 dB can harm hearing.",
        "Micro-interruptions de la respiration pendant le sommeil, par heure, mesurées par l'Apple Watch. Des valeurs hautes et durables peuvent évoquer une apnée du sommeil : parlez-en à un médecin." to
            "Brief breathing interruptions during sleep, per hour, measured by Apple Watch. Lasting high values can suggest sleep apnea: talk to a doctor.",
        "Santé Apple Watch" to "Apple Watch health", "Signes vitaux de la nuit" to "Overnight vitals", "Signes vitaux" to "Vitals",
        "Signes vitaux de la nuit, cœur, ECG, mobilité, bien-être" to "Overnight vitals, heart, ECG, mobility, wellbeing",
        "Cœur" to "Heart", "Activité et mobilité" to "Activity and mobility", "Bien-être et environnement" to "Wellbeing and environment",
        "Alertes cardiaques et ECG" to "Heart alerts and ECG", "Pouls élevé ou bas, rythme irrégulier, électrocardiogrammes" to "High or low heart rate, irregular rhythm, ECGs",
        "Dans votre plage habituelle" to "Within your typical range", "1 mesure hors de l'habituel" to "1 measurement outside your range",
        "%1\$s mesures hors de l'habituel" to "%1\$s measurements outside your range", "Hors de l'habituel" to "Outside range", "Normal" to "Typical",
        "Portez l'Apple Watch la nuit avec le suivi du sommeil : vos signes vitaux apparaissent après quelques nuits." to
            "Wear Apple Watch at night with sleep tracking: your vitals appear after a few nights.",
        "Les signes vitaux comparent la dernière nuit à vos 28 nuits précédentes (méthode SuperHealth). Ce ne sont pas des diagnostics médicaux." to
            "Vitals compare last night with your previous 28 nights (SuperHealth method). They are not medical diagnoses.",
        "Pouls élevé" to "High heart rate", "Pouls bas" to "Low heart rate", "Rythme irrégulier" to "Irregular rhythm", "ECG" to "ECG",
        "Rythme sinusal" to "Sinus rhythm", "Fibrillation auriculaire" to "Atrial fibrillation", "Non concluant" to "Inconclusive",
        "Non concluant (pouls bas)" to "Inconclusive (low heart rate)", "Non concluant (pouls élevé)" to "Inconclusive (high heart rate)",
        "Non concluant (mauvais enregistrement)" to "Inconclusive (poor recording)", "Non classé" to "Unclassified", "Seuil %1\$s bpm" to "Threshold %1\$s bpm",
        "Aucune alerte ni ECG cette année" to "No alerts or ECGs this year",
        "L'Apple Watch vous prévient d'un pouls anormalement élevé ou bas et d'un rythme irrégulier ; les ECG faits avec l'app ECG apparaissent ici." to
            "Apple Watch warns you about an unusually high or low heart rate and irregular rhythm; ECGs from the ECG app appear here.",
        "Informations reprises d'Apple Santé, à montrer à un médecin en cas de doute : SuperHealth ne pose aucun diagnostic." to
            "Information from Apple Health, to show a doctor if in doubt: SuperHealth makes no diagnosis.",
        "Effort" to "Effort", "Facile" to "Easy", "Modéré" to "Moderate", "Difficile" to "Hard", "Maximal" to "All out", "Estimé par la montre" to "Estimated by the watch",
        "Dynamique de course" to "Running form", "Mesures de la montre" to "Watch measurements", "Puissance" to "Power", "Cadence" to "Cadence",
        "Longueur de foulée" to "Stride length", "Oscillation verticale" to "Vertical oscillation", "Contact au sol" to "Ground contact",

    )
}
