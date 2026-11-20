package app.superhealth.ui

/** Import Strong / Hevy / FitNotes, export lisible, carte des muscles (récupération, négligés), séries dupliquées, repos sur la montre. */
internal object Strings36 {
    val map = mapOf(
        // Sauvegarde et export
        "Exporter toutes mes données" to "Export all my data",
        "ZIP lisible, non chiffré : JSON, photos, fichiers et tableaux CSV. Restaurable aussi." to "Readable, unencrypted ZIP: JSON, photos, files and CSV tables. Can also be restored.",
        "Ce fichier n'est pas chiffré : il contient vos journaux de santé, photos et séances en clair. Gardez-le en lieu sûr." to "This file is not encrypted: it holds your health logs, photos and workouts in plain form. Keep it somewhere safe.",
        "Données exportées" to "Data exported",
        // Import d'historique
        "Importer un programme ou un historique" to "Import a program or a history",
        "Importer un historique" to "Import a history",
        "Export CSV de Strong, Hevy ou FitNotes ; ajouté sans remplacer vos séances" to "CSV export from Strong, Hevy or FitNotes; added without replacing your workouts",
        "Format non reconnu : export CSV de Strong, Hevy ou FitNotes attendu" to "Unknown format: expected a CSV export from Strong, Hevy or FitNotes",
        "Importer depuis %1\$s" to "Import from %1\$s",
        "Aucune nouvelle séance dans ce fichier." to "No new workouts in this file.",
        "%1\$s séances, %2\$s séries" to "%1\$s workouts, %2\$s sets",
        "%1\$s séances déjà présentes, ignorées" to "%1\$s workouts already present, skipped",
        "%1\$s lignes sans série de musculation, ignorées" to "%1\$s rows without a strength set, skipped",
        "%1\$s exercices introuvables, créés comme exercices personnels" to "%1\$s exercises not found, created as custom exercises",
        "Correspondances" to "Matches",
        "nouvel exercice" to "new exercise",
        "%1\$s séances importées" to "%1\$s workouts imported",
        // Carte des muscles
        "Volume" to "Volume",
        "Récupération" to "Recovery",
        "Négligés" to "Neglected",
        "Ce qui récupère encore des dernières séances" to "What is still recovering from recent workouts",
        "Depuis quand chaque muscle n'a pas travaillé" to "How long since each muscle was trained",
        "Muscles encore chargés" to "Muscles still loaded",
        "Tout est récupéré : aucun muscle chargé par les séances des trois derniers jours." to "Fully recovered: no muscle loaded by the last three days of workouts.",
        "Récupéré à %1\$s %%" to "%1\$s %% recovered",
        "prêt dans ~%1\$s j" to "ready in ~%1\$s d",
        "prêt dans ~%1\$s h" to "ready in ~%1\$s h",
        "Estimation : une série laisse une fatigue qui disparaît en 48 h (petits muscles) ou 72 h (grands muscles). Muscles secondaires : ½ série." to "Estimate: each set leaves fatigue that fades over 48 h (small muscles) or 72 h (large muscles). Secondary muscles: ½ set.",
        "Du plus ancien au plus récent" to "Oldest first",
        "Jamais travaillé" to "Never trained",
        "Il y a %1\$s jours" to "%1\$s days ago",
        "à reprendre" to "time to train it again",
        "Un muscle passe au plus foncé après 14 jours sans série de travail (principale ou secondaire)." to "A muscle turns darkest after 14 days without a working set (primary or secondary).",
        // Séance
        "Série supprimée" to "Set deleted",
        "Dupliquer la série" to "Duplicate set",
        // Montre
        "Fin du repos sur la montre" to "Rest end on the watch",
        "La montre connectée à SuperHealth vibre et affiche la prochaine série" to "The watch connected to SuperHealth vibrates and shows the next set",
        "Via l'app de la montre" to "Through the watch app",
        "Notification relayée par Wear OS, Garmin Connect, Zepp ou Galaxy Wearable" to "Notification relayed by Wear OS, Garmin Connect, Zepp or Galaxy Wearable",
        "Essayer sur la montre" to "Try on the watch",
        "Envoie une fin de repos d'essai" to "Sends a test rest end",
        "Essai : la prochaine série s'affichera ici" to "Test: the next set will show here",
    )
}
