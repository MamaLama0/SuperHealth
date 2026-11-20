# SuperHealth

Nutrition, musculation et santé dans une app gratuite, sans compte : vos données restent sur le téléphone.

## Installer

**Android** : téléchargez l'APK dans les [versions](https://github.com/MamaLama0/SuperHealth/releases/latest) et
ouvrez-le. Les nouvelles versions sont proposées dans l’app ; Android peut demander de confirmer leur installation.

**iPhone** (expérimental) : mêmes numéros de version, fichiers `.ipa` dans la même [version](https://github.com/MamaLama0/SuperHealth/releases/latest) que l'APK. Installation par SideStore, avec un ordinateur la première fois seulement.

1. iPhone : installez **LocalDevVPN** (App Store), touchez **Connect**.
2. Ordinateur : avec **iloader**, installez `SideStore-HealthKit.ipa` sur l'iPhone branché.
3. iPhone : autorisez SideStore dans les Réglages, connectez-vous.
4. SideStore : **+** > `SuperHealth.ipa`, puis connectez Apple Santé.

Pas de jailbreak, tout se supprime comme une app normale. [Guide détaillé](https://github.com/MamaLama0/SuperHealth/tree/ios#installer-sur-iphone).

## Fonctionnalités

- **Nutrition** : journal (code-barres, photo ou description analysée par IA, recettes), calories ajustées chaque
  semaine, tendance du poids.
- **Musculation** : programmes générés, séances guidées, progression et records.
- **Santé** : montre (pas, cœur, sommeil, VFC…), scores de sommeil, récupération et effort, courses avec tracé.
  Sur Android, une Garmin se connecte sans Garmin Connect (météo et GPS rapide inclus).
- **IA offerte** sans clé, ou la vôtre.
- **Groupe** (plugin) : classement avec podium, amis par code ou QR, progression, séries, quêtes et badges. Fil à gauche du tableau de bord (glisser vers la droite) pour publier photos, vidéos, séances et courses entre amis. **Mes cartes** : cartes Athlète, paquets payés en or, combats entre amis, quêtes et album. Réglages dans Réglages → Groupe et amis.
- **Randonnée** (plugin) : recherche de parcours dans le monde, favoris, import/export GPX, cartes hors ligne et suivi GPS avec pause/reprise. [Guide](docs/HIKING.md).
- **Autres plugins** : Meal Suggestion (planificateur de repas), rappel de médication, méditation, étirements, animations d'exercices. Tous s'installent et se retirent dans Réglages → Fonctionnalités → Plugins ; l'app de base reste légère.
- **Tableau de bord** personnalisable et **29 widgets Android** (nutrition, santé, montre, Groupe et cartes). Ajout depuis Réglages → Écran d'accueil → Widgets Android, ou depuis le menu de votre écran d’accueil.
- **Sauvegardes chiffrées**, français et anglais.

## Publier et retrouver ses amis

Dans le fil à gauche du tableau de bord, choisissez **Créer une publication**, **Partager une séance** ou **Partager une activité**. Choisissez dans votre historique, ajoutez un message ou un média, relisez l’aperçu puis publiez. Un bouton **Partager** est aussi présent dans le détail des séances et activités.

Envoyez votre code depuis **Amis → Ajouter un ami** : quand la personne confirme l’ajout, la connexion se fait dans les deux sens. Les notifications se règlent dans **Général → Notifications**. Elles arrivent à la synchronisation, selon les autorisations et contraintes d’Android.

## Vie privée

Aucun compte requis. Les journaux restent sur le téléphone. Le partage entre amis est facultatif : le relais transporte des cartes et médias chiffrés. Les photos, vidéos et résumés de séance se publient uniquement quand vous le décidez. Les publications d’activité proposent un choix pour inclure le tracé GPS ; ce choix est désactivé par défaut pour les randonnées. La photo de profil peut être partagée sous forme de vignette chiffrée ; ce partage se désactive dans les préférences sociales. L’IA ne reçoit ni votre nom, ni e-mail, ni téléphone, ni position.

## iOS en détail

Données via Apple Santé (Apple Watch ou toute montre qui y écrit). Code sur la branche
[`ios`](https://github.com/MamaLama0/SuperHealth/tree/ios).

## Retours

Tout en bas des réglages, **Donner mon avis** permet de transmettre une note, une idée ou un problème sans compte GitHub. L’envoi nécessite la passerelle privée configurée sur le serveur ; aucun journal de santé n’est joint automatiquement.

## Documentation et contributions

- [Guide de contribution](CONTRIBUTING.md) : branches, compilation, tests et pull requests.
- [Index de la documentation](docs/README.md) : Android, iOS, SideStore, serveur et modules.
- [Issues](https://github.com/MamaLama0/SuperHealth/issues) et [solutions étudiées](docs/ISSUES.md).
- [Notes de version](docs/updates/).

SuperHealth Android s’appuie sur [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge). Son [README d’origine](docs/upstream/README.md) et ses [contributeurs](docs/upstream/Gadgetbridge-CONTRIBUTORS.rst) sont conservés pour l’attribution et la documentation des appareils. Les bugs propres à SuperHealth se signalent dans ce dépôt.
