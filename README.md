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

### Quelle méthode ?

| | SideStore + HealthKit | Sideloadly |
| --- | --- | --- |
| Apple Santé (pas, cœur, sommeil, Apple Watch…) | **Oui** | Non |
| Identifiant Apple gratuit | Oui | Oui |
| Renouvellement tous les 7 jours | Depuis l'iPhone, sans ordinateur | Depuis l'ordinateur |
| Ordinateur | Windows, Mac ou Linux, une seule fois | Windows ou Mac, à chaque renouvellement |

Avec un identifiant Apple gratuit, **Sideloadly retire le droit HealthKit** en signant l'app (les droits personnalisés
y sont réservés au compte développeur payant) : SuperHealth s'installe mais affiche « Installée sans le droit Apple
Santé ». Apple autorise pourtant HealthKit aux comptes gratuits ; la branche
[`sidestore`](https://github.com/MamaLama0/SuperHealth/tree/sidestore) fournit donc **SideStore avec HealthKit
autorisé** (SideStore officiel + un patch de 6 lignes). C'est la méthode à suivre pour avoir Apple Santé.

### Installer sur iPhone avec SideStore (Apple Santé)

[SideStore](https://sidestore.io) est un magasin d'apps installé sur l'iPhone : il signe les apps avec votre
identifiant Apple et les renouvelle lui-même, sans ordinateur après la première installation. On l'installe une fois
avec [iloader](https://iloader.app) depuis un ordinateur.

#### Ce qu'il faut

- Un iPhone sous iOS 16.2 ou plus récent, avec un code de verrouillage.
- Un ordinateur pour la première installation seulement : **Windows 8 ou plus récent (64 bits)**, **macOS 10.13 ou
  plus récent** ou **Linux** à jour.
- Un câble USB et une connexion **Wi-Fi** (les données mobiles ne suffisent pas pour SideStore).
- Un identifiant Apple (gratuit suffit). Les apps restent valables **7 jours** et SideStore les renouvelle.
- Deux fichiers à télécharger sur l'iPhone (app Fichiers) ou sur l'ordinateur :
  - `SideStore-HealthKit.ipa` : pré-version [« SideStore + HealthKit »](https://github.com/MamaLama0/SuperHealth/releases/tag/sidestore-healthkit-0.7.0-alpha) ;
  - `SuperHealth.ipa` : dernière version marquée « iOS » dans les [versions](https://github.com/MamaLama0/SuperHealth/releases).

#### 0. Si SuperHealth est déjà installé avec Sideloadly

L'app installée par Sideloadly n'a pas Apple Santé et ne pourra pas être remplacée par celle de SideStore (signature
différente) :

1. Dans SuperHealth : rond de votre initiale > **Sauvegarde** > **Exporter**, enregistrez le fichier dans Fichiers et
   notez le **code de récupération** affiché.
2. Supprimez SuperHealth (appui long sur l'icône > Supprimer l'app). Cela libère aussi une des 3 places d'app.
3. Si le SideStore officiel est déjà installé, supprimez-le aussi : la version HealthKit le remplace.

#### 1. Préparer l'iPhone : LocalDevVPN

1. Installez **LocalDevVPN** depuis l'[App Store](https://apps.apple.com/app/localdevvpn/id6755608044).
2. Ouvrez-la, touchez **Connect**, puis **Autoriser** pour la configuration VPN et entrez le code de l'iPhone.
3. Ce VPN local doit être **connecté chaque fois que SideStore installe, met à jour ou renouvelle une app**. Il ne
   fait pas sortir vos données de l'iPhone.

#### 2. Préparer l'ordinateur : iloader

- **Windows** : installez iTunes (Microsoft Store ou site d'Apple ; à défaut, l'app « Appareils Apple »), puis
  iloader en [MSI](https://github.com/nab138/iloader/releases/latest/download/iloader-windows-x64.msi).
- **Mac** : installez iloader depuis le [DMG](https://github.com/nab138/iloader/releases/latest/download/iloader-darwin-universal.dmg).
- **Linux** : installez `usbmuxd` avec le gestionnaire de paquets, puis iloader en
  [DEB](https://github.com/nab138/iloader/releases/latest/download/iloader-linux-amd64.deb),
  [RPM](https://github.com/nab138/iloader/releases/latest/download/iloader-linux-x86_64.rpm) ou
  [AppImage](https://github.com/nab138/iloader/releases/latest/download/iloader-linux-amd64.AppImage)
  (`chmod +x` puis lancez-la). Si l'iPhone n'apparaît pas : arrêtez le service `usbmuxd` et lancez
  `sudo usbmuxd -v -f` dans un autre terminal.

#### 3. Installer SideStore + HealthKit avec iloader

1. Branchez l'iPhone en USB, déverrouillez-le, touchez **Se fier à cet ordinateur** et entrez le code.
2. Ouvrez iloader et connectez-vous avec votre **identifiant Apple** (mot de passe sensible à la casse, puis code de
   double authentification). Le mot de passe part uniquement vers Apple.
3. Choisissez votre iPhone.
4. Choisissez **Import IPA** (et non « Install SideStore (Stable) », qui installe la version sans HealthKit) et
   sélectionnez `SideStore-HealthKit.ipa`. Attendez la fin de l'installation.
5. Toujours dans iloader, ouvrez la gestion des **fichiers d'appairage** (pairing files) et installez-en un pour
   **SideStore** : c'est ce qui permet à SideStore d'installer des apps sans ordinateur ensuite.

#### 4. Autoriser SideStore sur l'iPhone

1. **Faire confiance au profil** : Réglages > Général > **VPN et gestion de l'appareil** > section « App de
   développeur » > votre identifiant Apple > **Faire confiance à …** > **Faire confiance**.
2. **Mode développeur** (une seule fois) : Réglages > Confidentialité et sécurité > tout en bas **Mode développeur** >
   activer. L'iPhone redémarre ; au réveil, confirmez **Activer** et entrez le code.
3. Ouvrez **LocalDevVPN** > **Connect**.
4. Ouvrez **SideStore** et connectez-vous avec le **même identifiant Apple** que dans iloader.
5. Onglet **My Apps** : touchez le compteur **7 DAYS** à côté de SideStore pour le renouveler une première fois ; s'il
   propose de révoquer ou de créer un certificat, répondez **Yes** / **Refresh Now**. SideStore revient à l'écran
   d'accueil avec une notification : c'est normal, rouvrez-le.

#### 5. Installer SuperHealth depuis SideStore

1. LocalDevVPN connecté, ouvrez SideStore > **My Apps** > bouton **+** en haut à gauche.
2. Choisissez `SuperHealth.ipa` dans Fichiers (téléchargez-la d'abord depuis Safari : elle va dans Téléchargements).
3. Attendez la fin de l'installation : SideStore crée l'identifiant d'app **avec la capacité HealthKit** et garde le
   droit Apple Santé à la signature.
4. Ouvrez SuperHealth. Si vous aviez une sauvegarde (étape 0) : rond de votre initiale > **Sauvegarde** >
   **Restaurer un fichier**, puis entrez le code de récupération.

#### 6. Connecter Apple Santé

1. Pendant l'accueil, touchez **Connecter** (ou plus tard : rond de votre initiale en haut à droite > Apple Santé >
   activez l'interrupteur), puis **Tout activer** dans la fenêtre de Santé et **Autoriser**.
2. Vérifiez : Réglages de l'iPhone > Santé > Accès aux données et appareils > **SuperHealth** doit apparaître.
3. Avec une Apple Watch, rien d'autre à faire : elle écrit dans Santé. Pour une autre montre (Garmin, Polar,
   Withings…), activez le partage vers Apple Santé dans son app.

Si « Installée sans le droit Apple Santé » s'affiche encore : vérifiez que SuperHealth a bien été installée **depuis
SideStore + HealthKit** (pas Sideloadly, pas le SideStore officiel), supprimez-la et réinstallez-la depuis SideStore.

#### Renouvellement et mises à jour

- **Tous les 7 jours** : LocalDevVPN connecté, SideStore > My Apps > **Refresh All** (ou le compteur de chaque app).
  Faites-le avant l'expiration ; une app expirée ne s'ouvre plus, mais **ses données restent** et un renouvellement
  la relance.
- **Mise à jour de SuperHealth** : SideStore > My Apps > **+** > nouvelle `SuperHealth.ipa`, **sans supprimer**
  l'ancienne : les données sont conservées.
- **Fichier d'appairage expiré** (après une mise à jour d'iOS, une réinitialisation ou parfois sans raison) :
  SideStore n'arrive plus à installer ; refaites l'étape 3.5 avec iloader.
- **Limites d'un identifiant gratuit** : 3 apps à la fois (SideStore en compte une, SuperHealth une) et 10
  identifiants d'app tous les 7 jours (SuperHealth en utilise 2 : l'app et ses widgets).

Aide : [documentation de SideStore](https://docs.sidestore.io) et sa FAQ.

### Installer avec Sideloadly (sans Apple Santé)

> Avec un identifiant Apple gratuit, Sideloadly retire le droit HealthKit : l'app fonctionne, mais **sans Apple
> Santé** (pas de pas, cœur, sommeil ni Apple Watch). Pour Apple Santé, suivez la méthode SideStore ci-dessus.

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
