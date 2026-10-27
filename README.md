# SideStore + HealthKit (pour SuperHealth iOS)

Avec un identifiant Apple gratuit, Sideloadly, AltStore et SideStore retirent le droit HealthKit en signant l'app :
SuperHealth s'installe, mais Apple Santé répond « Missing com.apple.developer.healthkit entitlement ». Apple permet
pourtant HealthKit aux comptes gratuits ([tableau des capacités iOS](https://developer.apple.com/help/account/reference/supported-capabilities-ios),
colonne « Apple Developer ») : c'est la liste des fonctions de SideSign, le module de signature de SideStore, qui
l'oublie.

Cette branche compile le [SideStore](https://github.com/SideStore/SideStore) officiel (version `0.7.0-alpha`) avec
un seul changement de 6 lignes, [`patch_healthkit.py`](patch_healthkit.py) :

- la capacité HealthKit (`HK421J6T7P`) est activée sur l'identifiant d'app créé pour SuperHealth ;
- les droits `com.apple.developer.healthkit` et `com.apple.developer.healthkit.access` sont gardés à la signature.

Rien d'autre ne change. L'idée vient de [kenantan32/sidestore-healthkit](https://github.com/kenantan32/sidestore-healthkit).

## Télécharger

`SideStore-HealthKit.ipa` est dans la pré-version
[« SideStore 0.7.0-alpha + HealthKit »](https://github.com/MamaLama0/SuperHealth/releases/tag/sidestore-healthkit-0.7.0-alpha),
compilée par GitHub Actions ([`.github/workflows/sidestore.yml`](.github/workflows/sidestore.yml)) à chaque envoi sur
cette branche.

## Installer

Le guide complet est dans le README de la branche [`ios`](https://github.com/MamaLama0/SuperHealth/tree/ios#installer-sur-iphone-avec-sidestore-apple-santé).
En bref : installez `SideStore-HealthKit.ipa` comme un SideStore normal (Sideloadly ou iloader, fichier
d'appairage, LocalDevVPN), puis installez `SuperHealth.ipa` **depuis SideStore**.

## Licence

SideStore est sous licence [AGPL-3.0](https://github.com/SideStore/SideStore/blob/develop/LICENSE) ; cette version
modifiée aussi. Son code source complet = SideStore `0.7.0-alpha` + `patch_healthkit.py`.
