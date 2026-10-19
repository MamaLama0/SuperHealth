# SuperHealth

La Bouffe (nutrition, MacroFactor) + La Salle (musculation, MacroFactor Workouts) + LocalHealth (montres, moteur
Gadgetbridge) dans une seule app, avec l'interface de MacroFactor. Flavor `superhealth` de ce fork de Gadgetbridge
(`app/src/superhealth`, identifiant `app.superhealth`).

## Organisation

| Dossier (`app/src/superhealth/java/app/superhealth`) | Contenu |
|---|---|
| `nutrition/` | La Bouffe (ex-`fr.perso.nutrilocal`) : journal, recherche, stratégie, check-in, analyses |
| `training/` | La Salle (ex-`fr.perso.workoutlocal`) : programmes, séance, progression, niveaux |
| `health/data/` | LocalHealth : lecture des données Gadgetbridge (montres) et saisies manuelles |
| `bridge/` | Communication entre modules : `Bridge` (profil, poids, pas, activités), `HealthConnectHub`, `Watch` (module désactivable), `SyncWorker` |
| `ui/` | Coque : `SuperRoot` (4 onglets + « + »), `onboarding/`, `dashboard/`, `health/`, `settings/`, thème et langue communs |

Onglets : Tableau de bord · Journal · **+** · Activités · Réglages.

## Une seule source par donnée
- Profil, langue (FR / EN), thème, unités, objectif de pas : module nutrition → recopiés vers la musculation et le profil Gadgetbridge (montre).
- Poids de corps : pesées du module nutrition (saisie, balance Gadgetbridge, Health Connect) → poids de corps de la musculation.
- Pas : montre si elle les mesure, sinon Health Connect → dépense énergétique V3.
- Activités : séances de musculation + séances de la montre + séances Health Connect, dédoublonnées.
- Health Connect : une seule demande d'autorisations ; l'export des données brutes de la montre reste celui de Gadgetbridge.

## Construire
```bash
JAVA_HOME=~/tools/jdk21 ./gradlew :app:assembleSuperhealthDebug        # app/build/outputs/apk/superhealth/debug/
JAVA_HOME=~/tools/jdk21 ./gradlew :app:testSuperhealthDebugUnitTest --tests 'app.superhealth.*'
```

Les assets graphiques (illustrations, icônes d'aliments, exercices, animations) viennent des APK MacroFactor :
**build réservé à un usage privé, ne pas le distribuer.**
