package app.superhealth.coach

import java.text.Normalizer
import java.util.Locale

/** Catalogue local de destinations autorisées : le coach peut ouvrir une page, jamais changer un réglage à distance. */
object SettingsHelp {
    data class Destination(val key: String, val label: String, val route: String, val keywords: String)
    val destinations = listOf(
        Destination("home_widgets", "Widgets Android", "widgets", "widget accueil ecran bureau ajouter android social classement progression series quetes"),
        Destination("nutrition_guide", "Guide nutrition", "setup/nutrition", "nutrition assistant guider configuration objectif kcal calories macros jour"),
        Destination("nutrition_program", "Programme nutrition", "program", "kcal calories macros proteines glucides lipides par jour lundi manuel collaboratif jours hauts repartition depense fixe dynamique metabolisme bmr tdee plancher"),
        Destination("nutrition_override", "Remplacer mes cibles", "targets/override", "calories macros temporaire remplacement aujourd hui vacances exception"),
        Destination("nutrition_meals", "Journal et repas", "n/settings/daily", "repas horaires check in jour bilan pesee matin journal"),
        Destination("profile", "Profil", "profile", "sexe age taille masse grasse activite experience sportif poids titre badge profil"),
        Destination("nutrition_goal", "Objectif de poids", "goal", "maigrir grossir maintien objectif poids rythme duree cible"),
        Destination("appearance", "Apparence et langue", "settings/look", "theme clair sombre couleur police taille texte francais anglais langue"),
        Destination("units", "Unités et objectifs", "settings/units", "kg livres lbs kcal kilojoules pas objectifs unites"),
        Destination("training_guide", "Guide musculation", "t/setup", "programme assistant musculation creation exercices salle"),
        Destination("training_level", "Programme et expérience", "t/settings/program", "niveau debutant intermediaire avance programme bibliotheque"),
        Destination("training_equipment", "Salles et matériel", "t/settings/gyms", "materiel salle haltere barre machine disque increment charge equipement"),
        Destination("training_progression", "Progression et séries", "t/settings/progression", "smart progression pre remplissage reps charge poids ajustement"),
        Destination("training_rest", "Échauffements et repos", "t/settings/session", "repos minuteur son vibration echauffement series warm up"),
        Destination("training_warmups", "Personnaliser le schéma", "t/warmups", "echauffement pourcentage repetition schema serie"),
        Destination("training_data", "Données musculation", "t/settings/data", "import exporter programme workouts sauvegarde restaurer"),
        Destination("activities_settings", "Sections de l'onglet Activités", "settings/activities", "activite meditation etirement course musculation ordre section"),
        Destination("stretch_editor", "Ma séance d'étirements", "stretch/edit", "etirement souplesse mobilite muscles exo exercice remplacer swap personnaliser seance rapide"),
        Destination("stretch_library", "Bibliothèque d'étirements", "stretch/all", "etirement recherche exercice favoris bibliotheque"),
        Destination("reminders", "Rappels", "settings/reminders", "rappel notification horaire repas poids pesee check in moral humeur journal pas activite coucher"),
        Destination("sleep_settings", "Sommeil", "settings/sleep", "sommeil besoin horaires coucher reveil"),
        Destination("connections", "Health Connect", "settings/hc", "montre health connect google autorisation donnees sante synchro"),
        Destination("sources", "Sources", "settings/sources", "sources donnees montre garmin apple doublons priorite"),
        Destination("security", "Sécurité", "settings/security", "verrou code empreinte biometrie confidentialite capture ecran"),
        Destination("backup", "Sauvegardes", "backup", "sauvegarde restauration drive chiffre fichier export transfert"),
        Destination("plugins", "Plugins", "modules", "plugin module installer meditation etirement ajouter telecharger"),
        Destination("social_profile", "Profil et social", "social", "titre badge profil public amis social"),
        Destination("weekly_quests", "Quêtes de la semaine", "quests", "quete defis semaine recompense xp objectif"),
        Destination("friends", "Ajouter un ami", "friends/add", "ami invitation qr code scanner partager"),
        Destination("league_settings", "Réglages du classement", "league/settings", "serveur personnalise relais synchronisation score classement streak serie suivis partage"),
        Destination("coach_settings", "Réglages du coach", "coach/settings", "ia coach modele cle fournisseur partage memoire confidentialite"),
        Destination("ai_settings", "Réglages IA nutrition", "aisettings", "ia repas photo code barre modele fournisseur cle"),
        Destination("updates", "Mises à jour", "update", "mise a jour version apk nouveautes"),
        Destination("dashboard", "Personnaliser le tableau de bord", "dash/edit", "dashboard widget carte taille classement ordre tableau bord"),
        Destination("quick_actions", "Actions rapides", "plus/edit", "bouton plus raccourci action rapide"),
    )
    private fun normalized(raw: String) = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "").lowercase(Locale.ROOT)
    fun search(query: String): List<Destination> {
        val words = normalized(query).split(Regex("[^a-z0-9]+")).map { if (it.length > 3 && it.endsWith("s")) it.dropLast(1) else it }.filter { it.length >= 2 && it !in setOf("je", "un", "une", "le", "la", "les", "des", "du", "de", "pour", "mes", "mon", "comment", "changer", "ou", "veux", "dans", "avec", "reglage", "parametre", "the", "how", "my", "for") }
        if (words.isEmpty()) return destinations
        return destinations.map { d -> d to words.count { normalized(d.label + " " + d.keywords).contains(it) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
}
