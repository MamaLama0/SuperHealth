# SuperHealth

Nutrition, musculation et santé dans une seule app, sans compte ni cloud : vos données restent sur le téléphone.

## Fonctionnalités

- **Nutrition** : journal (recherche, code-barres, description ou photo analysée par IA, recettes), calories et macros
  ajustées chaque semaine à votre dépense réelle, tendance du poids, objectif avec un rythme choisi, mesures et photos
  de progression.
- **Musculation** : programmes générés selon vos objectifs, vos disponibilités et votre salle ; séances guidées avec
  charges et répétitions calculées, échauffements et minuteur de repos ; progression, records, muscles travaillés.
- **Santé et activités** : montre connectée (pas, cœur, sommeil, VFC, SpO₂…), scores de récupération, d'effort et
  dette de sommeil, courses avec tracé, zones cardiaques et splits, import GPX, TCX et FIT.
- **Tableau de bord** à composer : cartes en quatre tailles, anneaux du jour, constantes, constats et corrélations.
- **Plugins** : coach IA, planificateur de repas, rappels de médicaments.
- **Widgets**, sauvegardes chiffrées (fichier ou WebDAV), français et anglais.

## Android

APK dans les [versions](https://github.com/MamaLama0/SuperHealth/releases/latest).

## iOS (expérimental)

Build **expérimental** : même interface et mêmes fonctions qu'Android. Les données de la montre viennent de la santé
de l'iPhone (Apple Watch), avec en plus les anneaux d'activité, les signes vitaux de la nuit, les alertes cardiaques et
ECG, la dynamique de course et la séance en cours sur l'écran verrouillé.

L'`.ipa` n'est pas signée : il faut l'installer avec un outil de sideload, qui la signe avec votre identifiant Apple.
Elle est publiée dans les [versions](https://github.com/MamaLama0/SuperHealth/releases) marquées « iOS » ; le code est
sur la branche [`ios`](https://github.com/MamaLama0/SuperHealth/tree/ios).

### Installer sur iPhone avec Sideloadly

[Sideloadly](https://sideloadly.io/#features) installe une `.ipa` depuis un ordinateur Windows ou Mac, avec un
identifiant Apple gratuit (pas besoin du programme développeur payant).

#### Ce qu'il faut

- Un iPhone sous iOS 16.2 ou plus récent (l'app est faite pour iPhone ; sur iPad elle s'ouvre au format iPhone).
- Un ordinateur **Windows 7 ou plus récent** (64 ou 32 bits) ou **macOS 10.12 ou plus récent** (Mac Intel ou Apple Silicon).
- Un câble USB pour la première installation.
- Un identifiant Apple. Gratuit : l'app reste valable **7 jours**, puis doit être re-signée (voir « Renouvellement »).
  Compte développeur payant (99 $/an) : valable 1 an.
- Le fichier `SuperHealth.ipa` de la dernière version « iOS ».

#### 1. Préparer l'ordinateur

**Sous Windows**

1. Désinstallez iTunes et iCloud s'ils viennent du **Microsoft Store** : Sideloadly ne fonctionne qu'avec les versions
   « web ».
2. Installez les versions web d'**iTunes** et d'**iCloud** (liens sur la page de téléchargement de Sideloadly), puis
   redémarrez l'ordinateur.
3. Téléchargez et installez **Sideloadly** depuis [sideloadly.io](https://sideloadly.io/).

**Sur Mac**

1. Téléchargez **Sideloadly** depuis [sideloadly.io](https://sideloadly.io/) et glissez-le dans Applications.
2. Au premier lancement, si macOS dit que le développeur ne peut pas être vérifié : Réglages Système >
   Confidentialité et sécurité > **Ouvrir quand même**.

#### 2. Brancher l'iPhone

1. Branchez l'iPhone en USB et déverrouillez-le.
2. Touchez **Se fier à cet ordinateur** et entrez le code de l'iPhone.
3. L'iPhone doit apparaître dans le champ « iDevice » de Sideloadly. Sinon : réinstallez les versions web d'iTunes et
   d'iCloud, redémarrez l'ordinateur, essayez un autre câble ou un autre port.

#### 3. Installer l'app

1. Ouvrez Sideloadly et **glissez `SuperHealth.ipa`** sur la fenêtre (ou cliquez sur l'icône IPA pour la choisir).
2. Entrez votre **identifiant Apple** (adresse e-mail) dans « Apple account ».
3. Laissez les **options avancées** par défaut :
   - mode de signature **Apple ID Sideload** ;
   - **ne changez pas l'identifiant de l'app (bundle ID)** : un autre identifiant crée une deuxième app vide, sans vos
     données ;
   - ne cochez **pas** « Remove extensions » : l'extension sert aux widgets et à la séance sur l'écran verrouillé.
4. Cliquez sur **Start**, puis entrez le mot de passe de l'identifiant Apple et le code de double authentification
   quand ils sont demandés. Le mot de passe part uniquement vers les serveurs d'Apple.
5. Attendez « Done » dans le journal en bas de la fenêtre.

#### 4. Autoriser l'app sur l'iPhone

1. **Mode développeur** (iOS 16 et plus, une seule fois) : Réglages > Confidentialité et sécurité > **Mode
   développeur** > activer, puis redémarrer l'iPhone et confirmer « Activer » au réveil. L'option n'apparaît qu'après
   une première installation par Sideloadly.
2. **Faire confiance au profil** : Réglages > Général > **VPN et gestion de l'appareil** > touchez votre adresse
   e-mail > **Faire confiance**.
3. Ouvrez SuperHealth.

#### 5. Connecter Apple Santé

1. Pendant l'accueil, touchez **Connecter** (ou plus tard : rond de votre initiale en haut à droite > Apple Santé >
   activez l'interrupteur), puis **Tout activer** dans la fenêtre de Santé et **Autoriser**.
2. Pour revoir les accès : Réglages de l'iPhone > Santé > Accès aux données et appareils > SuperHealth.
3. Avec une Apple Watch, rien d'autre à faire : elle écrit dans Santé. Pour une autre montre (Garmin, Polar,
   Withings…), activez le partage vers Apple Santé dans son app.

Si un message dit que l'app a été **installée sans le droit Apple Santé**, le compte qui a signé l'app n'a pas reçu la
fonction HealthKit : réinstallez avec Sideloadly en mode **Apple ID Sideload** (pas « Ad-hoc ») ; si le message
revient avec un identifiant Apple gratuit, seul un compte développeur payant permet Apple Santé.

#### Renouvellement (identifiant gratuit : tous les 7 jours)

- Au bout de 7 jours l'app ne s'ouvre plus, mais **vos données restent** : réinstallez la même `.ipa` (ou une plus
  récente) avec le **même identifiant Apple**, sans supprimer l'app.
- **Renouvellement automatique** : cochez « Auto-refresh » avant de cliquer sur Start ; le service de Sideloadly
  re-signe l'app tous les quelques jours tant que l'ordinateur reste allumé et voit l'iPhone (en USB ou en Wi-Fi).
- **Par Wi-Fi** : après un premier branchement en USB, activez « Synchroniser avec cet iPhone en Wi-Fi » (iTunes sous
  Windows, avec le service Bonjour d'Apple) ou « Afficher cet iPhone en Wi-Fi » (Finder sur Mac). L'ordinateur et
  l'iPhone doivent être sur le même réseau, écran de l'iPhone allumé.

#### Mettre à jour

Glissez la nouvelle `SuperHealth.ipa` dans Sideloadly et installez-la avec le même identifiant Apple et le même
identifiant d'app, **sans supprimer l'ancienne** : les données sont conservées. Supprimer l'app efface ses données :
faites d'abord une sauvegarde (Réglages > Sauvegarde) si vous devez la supprimer.

#### Limites d'un identifiant Apple gratuit

- **3 apps** installées par sideload en même temps sur l'iPhone.
- **10 identifiants d'app tous les 7 jours** : SuperHealth en utilise 2 (l'app et son extension de widgets).

#### Problèmes fréquents

| Message | Solution |
| --- | --- |
| `<no devices detected>` | Versions web d'iTunes et d'iCloud, redémarrer l'ordinateur, toucher « Se fier » sur l'iPhone, autre câble ou port. |
| `LOCKDOWN_E_INVALID_HOST_ID` | Toucher « Se fier » sur l'iPhone, redémarrer l'ordinateur iPhone branché, ou synchroniser une fois avec iTunes ou le Finder. |
| `NP_E_CONN_FAILED`, `AFC_E_MUX_ERROR` | Remplacer iTunes du Microsoft Store par la version web, synchroniser puis rouvrir Sideloadly. |
| `Your maximum App ID limit has been reached` | Attendre quelques jours ou utiliser un autre identifiant Apple. |
| `maximum number of installed apps using a free developer profile` | Supprimer une autre app installée par sideload (3 au maximum). |
| `A valid provisioning profile for this executable was not found` | Mettre la date et l'heure automatiques sur l'ordinateur et l'iPhone. |
| `The identity used to sign the executable is no longer valid` | Même chose : date et heure correctes des deux côtés. |
| `DeviceNotSupportedByThinning` | Cocher « Remove limitation on supported devices » dans les options avancées. |
| `Failed to obtain anisette` | Réessayer plus tard, ou passer en « Local Anisette » dans les options avancées. |
| `Installation failed: 0 InstallProhibited` | Temps d'écran > Restrictions de contenu et de confidentialité > Achats dans l'iTunes et l'App Store > Installation d'apps : autoriser. |
| L'app est installée mais sans icône | Redémarrer l'iPhone. |
| L'app s'ouvre puis se ferme aussitôt | Le profil n'est pas approuvé (étape 4) ou les 7 jours sont passés : réinstaller. |

Plus de réponses dans la [FAQ de Sideloadly](https://sideloadly.io/faq) et sur r/Sideloadly.

### Construire l'app iOS

- GitHub Actions (`.github/workflows/ios.yml`) produit `SuperHealth.ipa` et une version pour le simulateur.
- Sur un Mac : `cd iosApp && xcodegen generate && open SuperHealth.xcodeproj`.
- Sous Linux : `./gradlew :shared:compileKotlinIosArm64` vérifie le code iOS, `./gradlew :shared:desktopTest` lance les
  tests et `./gradlew :shared:screenshots` rend des captures au format iPhone.
