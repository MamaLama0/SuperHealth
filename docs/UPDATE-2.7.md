# SuperHealth 2.7 — revue de l'intégration

État du travail du 2 novembre 2026. Les essais sur téléphones utilisent le paquet séparé `app.superhealth.qa` ; l'application personnelle `app.superhealth` et ses données n'ont pas été remplacées.

## Changements

| Partie | Comportement ajouté ou corrigé |
|---|---|
| Nutrition | Programmes coaché, collaboratif et manuel. Saisie calories + pourcentages ou grammes de macros avec conversion 4/9/4, cibles par jour, objectif de poids facultatif. |
| Dérogations nutritionnelles | Cible temporaire pour aujourd'hui, sept jours ou jusqu'à nouvel ordre ; retour au programme, avec conservation des périodes passées. |
| Configuration | Relance indépendante des parcours Nutrition et Musculation dans leurs paramètres ; les simulations restent des aperçus séparés. La relance Nutrition ne recrée pas le programme de musculation. |
| Introduction | Activités choisies avant leurs questions ; réglages manuels proposés dans le parcours ; résumé cohérent avec le mode et le suivi du poids ; page Plugins facultative, téléchargements après validation, reprise différée si hors ligne. Retour possible depuis le résumé. |
| Plugins | Catalogue compact avec recherche, filtres, interrupteurs des plugins installés et fiches détaillées ; options/sources regroupées à la demande ; catalogue initial et dernier catalogue disponibles hors ligne. Les amis sont inclus dans l'app. |
| Édition des exercices | Identité stable des lignes et de la sélection pendant le déplacement ; chargement des images lié au chemin réel. Le déplacement conserve les associations exercice/cibles/images. |
| Cycles | Choisir un cycle reste un aperçu. Un bouton et une confirmation permettent d'en faire le cycle courant, même si le cycle précédent est incomplet, ou de sauter plus loin. Une séance déjà commencée reste attachée à son ancien cycle. |
| Programmes | Import/export d'un programme seul en JSON, dans la bibliothèque avec de nouveaux identifiants ; programme additionnel bras 30 jours de la référence Workouts 1.4.2 ; conservation des exercices exclus selon les cycles et des supersets. |
| Échauffement | Schéma personnalisable de pourcentages de charge et de répétitions depuis les paramètres. |
| Indice de santé | Indice d'habitudes v2 sur sept jours terminés, pondération documentée, données manquantes distinctes de zéro et minimum de trois parties renseignées. Comparaison seulement entre périodes et parties identiques. Ce n'est pas une mesure médicale. |
| Comparaisons | Activités, séances/volume de musculation, course (nombre, distance, durée, plus longue, meilleure allure moyenne sur une course ≥ 1 km), PR par exercice : charge, 1RM estimé, répétitions au poids du corps. Les PR sont historiques, les totaux portent sur sept jours. Partage détaillé facultatif, aucun tracé GPS. |
| Amis | Cartes signées, P2P sur Wi-Fi local, authentification éphémère avant échange des détails entre amis connus. Option de recherche à chaque ouverture ; arrêt de la découverte quand l'app n'est plus visible. |
| Internet | Relais personnel facultatif fourni dans `tools/friend-relay`, publiant uniquement les cartes partagées. Rafraîchissement manuel, 2 ou 4 fois/jour et à l'ouverture avec délai minimal de 10 min entre tentatives distantes. Aucun dépôt Google partagé. |
| Sauvegardes | Fichier privé chiffré dans Google Drive choisi via le sélecteur Android et son app ; droit d'accès persistant, remplacement du même fichier, lecture/restauration. WebDAV et dossier conservés. L'export complet échoue si l'export des montres échoue, au lieu d'omettre silencieusement ces données. |
| Montres | Intégration de Gadgetbridge `6351d961ac6b01cd1c078eb07ea7bf7d2d891cb2` (voir note ci-dessous), en conservant WHOOP et les adaptations SuperHealth. Migration 158 → 160 compatible avec la numérotation WHOOP existante. |

Les 622 types d'appareils du registre initial sont conservés ; le registre final contient 653 types, dont les nouvelles références amont et WHOOP. Cela vérifie le registre et la compilation, pas chaque montre physique. La copie avant intégration est dans `Update/02⁄11/Gadgetbridge-avant-integration/` à la racine du projet parent.

## Validation

- 175 tests ciblés SuperHealth et WHOOP : réussis, aucun ignoré. Incluent conversions nutritionnelles, indice, cycles, import natif complet et migration SQLite idempotente.
- Test du relais Node : réussi ; deux clients HTTP indépendants, identité/signature, authentification, rejouage, limites, URL de lecture et suppression.
- Suite complète finale : 2 735 tests, 2 696 réussis, 39 ignorés, aucun échec. La base Robolectric et les cinq annotations qui forçaient Android 23 ont été alignées sur Android 26, le minimum de SuperHealth. Le test SQLite de la migration 156 utilise une Application minimale pour ne pas démarrer WorkManager. Les 39 tests ignorés concernent des corpus/captures ou scénarios optionnels déjà ignorés dans le projet.
- appareil Android A et appareil Android B : installation QA distincte, démarrage, écrans de plugins, import réel du programme et échange local des nouvelles cartes entre amis connus. Démarrage du catalogue également vérifié dans le build QA optimisé par R8.
- Appareil Android B : passages cycle 2 incomplet → 3 → 5, confirmation et état stocké vérifiés ; les jours faits du nouveau cycle sont remis à zéro, l'historique reste présent.
- Appareil Android A : parcours d'introduction en mode nutrition manuel, sans objectif de poids, page Plugins et résumé affichant 2 150 kcal / 150 g protéines / 70 g lipides / 230 g glucides, sans promesse de check-in automatique.
- Migration réelle de la base QA 158 → 160 : colonne Xiaomi `INTO_BED_TIME` créée ; table WHOOP conservée.
- Sauvegarde réelle QA : archive chiffrée, présence de `montre.zip`, nutrition, musculation, paramètres et santé ; restauration complète réussie, programme exporté identique avant/après.
- Les essais antérieurs à l'intégration amont ont aussi couvert le déplacement des exercices et leurs images, les deux sens de conversion des macros, la désactivation de l'objectif de poids et l'indépendance des deux parcours de configuration.

Les téléphones de test n'ont pas de fournisseur Google Drive installé/connecté : l'interface et le mécanisme de fichier sont intégrés, mais une mise en ligne réelle dans un compte Drive reste à vérifier avec ce fournisseur. La sauvegarde complète et sa restauration ont été testées localement. Le relais n'a pas été déployé ; l'échange Internet réel entre réseaux distincts reste à valider après hébergement HTTPS. Le test local du serveur ne remplace pas cet essai.

## Références et limites de comparaison

Les fichiers fournis MacroFactor Nutrition 5.8.10 et Workouts 1.4.2 ont été inventoriés. Les preuves sont sous `docs/evidence/2026-11-02/`. L'analyse Dart de la version Nutrition n'a pas produit une décompilation complète exploitable : les chaînes statiques ne prouvent pas les algorithmes ni une parité exhaustive de fonctions.

Le JSON du programme additionnel Workouts est directement observable dans l'APK : 5 cycles, 7 jours, 33 emplacements d'exercices et 24 exclusions exercice/cycle. Les temps de repos natifs sont des durées en microsecondes, converties en secondes à l'import. Une décharge n'est indiquée que si le fichier la déclare ; le programme additionnel n'en déclare pas.

Déjà présents dans SuperHealth, donc conservés : progression, supersets, séries dégressives/myo/échec, répétitions partielles, salles et matériel, historique, calculateur de disques, gestion des recettes/aliments, suivi de dépense et tendance de poids. Les règles propres à SuperHealth ne sont pas présentées comme une reproduction exacte d'un algorithme fermé.

L'import JSON refuse explicitement les exercices inconnus, les cibles de durée/distance et les types de séries non reconnus. L'import de tableurs arbitraires, les services privés MacroFactor, les intégrations Apple Watch/Live Activities et le P2P Internet direct avec traversée de NAT ne sont pas ajoutés. Pour l'Internet automatique et asynchrone, l'implémentation livrée utilise le petit relais auto-hébergeable. Sa documentation décrit aussi que les cartes sont signées mais lisibles par l'hébergeur, et comment les liens de lecture doivent être protégés.

## Reproduction

Depuis le dossier `SuperHealth/` :

```sh
./gradlew :app:assembleSuperhealthDebug :app:testSuperhealthDebugUnitTest -PsuperhealthAppId=app.superhealth.qa
./gradlew :app:assembleSuperhealthPerf
node --test tools/friend-relay/server.test.mjs
```

Le premier build est l'installation QA distincte. Le second produit l'APK normal optimisé, non débogable, signé avec la même clé que la version 2.63 fournie. Les identifiants, signatures et empreintes des APK livrés sont conservés dans `docs/evidence/2026-11-02/apk-verification.json`.
