# Panel 01 — Le cabinet des merveilles

Ouvrir `index.html` dans un navigateur. Tout fonctionne hors ligne, sans installation ni requête externe. Conserver le dossier entier avec ses illustrations. Les vues proposent une comparaison avant/après en grand, les dix cartes, l’ensemble Griffon royal et une comparaison à taille téléphone. Cliquer sur une carte pour donner une note, un verdict et un commentaire. Export : `arsenal-panel-01-avis.json`.

## Direction artistique

L’objet devient une pièce de collection : volume, relief, assemblages et matière précèdent les effets lumineux. Un fond calme, une lumière de musée et un cadre fin unifient le panel. Les communes sont soignées par leur proportion et leur fabrication ; les épiques et légendaires gagnent une construction particulière, des surfaces travaillées et une silhouette reconnaissable. Les cinq raretés combinent libellé, couleur et motif de cadre.

Le Griffon royal réunit plumes, acier sombre, or pâle et accents grenat. Les différences de matériaux du catalogue restent présentes : damas pour la hallebarde, acier noir et cuir pour le torse, émail rouge et or repoussé pour le pavois, acier riveté et velours pour les jambes. Le lion du pavois est conservé, avec un dessin de crinière qui dialogue avec les plumes du griffon.

| ID stable | Objet | Rareté | Dégâts |
|---|---|---|---|
| `relic-warrior-weapon-v2` | Le Jugement du griffon | Légendaire | Mêlée + Divine |
| `relic-warrior-chest-v2` | La Cuirasse des cent rois | Épique | Mêlée |
| `relic-warrior-guard-v2` | Le Lion de la porte rouge | Légendaire | Mêlée + Divine |
| `relic-warrior-legs-v2` | La Marche des cataphractes | Épique | Mêlée |
| `4-weapon-0` | Hachette du guet | Commune | Mêlée |
| `3-legs-1` | Sandales du chemin des ermites | Peu commune | Divine |
| `5-weapon-2` | Arbalète du sonneur | Rare | À distance |
| `9-weapon-3` | Luth du dernier souffle | Épique | Arcaniste |
| `3-weapon-3` | Arc de la Rose éternelle | Épique | À distance + Arcaniste |
| `relic-priest-weapon-v2` | Le Chant du premier soleil | Légendaire | Divine |

Choisir Bénédiction sur le pavois pour voir les deux effets ensemble. Choisir Bénédiction sur le luth pour voir un Arcaniste béni, sans malédiction. L’encensoir et les sandales sont Divine sans être bénis par défaut. La pénalité du pavois est celle du catalogue ; aucune malédiction n’est ajoutée aux autres pièces. Les icônes utilisent exactement les chemins SVG de `RelicCuration.types`, également employés dans l’application.

## Isolation et provenance

Seul ce nouveau dossier est écrit. Aucun fichier préexistant de l’application, du backend, de l’atelier ou du bestiaire n’est remplacé. Les bases locales consultées sont Android `feb0ea2d` et serveur `25bcce62`, ainsi que les changements non commités et `docs/ARSENAL-REWORK.md`.

- `build.cjs` lit les scripts actuels dans une VM sans DOM, puis capture uniquement les dix objets retenus. Il ne lance aucun tirage et ne produit aucun changement dans `loot.json` ou le serveur.
- `data.js` contient les données et les faces avant aux finitions Normale, Brillante et Bénédiction. `selection.json` conserve les métadonnées complètes des objets choisis.
- `art/` contient les dix illustrations originales PNG. Outil utilisé : ImageGen intégré. Les prompts exacts sont dans `prompts.json`.
- `panel.js` compose les propositions avec des textes, pictogrammes et cadres vectoriels indépendants des illustrations. Les images ne sont pas déformées ni recadrées ; un fondu de bord léger les relie au fond.
- Les valeurs de bonus sont calculées par `ArsenalCatalog.effect` à partir du jet d’aperçu existant, avec la même finition avant et après. Elles ne représentent pas un nouveau tirage. Le stockage des jets aléatoires et leur conservation définitive ne sont pas touchés.
- Les avis utilisent une nouvelle clé `superhealth.arsenal.panel.v1.reviews`. Aucune lecture ni écriture de la clé de l’atelier `superhealth.arsenal.review.v2`. Les imports du panel sont distincts des anciens exports pour éviter de mélanger des avis sur deux directions artistiques.

## Limites de cette étape et suite éventuelle

Aucune intégration Android, aucun APK ni déploiement serveur. La vue téléphone est une simulation navigateur, pas une capture d’un appareil physique. Les finitions Brillante et Bénédiction sont des aperçus visuels ; le reflet Brillant est une couche CSS sobre et respecte les animations réduites. Les shaders GLSL et les autres finitions du jeu restent en place, inchangés.

Si la direction est retenue, l’intégration devra prévoir des illustrations WebP optimisées, des masques d’objet/de matière pour appliquer les shaders sans éclairer le fond, et éventuellement des couches distinctes pour l’animation. Ces adaptations devront se brancher sur les IDs existants sans toucher à la génération des bonus. Les PNG actuels servent à valider l’art, pas à alourdir l’APK. Le cadre légendaire pourrait ensuite recevoir une gravure propre à chaque ensemble. Aucune extension aux 400 cartes n’est engagée.

## Vérification et captures

`verify.cjs` contrôle la sélection, les types, les valeurs identiques, les textes dans le cadre, les effets indépendants, les avis, le rechargement, les imports invalides, l’export sans stockage et les vues à 390/320 px. Les résultats sont dans `verification.json`. Ce test emploie un profil navigateur temporaire, sans modifier les avis de l’utilisateur.

Captures : `panel-10-cartes.png`, `ensemble-griffon.png`, `comparaison-desktop.png`, `comparaison-telephone.png`, `effets-coexistants.png`, et une comparaison grand format par objet dans `comparaisons/`.

Pour régénérer les données depuis l’état courant : `node build.cjs`. Pour vérifier avec une installation existante de Playwright : `node verify.cjs` (configurer `NODE_PATH` et, si nécessaire, `CHROMIUM_PATH`). Le panel reste consultable sans ces outils. Les avis existants du panel survivent à la régénération grâce aux IDs stables.

Pour annuler cette exploration : retirer uniquement le dossier `arsenal-panel-v1`. Aucun retour arrière dans les fichiers du jeu n’est requis ; exporter ses avis avant de retirer le dossier.
