# SuperHealth 3.0.1 · le club et les publications entre amis

## Parcours

- **Fil à gauche de l'accueil** : glisser vers la droite depuis le dashboard. Deux petites briques présentent le classement et la progression ; les publications défilent en dessous. Glisser vers la gauche revient au dashboard. Les cartes sociales existantes restent en place. Aucun bouton ne mène au fil. Le réglage **Réglages → Accueil → Fil des amis à gauche du dashboard** le désactive. Une présentation explique le geste au premier lancement de cette version et le questionnaire d'accueil propose aussi ce choix.
- **Ajouter un ami** : révéler son code avec QR, copie et partage, puis saisir le code d'un ami, puis les autres options. Le message partagé explique où saisir le code et inclut l'installation. La durée de 24 h protège l'invitation ; renouveler ce code ne renouvelle pas l'identité, les clés des amitiés ni leurs liens de synchronisation. La révocation avancée des liens reste une action distincte avec confirmation.
- **Publications** : texte, photo, vidéo, carte de séance avec séries, résumé de course ou série d'habitude. L'éditeur permet de relire le contenu et d'ajouter un média. Le partage est également proposé dans les résumés de musculation et d'activité. Les cartes de course n'exportent pas le tracé GPS. Les photos sont orientées selon l'EXIF, redimensionnées et réencodées sans métadonnées ; les vidéos conservent leur fichier d'origine. Lecture de la vidéo dans l'app, suppression de ses publications et nouvelle tentative de synchronisation depuis le fil.
- **Badges** : trois emplacements de taille stable, titre réservé, largeur fixe des actions. La coche d'une catégorie ne change plus la largeur de sa puce. Le choix d'un badge conserve les positions verticales de la liste.
- **Classement** : trophée, podium doré/argent/bronze, rang personnel et prochain ami à rejoindre. Les ex æquo gardent le même rang et la même hauteur de podium. Les cartes du dashboard et les quatre formes du widget Android reprennent le trophée, le rang et l'objectif suivant. Les XP et les performances restent des comparaisons distinctes.
- **Nettoyage** : suppression du raccourci Prénom et photo qui ramenait aux réglages et masquage des anciennes entrées de plugins Amis/Health index. Le partage de performances est proposé uniquement avec un suivi sportif disponible.

## Règles adaptées aux activités

Le questionnaire initialise les domaines de série, les centres d'intérêt des quêtes et les préférences de partage depuis les activités choisies. Le serveur reste facultatif et les publications de médias restent une action explicite.

Les quêtes utilisent l'historique des 14 derniers jours. Le nombre de jours du journal peut aller de 1 à 7 selon sa fréquence réelle ; les séances restent limitées par l'objectif du programme et par le temps restant dans la semaine. Une activité de méditation jamais utilisée ne produit plus une quête de pause. La quête correspondante demande maintenant 10 minutes de méditation ou de mobilité. Les quêtes course et pas ne sont pas générées en l'absence de données santé récentes. Les récompenses déjà récupérées sont conservées lors du retrait d'une ancienne quête ou d'un suivi.

Le **modèle de score 4** conserve les poids relatifs et la fenêtre de sept jours terminés. Il ne compte que les suivis choisis et exige trois domaines renseignés. La musculation utilise le nombre de séances du programme actif. Les domaines absents sont exclus de la moyenne ; les domaines choisis et disponibles peuvent valoir zéro. Les amis peuvent comparer des habitudes différentes : même version, même période, au moins trois domaines par personne et carte de moins de 48 h. Le détail et les comparaisons sportives restent disponibles. Les historiques des modèles précédents et les amitiés sont conservés ; les deux apps doivent être à jour pour le nouveau score.

## Relais et limites

Les publications et leurs descripteurs sont inclus dans les cartes signées et chiffrées existantes. Les pièces jointes sont chiffrées individuellement sur le téléphone, avec des clés aléatoires, une vérification SHA-256 avant déchiffrement et des routes de capacité rattachées à la boîte propriétaire. Le serveur écrit et lit les pièces jointes en flux. Les clients publiés exigent HTTPS ; seul le paquet QA débogable accepte localhost pour les essais par ADB.

Limites livrées : 12 publications par personne sur 34 jours, média de 15 Mio dans l'app, corps de 16 Mio sur le serveur, un envoi serveur à la fois, 512 Mio et 2 000 pièces jointes au total, expiration après 35 jours. Les transferts interrompus ou trop grands ne gardent pas de fichier partiel. Le cache client est borné et les médias sont conservés pour un nouvel essai si la publication de la carte est interrompue.

La branche `server/homeserver` fournit une image amd64/arm64 et garde le volume, les identités et les amitiés existantes. **Le NAS doit recréer son service avec cette image pour accepter les photos et vidéos.** Le déploiement TrueNAS n'a pas pu être exécuté : aucun accès d'administration à ce NAS n'est connecté. Le contrôle HTTP du serveur de distribution depuis ce poste a renvoyé 403. Les vérifications entre téléphones ci-dessous utilisent donc un relais local isolé et ne sont pas une validation de production.

## Vérifications

- Suite Android complète : **2 814 tests, zéro échec, 39 ignorés** ; les **233 tests applicatifs SuperHealth** passent, dont les régressions de score, de fréquence des quêtes, de compatibilité des cartes et de taille/rétention des publications.
- Relais : **12 tests HTTP**, dont transferts chiffrés, secrets propriétaires, reprise après redémarrage, dépassements fragmentés, absence de fichiers partiels et libération du quota par suppression. Le conteneur est construit et démarré en CI avant publication : [validation et image finale](https://github.com/MamaLama0/SuperHealth/actions/runs/41580572311).
- **OnePlus 9 / Android 16** et **Pixel 8 Pro / Android 17**, paquets `app.superhealth.qa` séparés : présentation de mise à jour, swipe vers le fil, désactivation depuis les réglages, sélection des badges sans déplacement vertical, classements avec égalité et rang hors podium.
- Photo **OnePlus → Pixel** et vidéo **Pixel → OnePlus** via relais isolé : octets déchiffrés identiques entre expéditeur et destinataire. La vidéo reçue a été lue dans l'app. Une photo avec orientation EXIF à 90° est reçue en 360 × 640, sans métadonnées, et une erreur préalable de synchronisation d'un ami ne bloque pas la publication. Avec trois suivis choisis, les scores QA sont de 827 et 818 ; avec deux, aucun score global n'est présenté.
- Rendus du widget Classement : quatre tailles × deux apparences (sombre et clair avec grande police) × deux téléphones. Réglages d'apparence et données sociales QA restaurés après les contrôles. Les installations personnelles restent conservées.

[Fil OnePlus](evidence/3.0.1/oneplus-feed-posts.png) · [Fil Pixel](evidence/3.0.1/pixel-feed-posts.png) · [Classement](evidence/3.0.1/oneplus-leaderboard.png) · [Badges](evidence/3.0.1/oneplus-badges-stable.png) · [Lecture vidéo](evidence/3.0.1/oneplus-video-player.png)

Les essais ne couvrent pas une semaine complète de quêtes, tous les codecs vidéo, une acquisition montre en direct ni une mise à jour iOS. La version distribuée reste Android 3.0.1, code 34.
