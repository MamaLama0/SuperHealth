# SideStore + HealthKit (pour SuperHealth iOS)

Avec un identifiant Apple gratuit, Sideloadly, AltStore et SideStore retirent le droit HealthKit en signant l'app :
SuperHealth s'installe, mais Apple Santé répond « Missing com.apple.developer.healthkit entitlement ». Apple permet
pourtant HealthKit aux comptes gratuits ([tableau des capacités iOS](https://developer.apple.com/help/account/reference/supported-capabilities-ios),
colonne « Apple Developer ») : c'est la liste des fonctions de SideSign, le module de signature de SideStore, qui
l'oublie.

Cette branche compile le [SideStore](https://sidestore.io) officiel, dans la version qu'installe iloader avec
« SideStore (Stable) » (la dernière version publiée sur GitHub, aujourd'hui `0.7.0-alpha`), avec un seul changement
de 6 lignes, [`patch_healthkit.py`](patch_healthkit.py) :

- la capacité HealthKit (`HK421J6T7P`) est activée sur l'identifiant d'app créé pour SuperHealth ;
- les droits `com.apple.developer.healthkit` et `com.apple.developer.healthkit.access` sont gardés à la signature.

Rien d'autre ne change. L'idée vient de [kenantan32/sidestore-healthkit](https://github.com/kenantan32/sidestore-healthkit).

## Télécharger

`SideStore-HealthKit.ipa` est jointe à chaque version iOS de SuperHealth (pré-versions `ios-v…` des
[versions](https://github.com/MamaLama0/SuperHealth/releases)) et à la pré-version `sidestore-healthkit-<version>`
compilée par GitHub Actions ([`.github/workflows/sidestore.yml`](.github/workflows/sidestore.yml)) : à chaque envoi
sur cette branche et chaque lundi quand SideStore sort une nouvelle version. La compilation échoue (au lieu de
publier) si le patch ne s'applique plus ou si la capacité HealthKit manque dans le binaire.

## Installer

Le guide complet est dans le README de la branche [`ios`](https://github.com/MamaLama0/SuperHealth/tree/ios#installer-sur-iphone).

**Ne mettez pas SideStore à jour depuis SideStore** (bouton « Update ») : la mise à jour officielle n'a pas le patch
et SuperHealth perdrait Apple Santé au renouvellement suivant. Pour une nouvelle version, installez la nouvelle
`SideStore-HealthKit.ipa` depuis SideStore (My Apps > +) ou avec iloader (Import IPA).

## Licence

SideStore est sous licence [AGPL-3.0](https://github.com/SideStore/SideStore/blob/develop/LICENSE) ; cette version
modifiée aussi. Son code source complet = SideStore à la version indiquée dans le nom de la pré-version +
`patch_healthkit.py`.
