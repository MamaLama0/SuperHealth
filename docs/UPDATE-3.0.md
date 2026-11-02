# SuperHealth 3.0 · interfaces et widgets Android

## Interfaces et widgets du 3 novembre 2026

- Les cinq cartes sociales du tableau de bord partagent désormais une même présentation : un titre, une valeur et un contexte court. Chaque carte ouvre son propre détail. La carte Séries conserve la flamme animée mais ne contient plus le niveau, le calendrier XP, les quêtes et les boutons de classement. Les cartes Progression et Quêtes portent ces informations dans leurs propres espaces.
- Les formats petit, moyen, bandeau et grand adaptent la quantité de contenu. Le classement montre le score, la place et, si pertinent, le prochain ami à rejoindre. Les ex æquo et la comparaison de périodes/domaines restent conservés. Une récompense de quête à récupérer passe devant une quête déjà terminée ; la prochaine quête incomplète suit.
- Une catégorie **Social** regroupe les cartes et raccourcis dans la personnalisation. La page Social rassemble le profil, les amis, les quêtes, la progression et le catalogue des widgets Android. Ses compteurs Niveau, Amis et Badges ouvrent leur détail.
- **Six nouveaux widgets d’accueil Android** : Classement, Progression, Séries, Quêtes, Amis et Vue d’ensemble sociale. Ils portent le total du catalogue à **21**. Les quatre formes sont redimensionnables ; les widgets sociaux ouvrent leur écran dans l’app. Ils ne montrent aucun nom d’ami sur l’écran d’accueil et ne déclenchent aucune connexion réseau pour leur rendu.
- Le catalogue **Réglages → Accueil → Widgets Android** présente les sections Social, Santé, Nutrition et Musculation. Chaque ajout appelle la confirmation native Android. Si le lanceur ne propose pas l’ajout direct, la page explique comment passer par son sélecteur de widgets.
- Les widgets sont actualisés en quittant l’app, après les mises à jour nutrition/musculation et après une synchronisation sociale planifiée. Le widget Esprit et mobilité rejoint aussi ce rafraîchissement.
- La page Progression présente les suivis à continuer avant les jalons. Leurs raccourcis ouvrent la bonne activité. Les jalons utilisent les seuils du catalogue réel des badges, avec un accès à tous les paliers. Les informations de série et record sont regroupées avec le suivi au lieu d’être répétées dans une seconde liste.
- Les quêtes donnent une action claire pour reprendre un suivi ou récupérer une récompense. Les préférences sont réinitialisées depuis les choix enregistrés à la réouverture ; modifier des préférences existantes affiche « Enregistrer mes choix ». Un échec d’enregistrement reste récupérable dans l’interface.
- Les boutons capsules autorisent deux lignes. La recherche de réglages conserve la requête au retour et propose une remise à zéro quand aucun résultat ne correspond. Les zones tactiles de recherche atteignent 48 dp.

Le script `tools/qa_widgets.py` (Python avec Pillow et ADB) pilote uniquement `app.superhealth.qa`, capture les vrais RemoteViews et les écrans Compose, vérifie les destinations des widgets puis restaure l’apparence QA, le thème Android et la taille de police. Les résultats des deux appareils et les planches de formats sont conservés dans `docs/evidence/3.0-polish/`.

### Vérifications de la refonte

Les **142 scénarios distincts** regroupent 96 rendus de widgets Android (6 familles × 4 formes × 2 apparences × 2 téléphones), 16 dispositions de cartes dans l’app et 30 vérifications de pages/langue. Les 24 actions de widgets grands ont ouvert la bonne destination. La grande police combine un facteur système de 1,25 et la taille « Grande » dans l’app. La confirmation native d’épinglage a été affichée puis annulée. Les versions QA optimisées ont rendu leur tableau de bord après lancement à froid sur les deux appareils.

La validation finale compte **229 tests applicatifs réussis**, une suite complète de **2 810 tests** sans échec (39 ignorés) et **9 tests HTTP** réussis. Les téléphones conservent leurs installations personnelles ; seuls les paquets QA ont été mis à jour.

[Cartes en sombre](evidence/3.0-polish/device-1-dashboard-dark.jpg) · [Cartes en clair et grande police](evidence/3.0-polish/device-2-dashboard-light-large.jpg) · [Six widgets Android et quatre formes](evidence/3.0-polish/device-1-android-light-large.jpg) · [Validation Pixel](evidence/3.0-polish/device-2-android-dark.jpg) · [Confirmation Android](evidence/3.0-polish/android-pin-confirmation.jpg) · [Matrice des contrôles](evidence/3.0-polish/ui-matrix.json)

## Validation du classement du 2 novembre 2026


Le classement ouvre sur le critère choisi, les dates, la place personnelle et la personne à rejoindre. La même liste sert au premier comme au cinquième. Deux scores égaux partagent leur rang. Les XP de la semaine, les performances par mesure et le détail des domaines restent accessibles. Le widget moyen montre le score personnel et un seul adversaire pour que les deux restent lisibles en grande police.

[Classement sur Pixel, avant et après](evidence/3.0/Pixel-comparaison.jpg) · [Égalité sur OnePlus, avant et après](evidence/3.0/OnePlus-comparaison.jpg) · [Cinquième place avec un nom long](evidence/3.0/OnePlus-hors-podium.png)

## Ce qui fait varier le score

Chaque domaine vaut de 0 à 100. L’indice sur 1 000 est la partie entière de dix fois la moyenne pondérée des domaines disponibles. Il en faut trois. La fenêtre va de J−7 à J−1, en dates locales. En 2.9, les deux domaines sportifs ne comptaient que le volume. Un record pouvait déjà rapporter des XP, suivis dans un classement séparé. Courir plus vite sur la même distance pouvait même faire baisser le domaine cardio en réduisant le nombre de minutes.

| Domaine | Poids | Calcul en 3.0 |
|---|---:|---|
| Musculation | 20 | Jusqu’à 90 points pour deux séances enregistrées. Jusqu’à 10 points pour le meilleur progrès sur un même exercice. |
| Cardio | 20 | Jusqu’à 90 points pour 150 minutes hors musculation. Jusqu’à 10 points pour un progrès de vitesse sur une course d’au moins 1 km, comparée à une distance voisine à 10 % près. |
| Pas | 15 | Moyenne des fractions de l’objectif atteint, plafonnées chaque jour. L’objectif du jour prévaut, puis l’objectif général, puis 8 000 pas. |
| Sommeil | 25 | Même règle face à 420 minutes par nuit. |
| Nutrition | 15 | Chaque jour renseigné vaut 100 moins 200 fois l’écart relatif absolu à la cible calorique du jour, avec un plancher à zéro. |
| Esprit et mobilité | 5 | Minutes de méditation et d’étirements, plafonnées à 70 minutes sur la période. |

Pas, sommeil et nutrition exigent quatre observations utilisables. Une source absente est exclue du dénominateur. La musculation activée sans séance vaut zéro. Pour le cardio, aucune activité connue laisse le domaine absent. Un module esprit ou mobilité actif sans minute vaut zéro.

Pour la performance, la référence est le meilleur résultat personnel des 28 jours **avant** la semaine évaluée. Un progrès de 1 % vaut un point de domaine, avec un plafond de 10. Les gains ne se cumulent pas. En musculation, le calcul utilise le 1RM estimé sur les séries de travail validées de 1 à 12 répétitions, ou le nombre de répétitions sans charge. Les exercices assistés sont exclus du bonus. Un résultat manquant, non fini, stable ou en baisse apporte zéro bonus. La saisie en livres est convertie en kilogrammes à l’enregistrement. Les kilos, les répétitions et les vitesses restent dans leur discipline.

| Même couverture, nutrition à 100 et esprit à 0 | 2.9 | 3.0 |
|---|---:|---:|
| Deux séances, référence absente ou performance stable | 875 | 825 |
| Deux séances, performance en hausse de 5 % | 875 | 850 |
| Deux séances, hausse de 10 % ou davantage | 875 | 875 |
| Vingt séances, performance en baisse | 875 | 825 |

**Entrées reproductibles.** Sur les deux QA, les séries de référence du même exercice incluent 52,5 kg le 24 octobre, puis 55, 57,5 et 60 kg les 27, 29 et 31 octobre, à 10 répétitions et 2 répétitions en réserve. Le gain dépasse 10 %. Les domaines valent 100, 100 et 0 pour des poids de 20, 15 et 5, soit 875. Dans le test de course, passer de 30 à 28 minutes sur 5 km fait passer le domaine cardio de 20 à 18 en 2.9, et de 18 à 24 en 3.0.

Les instantanés 2.9 restent enregistrés sous leur modèle, dans la limite existante des 60 entrées. Une mise à jour du même jour en 3.0 dispose de sa propre clé. La moyenne sur 28 jours utilise seulement les instantanés 3.0 disponibles de même couverture. Une carte amie 2.9 reste lisible et attend une mise à jour pour rejoindre le classement global. Pour la vue sur sept jours, modèle, fin de période et domaines doivent correspondre et la carte doit dater de moins de 48 h. Les performances comparent une même mesure et une même unité. Pour l’allure, le temps le plus court au kilomètre arrive en tête. Les mesures brutes restent comparables entre les modèles 2 et 3 quand leurs unités et périodes concordent.

## Vérifications sur les téléphones

- **Référence.** Branche locale `superhealth`, commit `e1f846cd` également en tête de la branche GitHub. La base testée comprend les évolutions locales 2.9. Les diffs du podium, des widgets et de l’indice ont été examinés. La CI de référence passe avec 222 tests SuperHealth et le relais avec 9 tests HTTP.
- **OnePlus 9, Android 16, 1 080 × 2 400, 450 dpi.** Mise à jour QA sur place. Égalité à 875, puis cinq minutes enregistrées aujourd’hui sans effet sur la période terminée. Rejeu contrôlé de la seule nouvelle séance à J−1. Le domaine esprit passe à 7, donnant 883 après troncature. Une fixture locale supplémentaire place le compte 5e sur 6, à 27 points de Camille du club du lundi. Une carte à 999 points vieille de 49 heures est écartée. Les deux premiers restent premiers ex æquo.
- **Pixel 8 Pro, Android 17, 1 008 × 2 244, 360 dpi.** Réception chiffrée du 883, position 2 sur 2, écart de 8. Même résultat après arrêt, relance et actualisation du widget. Les historiques du même jour contiennent encore 875 en v2 et 883 en v3 pour l’ami. Vérification séparée du classement XP (60 contre 140) et du record du même exercice (54 contre 57,4 kg).

| Réglage pertinent | Défaut | Vérification effectuée |
|---|---|---|
| Classement et progression | Activé | Désactivation sur OnePlus, écran dédié et disparition des cinq cartes sociales après relance. Réactivation avec l’ami et les scores conservés. |
| Séries suivies | Domaines disponibles | Retrait de Nutrition du jeu QA, relance, seul le parcours Musculation reste visible. Rétablissement. Le programme actif définit quatre séances. |
| Progression / composantes / activités | Oui / oui / non | Les trois partages étaient activés sur les QA. Désactivation, relance, synchronisation. Le Pixel conserve le score et reçoit zéro mesure, aucune composante et aucun résumé de progression. Nouvelle synchronisation après rétablissement. |
| Relais / fréquence / à l’ouverture | Activé / 4 fois par jour / non | Relais désactivé puis relancé, synchronisation sans changement d’horodatage. Valeurs 2, 4 et manuel enregistrées. L’option à l’ouverture persiste après relance. Retour au réglage QA manuel. |
| Serveur personnalisé | Désactivé | Une adresse vide déclenche la validation à l’enregistrement. Après relance, le serveur par défaut reste sélectionné. |
| Domaines de quêtes | À choisir | Ajout de Musculation à Nutrition sur Pixel, relance, quête courante toujours à 1/5. Retour à Nutrition seule. |
| Apparence | Système / normale sur le Pixel QA | Clair et très grande police appliqués depuis Réglages, conservés après relance. Retour à système et normale. Le classement a aussi été relancé en anglais puis en français, avec le même rang ex æquo. |

Les quatre tailles des cartes **Classement, Progression, Amis, Quêtes et Séries** ont été affichées sur les deux appareils. Leurs actions principales ouvrent respectivement le classement, la progression, les amis, les quêtes et la progression. Le widget Classement conserve la place personnelle hors du podium. La flamme Rive, les badges de catégorie et leurs chemins dynamiques restent utilisés. [Planche sombre](evidence/3.0/Widgets-sombre.jpg) · [Planche claire](evidence/3.0/Widgets-clair.jpg) · [Réglages 2.9](evidence/3.0/OnePlus-reglages-avant.png) · [Réglages 3.0](evidence/3.0/OnePlus-reglages-apres.png)

Le widget **Android Esprit et mobilité** compte les minutes depuis lundi. En petit, les minutes accompagnent désormais l’anneau. En moyen, bandeau et grand, un score disponible est libellé sur 1 000. Avec seulement deux domaines sur le OnePlus, la carte du tableau de bord indiquait « 3 domaines requis » et le widget Android omettait le score. Le widget Android a été lié et rendu par un hôte QA via AppWidgetManager dans les quatre tailles. Un appui sur le Pixel a ouvert l’onglet Activités, positionné sur Étirements. Les hôtes de test ont libéré leurs instances. [Widget Android en clair et en sombre](evidence/3.0/Widget-Android.jpg) · [Score indisponible](evidence/3.0/OnePlus-donnees-insuffisantes.png) · [Widget de classement moyen en grande police](evidence/3.0/Pixel-moyen-clair.png)

Pour revenir à la référence après le parcours croisé, seule la nouvelle séance QA a été retirée. En mode de proximité réservé aux amis connus, le Pixel est revenu de 883 à 875 pour le OnePlus sans changement de l’heure du dernier passage sur le relais. Les deux comptes ont ensuite resynchronisé leurs cartes chiffrées.

## Livraison

APK Android optimisé `app.superhealth`, version 3.0, code 33. La release contient `SuperHealth-3.0.apk` et son empreinte dans `SuperHealth-3.0.sha256`. L'archive, le certificat de signature et les fournisseurs de widgets sont contrôlés avant publication. Les activités de test restent réservées à la variante débogable QA.

La suite Android intégrale de la variante SuperHealth a recensé 2 810 tests, dont 2 771 réussis, 39 ignorés et aucun échec. Les 229 tests applicatifs SuperHealth ont tous réussi. Les 9 tests HTTP du serveur passent également. Le contrôle `git diff --check` est propre.

Pour reproduire le build, lancer `./gradlew --offline :app:testSuperhealthDebugUnitTest :app:assembleSuperhealthPerf` depuis le dépôt Android et `node --test server.test.mjs` depuis le dépôt serveur. Le mode hors ligne utilisé ici suppose le cache Gradle déjà rempli. La variante QA utilise `-PsuperhealthAppId=app.superhealth.qa`. Son écran de test `RegressionProbeActivity` accepte les opérations `ranking-fixture` et `ranking-cleanup` pour rejouer puis retirer les cinq amis fictifs du cas hors podium.

## Portée

Les contrôles portent sur l’application Android et le relais existant. Le dernier lancement à froid de la variante optimisée QA, depuis MainActivity, affiche 875/1 000 et la première place ex æquo sur chaque téléphone. Les deux QA ont ensuite retrouvé leur variante débogable 3.0, avec le même résultat après relance. Les installations personnelles sont restées en 2.6 sur le OnePlus et en 2.63 sur le Pixel. L’intervalle réel de déclenchement par Android à 6 ou 12 heures, une semaine complète avec changement de quête, une acquisition en direct depuis une montre et une mise à niveau iOS ne font pas partie des observations sur téléphone. Pour le widget de classement moyen, l’image de référence exploitable provient de l’archive QA 2.9. Pour le widget Android Esprit et mobilité, les captures disponibles sont celles de la 3.0. [Widget moyen 2.9 archivé](evidence/3.0/Archive-2.9-widget-M.png) · [Widget moyen 3.0](evidence/3.0/Pixel-widget-apres.png)
