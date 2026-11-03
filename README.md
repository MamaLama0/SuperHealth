# SideStore + HealthKit (pour SuperHealth iOS)

Avec un identifiant Apple gratuit, Sideloadly, AltStore et SideStore retirent le droit HealthKit en signant l'app :
SuperHealth s'installe, mais Apple Santé répond « Missing com.apple.developer.healthkit entitlement ». Apple permet
pourtant HealthKit aux comptes gratuits ([tableau des capacités iOS](https://developer.apple.com/help/account/reference/supported-capabilities-ios),
colonne « Apple Developer ») : c'est la liste des fonctions de SideSign, le module de signature de SideStore, qui
l'oublie.

Cette branche compile le [SideStore](https://sidestore.io) officiel, dans la version qu'installe iloader avec
« SideStore (Stable) » (la dernière version publiée sur GitHub, vérifiée le 3 novembre 2026 : `0.7.0-alpha`), avec le correctif [`patch_healthkit.py`](patch_healthkit.py) :

- la capacité HealthKit (`HK421J6T7P`) est activée sur l'identifiant d'app créé pour SuperHealth ;
- les droits `com.apple.developer.healthkit` et `com.apple.developer.healthkit.access` sont gardés à la signature.

L'idée vient de [kenantan32/sidestore-healthkit](https://github.com/kenantan32/sidestore-healthkit).

Second changement, [`anisette_80046.patch`](anisette_80046.patch) : depuis le 22 octobre 2026, Apple refuse la
connexion de SideStore (« -80045 » / « -80046 » dans Réglages > Compte). Le correctif de
[mariohurtado1402/SideStore](https://github.com/mariohurtado1402/SideStore/commit/15218d18cd68d5259f138b6e040b3b505c6c4289)
(adapté à la version stable) envoie l'identité de chaque serveur anisette au lieu de celle intégrée, utilise un
serveur Mac par défaut (`new.ani.sidestore.io`) et coupe l'anisette sur l'appareil. Il est sauté automatiquement le
jour où SideStore l'inclut.



## Télécharger

`SideStore-HealthKit.ipa` est jointe à chaque version iOS de SuperHealth (pré-versions `ios-v…` des
[versions](https://github.com/MamaLama0/SuperHealth/releases)) et à la pré-version `sidestore-healthkit-<version>-<empreinte des correctifs>`
compilée par GitHub Actions ([`.github/workflows/sidestore.yml`](.github/workflows/sidestore.yml)) : à chaque modification du workflow ou des correctifs, et chaque lundi. Le workflow est également présent sur la branche par défaut `superhealth`, condition nécessaire pour que le cron GitHub fonctionne. Il récupère toujours les sources de la branche `sidestore`. Une nouvelle version amont ou une modification des correctifs produit une nouvelle pré-version ; une compilation hebdomadaire identique est ignorée. La compilation échoue (au lieu de
publier) si le patch ne s'applique plus ou si la capacité HealthKit manque dans le binaire.

## Installer

Le guide complet est dans le README de la branche [`ios`](https://github.com/MamaLama0/SuperHealth/tree/ios#installer-sur-iphone).

**Ne mettez pas SideStore à jour depuis SideStore** (bouton « Update ») : la mise à jour officielle n'a pas le patch
et SuperHealth perdrait Apple Santé au renouvellement suivant. Pour une nouvelle version, installez la nouvelle
`SideStore-HealthKit.ipa` depuis SideStore (My Apps > +) ou avec iloader (Import IPA).

## Licence

SideStore est sous licence [AGPL-3.0](https://github.com/SideStore/SideStore/blob/develop/LICENSE) ; cette version
modifiée aussi. Son code source complet = SideStore à la version indiquée dans le nom de la pré-version +
`patch_healthkit.py` + `anisette_80046.patch`.

## Contribuer

Voir le [guide de contribution](https://github.com/MamaLama0/SuperHealth/blob/superhealth/CONTRIBUTING.md). Les PR de signature et de compilation ciblent `sidestore`. Garder `.github/workflows/sidestore.yml` identique sur `sidestore` et `superhealth` ; cette dernière héberge le déclencheur planifié. La vérification du binaire et la compilation macOS doivent réussir avant d’annoncer une nouvelle IPA disponible.
