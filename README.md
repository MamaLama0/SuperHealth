# SuperHealth iOS

Portage iOS de SuperHealth (app Android) en **Compose Multiplatform** : même interface, mêmes algorithmes, même code Kotlin.

- `shared/` : tout le code de l'app (commonMain), le branchement iOS (iosMain : HealthKit, notifications, trousseau,
  sélecteurs de fichiers et de photos, lecteur de codes-barres) et l'aperçu ordinateur (desktopMain).
- `iosApp/` : hôte Swift minimal et projet XcodeGen (`project.yml`).
- `assets/` : assets de l'app Android, embarqués tels quels.

Sur iOS, Apple Santé remplace le moteur Gadgetbridge et Health Connect : l'Apple Watch (ou une autre montre dont l'app
partage avec Santé) fournit pas, cœur, VFC, sommeil par phases et séances.

## Construire

- ipa non signée : GitHub Actions (`.github/workflows/ios.yml`), à installer avec SideStore, AltStore ou Sideloadly.
- Sur un Mac : `cd iosApp && xcodegen generate && open SuperHealth.xcodeproj`.
- Sous Linux : `./gradlew :shared:compileKotlinIosArm64` (vérifie le code iOS), `./gradlew :shared:desktopTest`,
  captures au format iPhone : `./gradlew :shared:screenshots -Pshots="$PWD/screenshots @onboarded home=home log=log"`.
