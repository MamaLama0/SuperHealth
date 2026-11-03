# Randonnée

Le plugin `hiking` ajoute une section à Activités. Son archive de données est embarquée dans l’APK et installable sans réseau depuis Réglages → Plugins. Le moteur et le service GPS restent dans l’application ; les sources de plugins ne fournissent aucun code exécutable.

- Explorer : sélection embarquée de huit itinéraires réels en France, Italie, Allemagne et au Royaume-Uni ; recherche volontaire par lieu dans le monde via Nominatim et Overpass. La couverture dépend des itinéraires de randonnée renseignés dans OpenStreetMap. Les relations discontinues sont exclues plutôt que reliées artificiellement.
- Favoris et bibliothèque : parcours, fond simplifié et données GPS locaux. Le fond simplifié comprend les éléments disponibles dans l’extraction ; ce n’est pas une carte topographique complète. Les cartes régionales Mapsforge `.map` donnent accès à un fond détaillé entièrement hors ligne : sélectionner un dossier dans la section randonnée. Les fichiers sont disponibles dans le [catalogue Mapsforge](https://download.mapsforge.org/maps/v5/). Le dossier et les thèmes sont communs au moteur cartographique Gadgetbridge. Hors couverture, le fond simplifié prend le relais.
- Import/export GPX : points, altitude, horaires et segments séparés. Les documents XML avec déclaration d’entités sont refusés. Une coupure ou une pause ne crée pas de liaison entre deux segments.
- Sortie : localisation Android, service au premier plan, pause/reprise, distance, durée, dénivelé si fourni, position sur le parcours et distance au tracé. Le suivi est explicitement démarré et arrêté par l’utilisateur. Après arrêt du processus, les points enregistrés sont conservés et la reprise demande une action. Il n’y a pas d’instructions de virage calculées ni d’évaluation technique du terrain.
- Sauvegarde chiffrée : bibliothèque, favoris, sorties et session en cours. Les fichiers régionaux `.map`, stockés dans le dossier choisi, restent séparés de la sauvegarde.

Les durées sont estimées sur une vitesse de marche de 4 km/h, augmentées d’une heure par 600 m de montée lorsque l’altitude est disponible. L’absence de dénivelé est affichée explicitement. Aucun tracé GPS n’est publié automatiquement dans le Groupe. Le bouton de partage ouvre un aperçu ; pour une randonnée, l’option de tracé est désactivée par défaut et peut être choisie avant publication.

## Données et références

Cartographie : © les contributeurs OpenStreetMap, [ODbL](https://www.openstreetmap.org/copyright). Le générateur `tools/plugins/build_hiking_catalog.py` conserve les réponses source et peut reconstruire la sélection à partir des réponses déjà enregistrées (`--offline`). Les fonds sont vectoriels ; aucun téléchargement massif des tuiles raster standard OSM n’est effectué, conformément à leur [politique d’usage](https://operations.osmfoundation.org/policies/tiles/).

Références autorisées examinées : AllTrails 26.10.40, Komoot 2026.44.2 et Visorando 3.21.8. L’inventaire REA et l’extraction statique confirment la présence de styles cartographiques, ressources graphiques et des services de parcours. Le paysage décoratif du plugin provient des ressources Visorando fournies par le titulaire des droits ; il ne représente pas la photo d’un itinéraire particulier. Les noms, logos et interfaces des marques ne sont pas repris.

La base SQLite AllTrails embarquée contient des référentiels mais aucun itinéraire ni photo. Les appels de recherche Visorando et de tracés AllTrails sans authentification ont renvoyé HTTP 403 lors des essais. Une route Komoot de collection testée a renvoyé 405. Ces observations ne prouvent pas l’indisponibilité générale de ces services ; elles ne permettent pas de les présenter comme des sources intégrées fonctionnelles. Aucun identifiant d’utilisateur ni secret extrait d’une application n’est intégré dans SuperHealth.

## Commentaires et notifications

Le tiroir du Groupe conserve jusqu’à 200 notifications locales, avec non-lus, ouverture, marquage et effacement. La permission de notifications Android ne conditionne pas cet historique ; les préférences de notifications système continuent de s’appliquer aux alertes Android.

Les réponses sont ordonnées sous leur parent, avec une indentation plafonnée sur les petits écrans. Les parents absents ou supprimés n’empêchent pas la lecture des réponses. Les cycles ne masquent pas de messages.

Pour rendre les commentaires de non-amis visibles sous le post d’un ami, le propriétaire du post relaie les commentaires signés dans son archive chiffrée. Les lecteurs vérifient la signature du propriétaire et celle de l’auteur original. Seuls les commentaires du post concerné et leurs suppressions signées sont relayés : aucune carte santé, clé de lecture ou liste de contacts n’est propagée. Ce mécanisme utilise le serveur social existant et nécessite la synchronisation du propriétaire du post, puis celle du lecteur. Les anciens clients doivent être mis à jour pour lire ce nouveau type d’événement.

## Santé Connect et activités de course

Les courses et courses sur tapis lues dans Santé Connect restent du type course ; les randonnées restent du type randonnée. La lecture des sessions est paginée. La distance et les calories d’une séance sont attribuées à son application d’origine, avec une lecture paginée de repli lorsque l’agrégat n’est pas disponible. La déduplication compare le sport et un recouvrement de plus de la moitié de la séance la plus courte : deux sports différents ou un léger chevauchement ne masquent plus une course.

Les activités importées localement (dont les courses) et les randonnées enregistrées par le plugin sont exportées comme sessions d’exercice, avec distance et calories lorsqu’elles sont présentes et autorisées. Les identifiants client sont stables pour éviter les doublons lors des synchronisations suivantes. Les autorisations d’écriture distance et calories doivent être accordées dans Santé Connect ; une autorisation partielle laisse fonctionner la session seule. Les données d’une autre application lues dans Santé Connect ne sont pas réexportées. La fenêtre de synchronisation reste celle du projet : trente jours, à l’ouverture, manuellement ou par la tâche horaire.

Le tracé d’une activité externe n’est lisible que si Santé Connect le fournit avec les autorisations correspondantes ; l’export ajouté ici concerne les résumés d’activité et leurs mesures, sans envoi automatique du tracé GPS. Voir les [règles Android sur les tracés](https://developer.android.com/health-and-fitness/health-connect/features/exercise-routes). L’affichage ou la reprise des données dans une application Google dépend aussi des accès que celle-ci détient à Santé Connect.

## Badges, quêtes et vie sociale

Une sortie contribue aux récompenses après 10 minutes actives et 500 mètres enregistrés, ou après 30 minutes lorsqu’une source ne fournit pas de distance. Consulter, télécharger, mettre en favori ou importer un itinéraire prévu ne valide pas une sortie. Le carnet local fusionne les sorties du téléphone, de la montre, des imports d’activités et de Santé Connect ; les copies d’une même activité ne comptent qu’une fois. Il conserve des résumés sans coordonnées GPS pour préserver les totaux lorsque la fenêtre de lecture Santé Connect expire.

La randonnée donne au plus 30 XP par jour, à l’intérieur du plafond global de 120 XP. Une série hebdomadaire vise par défaut un jour de randonnée, avec objectif réglable. Quinze nouveaux badges couvrent les sorties (1, 5, 10, 25, 50), les kilomètres cumulés (10, 50, 100, 250, 500) et la montée cumulée lorsqu’elle est disponible (100, 500, 1000, 5000, 10000 m). Cinq paliers de série complètent le parcours. Les titres débloqués et jusqu’à trois badges peuvent être choisis dans le profil partagé.

Le domaine Randonnée permet de générer une quête hebdomadaire adaptée à l’historique : une première sortie, puis au plus deux sorties dans la semaine. Les récompenses restent celles du système existant (80 XP pour la principale, 30 pour la secondaire), réclamables une seule fois. Les quêtes déjà en cours sont conservées lorsque les domaines choisis changent.

Le détail d’une sortie terminée propose « Partager ma randonnée ». Le brouillon contient le sport, la distance, la durée et le dénivelé disponible. Le tracé GPS reste décoché au départ ; une publication n’est envoyée qu’après validation de son aperçu. Les amis peuvent réagir et commenter ; les réponses imbriquées, y compris celles de non-amis du lecteur, suivent le protocole décrit ci-dessus. Les parcours simplement préparés ne deviennent pas des activités accomplies.
