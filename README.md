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
| **Coque** | Tableau de bord personnalisable, bouton « + » personnalisable, Health Connect, widgets, FR / EN |

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

Une seule source par donnée : profil, langue et unités viennent de la nutrition et sont recopiés vers la musculation et
le profil Gadgetbridge ; le poids de corps vient des pesées ; les pas fusionnent montre et téléphone (maximum par heure).

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

## Licence

Gadgetbridge est sous licence AGPL-3.0 ([LICENSE](LICENSE)) ; ce fork l'est donc aussi. README d'origine :
[README.gadgetbridge.md](README.gadgetbridge.md).
