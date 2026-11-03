# Contribuer à SuperHealth

Utilisez les [issues](https://github.com/MamaLama0/SuperHealth/issues) pour signaler un problème ou proposer une évolution. Vérifiez les demandes existantes et décrivez le résultat souhaité avec un exemple.

## Choisir la bonne branche

| Changement | Branche cible de la PR |
| --- | --- |
| Android et documentation générale | `superhealth` |
| iOS et widgets Apple | `ios` |
| SideStore avec HealthKit | `sidestore` |
| Relais chiffré et médias | `server/homeserver` |
| Catalogues des modules | `modules` |

Ces branches ont des sources autonomes. Pour une fonction Android et iOS, préparez une modification adaptée à chaque plateforme, sans fusionner leurs branches entières.

## Compiler et tester Android

Installez Git, JDK 21 et le SDK Android indiqué par `compileSdk` dans `app/build.gradle`, puis utilisez le wrapper fourni :

```sh
git clone --branch superhealth https://github.com/MamaLama0/SuperHealth.git
cd SuperHealth
git switch -c fix/description-courte
./gradlew :app:assembleSuperhealthDebug
./gradlew :app:testSuperhealthDebugUnitTest --tests 'app.superhealth.*'
./gradlew :app:assembleSuperhealthPerf
git diff --check
```

Les APK sont sous `app/build/outputs/apk/superhealth/`. Les fonctions locales se compilent sans clé privée. Pour un relais de développement, copiez `local.server.properties.example` et gardez les valeurs réelles hors de Git. Les commandes des autres plateformes sont dans leur README, lié depuis l’[index](docs/README.md).

## Préparer un retour ou une PR

Indiquez la plateforme, la version, les étapes, le résultat attendu et le résultat observé. Une PR explique le changement et les vérifications réellement faites. Testez les parcours concernés en français et en anglais ; vérifiez chargements, erreurs et conservation des données. Ajoutez un test de régression lorsqu’il est utile.

Ne joignez pas de secrets, codes amis ou données de santé. Masquez les informations personnelles des captures. Conservez les licences et attributions des sources et ressources tierces. Les [crédits SuperHealth](CONTRIBUTORS.rst) sont séparés de l’historique Gadgetbridge.

Les notes de version publiques restent centrées sur l’utilisateur : 1 à 4 puces, 80 mots maximum, sans diagnostics ni données privées. Le mainteneur gère les numéros de version et la publication.
