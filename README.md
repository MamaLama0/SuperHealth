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

Build **expérimental** : même interface et mêmes fonctions qu'Android. Les données de la montre viennent d'Apple Santé
(Apple Watch, ou toute montre qui y écrit), avec en plus les anneaux d'activité, les signes vitaux de la nuit, les
alertes cardiaques et ECG, la dynamique de course et la séance en cours sur l'écran verrouillé. Le code est sur la
branche [`ios`](https://github.com/MamaLama0/SuperHealth/tree/ios).

### Installer sur iPhone

SuperHealth n'est pas sur l'App Store : on l'installe avec **[SideStore](https://sidestore.io)**, un magasin d'apps
qui signe les apps avec votre identifiant Apple (gratuit) et les renouvelle lui-même depuis l'iPhone. L'ordinateur ne
sert qu'à la première installation de SideStore.

Il faut **la version « SideStore + HealthKit »** fournie ici, pas le SideStore officiel : avec un identifiant Apple
gratuit, le SideStore officiel (comme Sideloadly ou AltStore) retire le droit HealthKit en signant l'app, et
SuperHealth affiche alors « Installée sans le droit Apple Santé ». Apple autorise pourtant HealthKit aux comptes
gratuits ; la version de la branche [`sidestore`](https://github.com/MamaLama0/SuperHealth/tree/sidestore) est le
SideStore officiel avec ce seul oubli corrigé (6 lignes, code public).

#### Ce qu'il faut

- Un **iPhone sous iOS 16.2 ou plus récent**, avec un code de verrouillage. (Sur iPad, l'app s'ouvre au format iPhone.)
- Un **ordinateur**, pour la première installation seulement : Windows 8 ou plus récent (64 bits), macOS 10.13 ou plus
  récent, ou Linux à jour.
- Un **câble USB** et le **Wi-Fi** sur l'iPhone.
- Un **identifiant Apple** (un compte gratuit suffit ; ce n'est pas forcément celui de l'iPhone).
- Les deux fichiers de la dernière version iOS, [`ios-v1.7.6`](https://github.com/MamaLama0/SuperHealth/releases/tag/ios-v1.7.6)
  (ou une `ios-v…` plus récente dans les [versions](https://github.com/MamaLama0/SuperHealth/releases)) :
  - `SideStore-HealthKit.ipa` : à télécharger **sur l'ordinateur** ;
  - `SuperHealth.ipa` : à télécharger **sur l'iPhone**, avec Safari (elle va dans Fichiers > Téléchargements).

#### Étape 1 — LocalDevVPN sur l'iPhone

1. Installez **[LocalDevVPN](https://apps.apple.com/app/localdevvpn/id6755608044)** depuis l'App Store.
2. Ouvrez-la et touchez **Connect**. iOS demande d'ajouter une configuration VPN : **Autoriser**, puis le code de
   l'iPhone.
3. Ce VPN reste sur l'iPhone (aucun serveur, pas d'effet sur la batterie). Il doit être **connecté chaque fois que
   SideStore installe, met à jour ou renouvelle une app**.

#### Étape 2 — iloader sur l'ordinateur

[iloader](https://iloader.app) installe SideStore et lui donne son fichier d'appairage.

- **Windows** : installez iTunes (Microsoft Store ou [site d'Apple](https://www.apple.com/itunes/download/win64) ; à
  défaut, l'app « Appareils Apple »), puis iloader :
  [MSI](https://github.com/nab138/iloader/releases/latest/download/iloader-windows-x64.msi) (recommandé). Windows 32 bits
  et Windows sur ARM ne sont pas pris en charge.
- **Mac** : installez iloader depuis le [DMG](https://github.com/nab138/iloader/releases/latest/download/iloader-darwin-universal.dmg).
- **Linux** : installez `usbmuxd` avec le gestionnaire de paquets, puis iloader en
  [DEB](https://github.com/nab138/iloader/releases/latest/download/iloader-linux-amd64.deb),
  [RPM](https://github.com/nab138/iloader/releases/latest/download/iloader-linux-x86_64.rpm) ou
  [AppImage](https://github.com/nab138/iloader/releases/latest/download/iloader-linux-amd64.AppImage)
  (`chmod +x` sur le fichier, puis lancez-le).

#### Étape 3 — Installer SideStore + HealthKit avec iloader

1. Branchez l'iPhone en USB et déverrouillez-le. S'il demande **Se fier à cet ordinateur ?**, touchez **Se fier** et
   entrez le code.
2. Ouvrez iloader et connectez-vous avec votre **identifiant Apple** (mot de passe sensible à la casse, puis code de
   double authentification). Le mot de passe ne part que vers Apple.
3. Sélectionnez votre iPhone dans la liste.
4. Cliquez sur **Import IPA** (« Importer IPA » en français) et choisissez `SideStore-HealthKit.ipa`.
   N'utilisez pas « SideStore (Stable) » : il installerait la version officielle, sans HealthKit.
5. À la fin de l'installation, cliquez sur **Manage Pairing File** (« Gérer le fichier de jumelage »), puis sur
   **Place** à droite de **SideStore** : « Pairing file placed successfully! » doit s'afficher en vert. C'est ce
   fichier qui permet ensuite à SideStore d'installer des apps sans ordinateur.

Si l'iPhone n'apparaît pas dans iloader : autre câble ou autre port, redémarrez l'ordinateur ; sous Linux, arrêtez le
service `usbmuxd` puis lancez `sudo usbmuxd -v -f` dans un autre terminal.

#### Étape 4 — Autoriser SideStore sur l'iPhone

1. Réglages > Général > **VPN et gestion de l'appareil** > section « App de développeur » > votre identifiant Apple >
   **Faire confiance à « … »**.
   - iOS 18 et plus récent : touchez **Autoriser et redémarrer**, puis entrez le code.
   - iOS 16 et 17 : touchez **Faire confiance** pour confirmer.
2. Réglages > **Confidentialité et sécurité** > tout en bas, **Mode développeur** > activez. L'iPhone redémarre ; au
   réveil, confirmez **Activer** et entrez le code. (Une seule fois par iPhone.)
3. Ouvrez **LocalDevVPN** > **Connect**.
4. Ouvrez **SideStore** et connectez-vous avec **le même identifiant Apple** que dans iloader.
5. Onglet **My Apps** : touchez le compteur **7 DAYS** à droite de SideStore pour le renouveler une première fois. S'il
   propose de révoquer ou de créer un certificat, touchez **Yes** ou **Refresh Now**. SideStore se ferme et revient à
   l'écran d'accueil avec une notification : c'est normal, il est de nouveau disponible quelques secondes plus tard.
   Faites ce renouvellement **avant d'installer quoi que ce soit d'autre**.

#### Étape 5 — Installer SuperHealth depuis SideStore

1. LocalDevVPN connecté et Wi-Fi actif, ouvrez SideStore > **My Apps** > bouton **+** en haut à gauche.
2. Choisissez `SuperHealth.ipa` (Fichiers > Téléchargements).
3. Attendez la fin de l'installation : SideStore crée l'identifiant d'app **avec la capacité HealthKit**.
4. Ouvrez SuperHealth et suivez l'accueil.

**SuperHealth est déjà installé (par Sideloadly ou AltStore) ?** Ne le supprimez pas : installez simplement
`SuperHealth.ipa` depuis SideStore par-dessus, l'app passe dans SideStore **avec vos données**. Par précaution,
exportez d'abord une sauvegarde : rond de votre initiale en haut à droite > **Sauvegarde** > **Exporter**, gardez le
fichier dans Fichiers et notez le **code de récupération**. (Pour la restaurer : même écran > **Restaurer un
fichier**.)

#### Étape 6 — Connecter Apple Santé

1. Pendant l'accueil, touchez **Connecter** (ou plus tard : rond de votre initiale en haut à droite > **Apple Santé** >
   activez l'interrupteur).
2. Dans la fenêtre de Santé, touchez **Tout activer**, puis **Autoriser**.
3. Vérifiez : Réglages de l'iPhone > Santé > **Accès aux données et appareils** : SuperHealth doit y figurer. C'est là
   aussi qu'on revoit les accès.
4. Avec une Apple Watch, rien d'autre à faire : elle écrit dans Santé. Pour une autre montre (Garmin, Polar,
   Withings…), activez le partage vers Apple Santé dans son app.

#### Tous les 7 jours : renouveler

Avec un identifiant gratuit, les apps installées expirent au bout de **7 jours**. SideStore les renouvelle en
arrière-plan quand il le peut ; pour le faire à la main : LocalDevVPN connecté, SideStore > **My Apps** >
**Refresh All** (ou le compteur de chaque app). Renouvelez avant l'expiration : une app expirée ne s'ouvre plus,
mais **ses données restent**, et un renouvellement la relance.

#### Mettre à jour

- **SuperHealth** : téléchargez la nouvelle `SuperHealth.ipa`, puis SideStore > My Apps > **+** > choisissez-la,
  **sans supprimer** l'ancienne : les données sont conservées.
- **SideStore** : **ne touchez pas au bouton « Update » de SideStore** ; la mise à jour officielle n'a pas le
  correctif HealthKit et SuperHealth perdrait Apple Santé au renouvellement suivant. Installez plutôt la
  `SideStore-HealthKit.ipa` de la dernière version iOS d'ici, depuis SideStore (My Apps > **+**) ou avec iloader
  (Import IPA).

#### Limites d'un identifiant Apple gratuit

- **3 apps** installées par SideStore en même temps, SideStore compris : il reste donc 2 places, dont une pour
  SuperHealth.
- **10 identifiants d'app tous les 7 jours** : SuperHealth en utilise 2 (l'app et ses widgets). Supprimer puis
  réinstaller des apps en consomme ; si la limite est atteinte, attendez quelques jours.
- Un compte développeur payant (99 $/an) lève ces limites et les apps durent 1 an.

#### Problèmes fréquents

| Problème | Solution |
| --- | --- |
| « Installée sans le droit Apple Santé » dans SuperHealth | L'app n'a pas été installée par SideStore + HealthKit (Sideloadly, AltStore, SideStore officiel ou SideStore mis à jour par son bouton « Update »). Installez `SideStore-HealthKit.ipa` (étape 3), puis réinstallez `SuperHealth.ipa` depuis SideStore (étape 5). |
| SuperHealth n'apparaît pas dans Santé > Accès aux données | Même cause : réinstallez depuis SideStore + HealthKit, puis rouvrez Apple Santé dans SuperHealth. |
| « No Wi-Fi or VPN connection » / échec de connexion AFC | Wi-Fi et LocalDevVPN connectés, bloqueurs DNS désactivés, réessayez ; sinon redémarrez SideStore puis LocalDevVPN ; sinon replacez le fichier d'appairage (ligne suivante). |
| SideStore n'installe plus rien (après une mise à jour d'iOS, une réinitialisation, ou sans raison) | Le fichier d'appairage a expiré. Dans iloader, iPhone branché : **Delete Stored Pairing**, sélectionnez l'iPhone et touchez **Se fier**, puis **Manage Pairing File** > **Place** à droite de SideStore. |
| Erreur à la connexion dans SideStore | Souvent le serveur « Anisette » indisponible : réessayez plus tard ou changez « Anisette URL » dans les réglages de SideStore. |
| L'installation reste bloquée en cours de route | Redémarrez SideStore ; sinon videz le cache (réglages de SideStore), redémarrez l'iPhone, replacez le fichier d'appairage, et en dernier recours réinstallez SideStore avec iloader. |
| Une app ne s'ouvre plus | Elle a expiré (7 jours) : SideStore > My Apps > touchez son compteur pour la renouveler. Vos données sont intactes. |
| « Limite de 3 apps » | Supprimez une autre app installée par SideStore. |
| « Your maximum App ID limit has been reached » | 10 identifiants d'app par semaine atteints : attendez quelques jours. |

Plus d'aide : [documentation de SideStore](https://docs.sidestore.io) ([FAQ](https://docs.sidestore.io/docs/faq),
[problèmes courants](https://docs.sidestore.io/docs/troubleshooting/common-issues)) et son
[Discord](https://dis.sidestore.io/). Pour iloader, le Discord d'idevice.

### Construire l'app iOS

- GitHub Actions (`.github/workflows/ios.yml`) produit `SuperHealth.ipa` et une version pour le simulateur.
- Sur un Mac : `cd iosApp && xcodegen generate && open SuperHealth.xcodeproj`.
- Sous Linux : `./gradlew :shared:compileKotlinIosArm64` vérifie le code iOS, `./gradlew :shared:desktopTest` lance les
  tests et `./gradlew :shared:screenshots` rend des captures au format iPhone.
