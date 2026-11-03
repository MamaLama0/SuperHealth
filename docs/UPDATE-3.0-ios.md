# SuperHealth iOS 3.0

Portage des interfaces et fonctions de la version Android `v3.0` (`7b304eba69`), à partir de la branche iOS 2.43. Version marketing 3.0, build 33.

- Social : classement clarifié, amis par code ou QR, cartes signées P-256 et relais AES-GCM, profil et décorations.
- Progression : suivis quotidiens ou hebdomadaires, XP, niveaux, badges et quêtes avec récompenses explicites.
- Tableau de bord : cinq cartes sociales adaptées aux quatre formats de l'app, section Sociale dans l'éditeur.
- WidgetKit : six widgets sociaux, chacun en petit, moyen et grand ; catalogue dans les Réglages et le profil Social. Les instantanés ne contiennent aucune clé de partage ou adresse de relais.
- Activités : méditation avec sons locaux AAC et minuteur, mobilité et routines avec lecteur vidéo natif AVPlayer. La mise en arrière-plan met la méditation en pause.
- Musculation : assistant de configuration, import et export d'un programme seul, programmes additionnels, cibles et échauffements.
- Nutrition : configuration guidée, objectifs manuels et bilan hebdomadaire clarifié.
- Sélecteurs Photos/Fichiers, partage iOS, scan QR, trousseau et Apple Santé conservés. Sauvegardes enrichies des nouveaux suivis et de la photo locale.

## Adaptations iOS

Les widgets s'ajoutent depuis la galerie iOS : appui long sur l'écran d'accueil, Modifier, Ajouter un widget, SuperHealth. Les widgets existants (calories, anneaux, poids, séance, sommeil, constantes et journée) restent disponibles.

iOS décide des actualisations en arrière-plan. La fréquence demandée au relais est un intervalle minimum ; l'ouverture de l'app permet une mise à jour immédiate. Les données de montre viennent d'Apple Santé ; la connexion directe Gadgetbridge reste propre à Android.

Le visuel de série utilise l'illustration locale et le compteur, sans moteur Rive Android. Les contrôles vidéo sont ceux d'iOS. Les ambiances synthétiques du module Méditation sont converties en AAC pour AVAudioPlayer.

## Vérifications

- Suite commune : règles de progression et quêtes, qualifications, badges, fusion des amis et protection contre les rejeux, codes amis et chiffrement.
- Interopérabilité : signature EC et message AES-GCM produits par Java/Android, validation dans le code multiplateforme ; aller-retour des clés natives et signatures DER ; limites d'URL et de calendrier ISO.
- Transport : absence de redirection, réponse bornée, intégrité des en-têtes et publications.
- Compilation Kotlin native iPhone depuis Linux ; compilation et exécution sur simulateur Apple Silicon dans la CI macOS.
- Tests WidgetKit : compatibilité des anciens instantanés, six destinations uniques, 36 rendus (six widgets × trois tailles × clair/sombre).

Les deux téléphones disponibles pour cette session sont Android. Aucune validation sur un iPhone physique n'est revendiquée. La distribution iOS reste expérimentale et s'installe avec SideStore + HealthKit ; le binaire est signé lors du sideload.
