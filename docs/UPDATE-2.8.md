# SuperHealth 2.8 — progression, amis et entraînement

Travail du 2 novembre 2026, après la revue 2.7. Les essais utilisent exclusivement l'installation séparée `app.superhealth.qa`. Les applications personnelles restent conservées. La publication GitHub comprend l'app et la branche serveur ; le déploiement du serveur reste à effectuer sur le NAS.

## Expérience de jeu intégrée

Le dashboard ouvre désormais sur **Mes séries** : illustration de flamme, durée de la série, état de l'objectif, niveau, barre d'XP, activité XP de la semaine, missions et aperçu du classement entre amis. Les boutons Classement et Ajouter un ami sont dans le widget. Les widgets Progression, Classement et Amis restent personnalisables. Une migration ajoute le nouveau widget en tête une seule fois, sans réordonner les anciens widgets ; les constantes et anneaux restent voisins pour les nouveaux profils.

Le classement s'ouvre sur le défi XP hebdomadaire, avec trophée, place réelle, écarts et podium. Les égalités partagent une place. Seules les cartes récentes et de la même semaine sont comparées. Les onglets secondaires proposent l'indice de santé, les activités, courses et PR précis. Il n'existe pas de cohorte globale fictive ni de promotion calculée sur des adversaires inventés.

La page Progression rassemble niveaux, jalons, séries et évolution des performances. Les suivis retenus se règlent indépendamment : nutrition, musculation, course, pas, sommeil, esprit/mobilité. Les séries sportives sont **hebdomadaires**, avec un objectif de séances ou de jours de course : une journée de repos n'interrompt pas une série. La période ouverte n'est pas déclarée perdue avant sa fin. Les sources absentes n'inventent pas de suivi.

Les XP sont calculés sur les suivis qualifiés et les activités enregistrées : nutrition 20, musculation 40, course 30, pas 10, sommeil 10, esprit/mobilité 20 par catégorie et par jour ; PR 10, maximum deux PR récompensés par jour ; plafond global 120 XP/jour. Le niveau suit le seuil SuperHealth `(niveau−1) × (100 + 25 × (niveau−1))`. Ce calcul appartient à SuperHealth. Les XP ne récompensent pas une restriction calorique ni une perte de poids. Les séries affichées suivent les catégories choisies ; les XP décrivent l'ensemble des activités enregistrées.

L'animation de série utilise désormais le fichier **streak animation improved** fourni par l'utilisateur (indoorraccoon, remix d'aristote, CC BY 4.0). Le runtime Rive est local et plafonné à 30 images/s ; son événement de fin fige la flamme encore allumée, sans rejouer sa disparition. Le compteur intégré évite un deuxième grand nombre dans le widget. Le rendu s'arrête hors écran et en arrière-plan. Les badges, trophées et illustrations de niveau viennent de `Asset-free/Achievement/achievement-badges/`, sans personnage ni silhouette Duolingo. Les sources, attributions et empreintes sont dans `assets/game/provenance.json` et accessibles dans Réglages → À propos → Sources.

## Paramètres et parcours guidés

L'ordre principal est **Général → Accueil → Fonctionnalités → Social → Bien-être → Données → Outils → À propos**. Général contient apparence/langue, unités/objectifs, sécurité et paramètres du coach. Accueil contient cartes/widgets, bouton + et activités affichées. Fonctionnalités contient nutrition, musculation, montre et plugins. Social contient groupe d'amis et classement/progression. La montre se désactive indépendamment des activités sans montre. L'import d'activités a une route distincte de la sélection des activités affichées.

Nutrition et Musculation commencent par une carte **Me laisser guider**, suivie de cinq rubriques de réglages ciblés. Le parcours Nutrition demande d'abord le mode (coaché, collaboratif, manuel), puis l'objectif. En manuel sans objectif de poids, il passe directement aux cibles calories/macros et au résumé ; aucune pesée n'est ajoutée. Les autres choix demandent les informations utiles au calcul. Les simulations restent séparées. Le parcours Musculation propose création assistée complète (objectif, rythme, durée, périodisation, répétitions/RIR, priorités musculaires, salle, décharge, expérience), bibliothèque, composition manuelle ou import. Le programme courant n'est pas remplacé sans validation.

Le journal alimentaire retrouve un format latéral : calories à gauche, consommés et restants P/L/G à droite. Toucher le résumé le replie en une ligne ; retoucher le rouvre. Ce choix est mémorisé et réglable dans Nutrition → Journal et repas. Les détails de nutriments restent dans le menu du jour.

## Qualification des séries (progression v2)

L'affichage conserve toujours l'objectif entier. Une semaine de musculation à **3/4 reste incomplète visuellement** ; une fois la semaine terminée, la série est préservée si le nombre de séances terminées atteint le nombre prévu moins une, avec un minimum d'une séance. Deux séances distinctes le même jour comptent comme deux séances, mais les XP musculation restent plafonnés à une attribution par jour. Les nouvelles séances conservent le nombre prévu dans leur historique pour éviter qu'un changement de programme réécrive les anciens objectifs.

Pour la nutrition, une journée terminée est qualifiée entre **90 et 110 %** de sa cible calorique effective (cibles par jour, check-ins et remplacements compris). Pendant la journée ouverte, la coche demande 100 % et au plus 110 % ; la marge basse n'est appliquée qu'à sa clôture. Un journal incomplet, une cible invalide ou une simple saisie de repas ne suffit pas. La marge ne réduit pas le dénominateur affiché. Les changements de calories, de cible et de programme invalident le cache de progression.

Les pas demandent l'objectif du jour ; le sommeil, l'objectif personnel de durée ; esprit/mobilité, dix minutes cumulées ; la course conserve son objectif de jours de course. Les XP et séries utilisent la progression modèle 2 : les anciennes cartes de progression ne sont pas mélangées au nouveau calcul. Le modèle de l'indice santé reste 2, et le transport du relais reste v2, opaque et chiffré.


## Amis et serveur

L'ajout d'amis a sa propre page : scanner, montrer son QR et partager sont visibles immédiatement. Le collage, la proximité et les réglages avancés sont regroupés plus loin. Une invitation compacte contient l'identité et les capacités de connexion, sans la carte santé complète. Les mises à jour de l'identité et des données de santé ont des révisions séparées ; un ancien échange ne rétablit pas une ancienne adresse après révocation.

Le coach IA et le classement sont intégrés à l'app et activés par défaut, avec interrupteurs indépendants dans les paramètres. Désactiver le classement arrête sa découverte et sa synchronisation et masque ses widgets. Désactiver le coach bloque aussi les entrées backend et l'envoi de nouvelles requêtes ; la réactivation n'envoie pas automatiquement une ancienne question en attente.

Le serveur est dans la branche autonome **`server/homeserver`**, worktree frère `SuperHealth-server`. Son [README](https://github.com/MamaLama0/SuperHealth/blob/server/homeserver/README.md) et ses fichiers Compose couvrent TrueNAS SCALE/Community 24.10 ou ultérieur, avec ou sans reverse proxy HTTPS existant. L'ancien exemple v1 en clair est archivé hors du dépôt de l'app ; `tools/friend-relay/README.md` oriente vers cette branche.

Le relais v2 conserve uniquement la dernière carte chiffrée AES-256-GCM par boîte. Les identifiants, capacités de lecture et secrets d'écriture sont aléatoires ; la clé AES est transmise aux amis, jamais au serveur. Le téléphone vérifie le chiffrement, la signature et la révision avant application. L'hébergeur voit nécessairement les connexions réseau en cours ; aucun anonymat réseau absolu n'est promis. Aucun journal d'accès applicatif ni annuaire public n'est fourni.

Le conteneur sans dépendance npm est limité par défaut à 256 Mio et 0,5 cœur, 500 cartes, 96 Kio par carte et 35 jours d'expiration. Les GET inchangés retournent 304 ; les publications identiques sont espacées et les lectures parallèles limitées. Actualisation manuelle, à l'ouverture et 2 ou 4 fois/jour restent facultatives. Google Drive sert uniquement aux sauvegardes privées.

## Autres changements

- **Séance libre** : terminer les exercices actuels ne propose plus automatiquement de finir la séance ; l'ajout progressif d'exercices reste possible. Le bouton Terminer reste explicite.
- **Création de programme** : plages de répétitions et RIR personnalisables par famille de mouvements, dans l'assistant et l'introduction. Les cibles de gainage, durée et exercices sans charge restent adaptées. Champs bornés et validation avant génération ; la décharge garde moins de séries et davantage de RIR.
- **Journal alimentaire** : calories et macros restantes ou dépassements, en plus des quantités consommées et des objectifs.
- **Activités** : page dédiée pour choisir les sections visibles et leur ordre, avec installation des plugins nécessaires.
- **Dashboard** : toucher l'onglet déjà actif conserve l'écran et évite la reconstruction à l'origine du flash blanc.
- **Étirements** : player Media3/HLS, lecture/pause, sauts de dix secondes, vitesse, plein écran, consignes et restauration de la position. Mise en arrière-plan et pause gèlent le minuteur de routine.
- **Garmin Trouver mon téléphone** : alarme deux tons sur haut-parleur, gestion et restauration du volume, durée demandée par la montre, annulation renvoyée à la montre initiatrice. Le format de l'annulation est corroboré par l'analyse statique Garmin Connect fournie.

## Validation et limites

Les preuves finales sont sous `docs/evidence/2026-11-02-final/`, avec les étapes intermédiaires sous `docs/evidence/2026-11-02-v2/` et, pour le player et Garmin, `docs/evidence/2026-11-02/`. Les résultats finaux des tests et empreintes d'APK sont conservés dans leurs JSON de validation.

Suite complète : **2 784 tests, 2 745 réussis, 39 ignorés, aucun échec**. Les builds QA debug, QA optimisée R8 et production optimisée réussissent. Deux captures espacées de la flamme optimisée ont une région de rendu identique après son événement de fin ; elle reste visible sans rejouer sa disparition. L'APK normal non débogable conserve le certificat des versions précédentes ; la version est 2.8, code 31.

Sur les deux téléphones : installation QA indépendante, rendu des écrans, ajout d'amis et échange de cartes entre amis connus. Sur un appareil Android : séance libre avec dernier exercice validé puis ajout d'un second exercice sans popup de fin ; huit pressions répétées sur Accueil sans flash blanc dans la capture ; saisie RIR et validation d'une plage invalide dans l'assistant. Sur un second appareil Android : calories/macros restantes, ordre des activités et lecture HLS réelle, puis player optimisé R8 avec pause, recherche, plein écran, consignes et restauration après arrière-plan, sans crash.

Un véritable aller-retour de chiffrement Android → serveur Node local → second Android valide AES-GCM et signature ; PUT 204, GET 200 et GET conditionnel 304. Les tests HTTP du serveur vérifient aussi propriétaire, révisions, quotas et expiration. Ce test local ne valide pas le routage Internet, HTTPS et les ACL du futur NAS.

Le moteur Docker n'est pas disponible sur cette machine : les fichiers Compose ont été analysés avec deux parseurs YAML, mais leur lancement sur TrueNAS reste à vérifier sur le NAS. Aucune Garmin physique n'est connectée : le volume acoustique et l'écran réel de la montre restent à vérifier. La compatibilité conservée du registre Gadgetbridge et ses tests ne remplace pas des essais de chaque modèle. La sauvegarde Drive réelle reste à vérifier dans un compte connecté, comme indiqué en 2.7.

## Reproduction

Depuis le dépôt de l'app :

```sh
./gradlew :app:assembleSuperhealthDebug :app:testSuperhealthDebugUnitTest -PsuperhealthAppId=app.superhealth.qa --offline
./gradlew :app:assembleSuperhealthPerf --offline
```

Depuis le worktree serveur :

```sh
node --test server.test.mjs
```
