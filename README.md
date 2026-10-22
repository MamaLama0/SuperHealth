# SuperHealth

Nutrition, musculation et montre connectée dans une seule app Android, 100 % locale (ni compte ni cloud).
Interface inspirée de MacroFactor et Google Health, moteur montres de [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge).

> **Usage privé.** Les assets graphiques (illustrations, icônes d'aliments, photos d'exercices, animations) proviennent
> des APK MacroFactor : ce build ne doit pas être distribué.

## Fonctions

| Module | Contenu |
|---|---|
| **Nutrition** (ex-« La Bouffe ») | Journal par heure, recherche (bibliothèque, base suisse, Open Food Facts), code-barres, ajout rapide, recettes, IA (photo/texte, fournisseur compatible OpenAI), dépense énergétique adaptative, tendance du poids, programme et check-in hebdomadaires |
| **Musculation** (ex-« La Salle ») | Programmes générés ou manuels, séances perso, supersets, progression intelligente, échauffements, minuteur de repos, niveaux par muscle, records |
| **Santé / montre** (ex-« LocalHealth ») | Pas (montre + téléphone fusionnés heure par heure), fréquence cardiaque en direct, sommeil, constantes, activités Garmin façon Garmin Connect (carte, splits, zones), performances, import .fit, récupération de l'historique de la montre |
| **Scores** | Charge (récupération), Effort (charge cardio du jour), dette de sommeil, charge aiguë / chronique, signaux de fatigue — méthodes publiées documentées par NOOP, calculées pour toute montre |
| **WHOOP** | Pilote Gadgetbridge pour WHOOP 4.0 et 5.0 / MG : historique seconde par seconde, pouls en direct, sommeil et phases, VFC, pouls au repos, stress, température (5.0), respiration (4.0), pas (5.0) |
| **Progression** | Succès facultatifs : XP quotidienne, trophées bronze / argent / or, voyage du petit bonhomme autour de la Terre puis en fusée vers la Lune, Mars… |
| **Coque** | Tableau de bord personnalisable (graphiques de montre : phases de sommeil, VFC, stress, zones, charge, journée), bouton « + », bandeau de pesée, objectifs de pas par jour, Health Connect, widgets, FR / EN |
| **Données** | Sauvegarde chiffrée (AES-256-GCM, code de récupération créé au premier démarrage) en fichier, Nextcloud, WebDAV ou dossier ; mises à jour depuis les versions GitHub (vérification quotidienne, confirmation par notification) |

Testé avec une Garmin Forerunner 165 sur Pixel 10 Pro XL (GrapheneOS, Android 16). Toutes les montres prises en charge par
Gadgetbridge fonctionnent pour les données de base.

## Organisation du code

Flavor Gradle `superhealth` de ce fork (identifiant `app.superhealth`), dans `app/src/superhealth/` :

| Dossier (`java/app/superhealth`) | Rôle |
|---|---|
| `ui/` | Coque : `SuperRoot` (onglets Accueil · Journal · **+** · Activités · Réglages), `dashboard/`, `onboarding/`, `health/`, `settings/`, thème, langue |
| `nutrition/` | Journal, recherche, stratégie, analyses ; base Room `nutrilocal.db` |
| `training/` | Programmes, séance, éditeur de plans (`ui/screens/PlanEditor.kt`), progression ; état JSON `state.json` |
| `health/data/` | Lecture des données Gadgetbridge (`HealthRepository`), saisies manuelles, détail des séances, performances |
| `bridge/` | Liens entre modules : `Bridge` (profil, poids, pas, activités), `HealthConnectHub`, `Watch`, `FitImport`, `SyncWorker` |
| `health/data/Scores.kt` | Charge, Effort, dette de sommeil, références personnelles |
| `achievements/`, `ui/achievements/` | XP, trophées, voyage (scène dessinée, sans image) |
| `backup/`, `update/` | Sauvegarde chiffrée (`BackupCrypto`, `Secrets`, `Remote`, `BackupWorker`), mises à jour GitHub (`Updater`) |

Une seule source par donnée : profil, langue et unités viennent de la nutrition et sont recopiés vers la musculation et
le profil Gadgetbridge ; le poids de corps vient des pesées ; les pas fusionnent montre et téléphone (maximum par heure).

WHOOP (`app/src/main/.../devices/whoop`, `service/devices/whoop`) : protocole réécrit d'après les notes publiques de NOOP
(UUID, trames, CRC, numéros de commande et de paquet), stockage des secondes sur le téléphone (`WhoopRawStore`), analyse
(`WhoopAnalysis`) et écriture dans les tables génériques de Gadgetbridge (`WhoopPersist`, table `WhoopActivitySample`,
schéma 158). Seules des commandes réversibles sont envoyées ; un bloc d'historique n'est acquitté qu'une fois enregistré.

Changements dans le moteur Gadgetbridge (`app/src/main`) : récupération de l'historique Garmin
(`RecordedDataTypes.TYPE_HISTORY`, toutes les pages de la liste de fichiers), thème et langue alignés sur SuperHealth.

## Construire

JDK 21 et SDK Android requis.

```bash
./gradlew :app:assembleSuperhealthPerf     # APK à installer : app/build/outputs/apk/superhealth/perf/
./gradlew :app:assembleSuperhealthDebug    # débogable (run-as, inspection des bases), nettement moins fluide
./gradlew :app:testSuperhealthDebugUnitTest --tests 'app.superhealth.*'
```

Le build `perf` a le même identifiant et la même clé que `debug` : on passe de l'un à l'autre sans perdre de données.
Après installation, `adb shell cmd package compile -m speed-profile -f app.superhealth` accélère le premier lancement.
Les tests Robolectric hérités de Gadgetbridge échouent sous ce flavor (« Requires newer sdk version ») : seuls les tests
`app.superhealth.*` comptent.

La CI GitHub (`.github/workflows/build.yml`) construit l'APK `perf` et lance ces tests à chaque push ; l'APK est
téléchargeable dans les artefacts du run.

## Assets sous copyright

`python3 tools/superhealth_copyright_assets.py export DOSSIER` copie tous les assets tiers (MacroFactor, MacroFactor
Workouts, icône Apple Santé) dans DOSSIER avec une galerie (`index.html`, `ouvrir.sh`) et un inventaire (`manifest.csv`) ;
`… import DOSSIER` remet dans l'app les fichiers modifiés (mêmes chemins).

## Licence

Gadgetbridge est sous licence AGPL-3.0 ([LICENSE](LICENSE)) ; ce fork l'est donc aussi. Le pilote WHOOP et les scores
s'appuient sur la documentation de [NOOP](https://github.com/ryanbr/noop) (PolyForm Noncommercial 1.0.0 ; faits de
protocole non protégés), sans en reprendre le code. README d'origine :
[README.gadgetbridge.md](README.gadgetbridge.md).
