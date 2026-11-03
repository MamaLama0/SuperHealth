# SuperHealth iOS

Version expérimentale de SuperHealth : nutrition, musculation et données via Apple Santé. Une Apple Watch ou une autre montre écrivant dans Santé peut fournir les mesures. La branche Android possède son [propre README](https://github.com/MamaLama0/SuperHealth/tree/superhealth).

## Installer sur iPhone

Prévoir un iPhone compatible, un identifiant Apple, un ordinateur et un câble pour la première installation. Un compte Apple gratuit suffit.

1. Depuis les [versions](https://github.com/MamaLama0/SuperHealth/releases), télécharger `SuperHealth.ipa` de la dernière pré-version `ios-v…` et `SideStore-HealthKit.ipa` de la dernière pré-version `sidestore-healthkit-…`.
2. Sur l’iPhone, installer [LocalDevVPN](https://apps.apple.com/app/localdevvpn/id6755608044) et toucher **Connect**.
3. Sur l’ordinateur, ouvrir [iloader](https://iloader.app), brancher et déverrouiller l’iPhone, puis utiliser **Import IPA** pour installer `SideStore-HealthKit.ipa`. Placer le fichier de jumelage depuis **Manage Pairing File → Place**.
4. Dans les réglages de l’iPhone, autoriser l’application de développeur et activer le **Mode développeur**. Ouvrir SideStore avec le même identifiant Apple et renouveler SideStore une première fois.
5. LocalDevVPN connecté, SideStore → **My Apps → +** : choisir `SuperHealth.ipa`, puis ouvrir SuperHealth et autoriser **Apple Santé**.

Le [guide détaillé](docs/INSTALLATION.md#installer-sur-iphone) explique les réglages, les erreurs fréquentes et les étapes selon le système de l’ordinateur.

### Renouveler et mettre à jour

Avec un compte gratuit, renouveler les apps avant leur expiration de 7 jours : LocalDevVPN connecté, SideStore → **Refresh All**. La limite habituelle est de 3 apps, SideStore compris.

Pour mettre SuperHealth à jour, installer la nouvelle IPA par-dessus l’ancienne, sans la supprimer. Exporter une sauvegarde au préalable. Pour SideStore, utiliser la nouvelle **IPA HealthKit fournie ici** ; le bouton de mise à jour officielle peut retirer ce correctif.

## Données et confidentialité

Les journaux sont conservés sur le téléphone. Les partages entre amis sont facultatifs. Les sauvegardes sont chiffrées. Les modifications sociales, avatars et recettes de la version Android 3.0.4 doivent être portées et vérifiées séparément sur iOS ; elles ne sont pas annoncées comme livrées par cette mise à jour de documentation.

## Développer

- Les PR iOS ciblent `ios` : [guide de contribution commun](https://github.com/MamaLama0/SuperHealth/blob/superhealth/CONTRIBUTING.md).
- GitHub Actions produit l’IPA non signée, ensuite signée à l’installation par SideStore.
- Sur Mac : `cd iosApp && xcodegen generate && open SuperHealth.xcodeproj`.
- Tests communs : `./gradlew :shared:desktopTest`.
- [Notes du portage](docs/UPDATE-3.0-ios.md), [SideStore HealthKit](https://github.com/MamaLama0/SuperHealth/tree/sidestore), [issues](https://github.com/MamaLama0/SuperHealth/issues).
