# Arsenal — 550 cartes vectorielles

Le catalogue actif contient désormais 550 cartes, 66 ensembles complets et
30 pièces signatures. Les 150 ajouts sont décrits dans la dernière section.
Le rendu vectoriel reste celui de l’édition 5 ; la compatibilité réseau passe
à `catalogueVersion: 6` pour identifier les objets de cette extension.

## Base de la refonte — les 400 cartes historiques

La collection active revient aux 400 IDs historiques : 100 armes, 100 torses,
100 protections et 100 équipements de jambes. Elle comprend 64 épiques et
48 légendaires, dont 24 pièces signatures. Les 30 ensembles ont chacun leurs
quatre emplacements et une affinité commune. Les classes ne conditionnent pas
l’équipement ; leurs clés historiques restent compatibles avec les sauvegardes.

Les illustrations actives sont des SVG construits dans `refined-art.js`.
Chaque ID possède une fiche dans `art/arsenal-v5/refinements.json` : construction,
variante, doublure, motif, orientation, nom, type, matériau et intention de correction.
Les 174 profils emploient des familles de tracés partagées ; ce ne sont pas 400
illustrations indépendantes. Les ensembles partagent doublure et héraldique.

La passe reprend notamment :
- luths en poire, rosace, cordes ancrées au chevalet et chevillier coudé ;
- haches avec œil, talon, tranchant et manche traversant ; lances à douille,
  petite pointe et hampe longue ;
- cuirasses à plaques articulées, mailles, broignes, écailles et lamelles ;
- genouillères, cuissards, sandales ouvertes et protections réellement distinctes ;
- ornements des épiques/légendaires : incrustations, héraldique, broderies,
  reliquaires articulés, iris, miroirs et pièces assorties.

Les ouvrages D&D locaux, notamment les descriptions d’armures du Manuel des
Joueurs et les objets du Guide du Maître, ont servi de références de construction
et de thème. Aucune illustration des ouvrages n’est copiée. Les anciens essais
ImageGen et Blender sont conservés comme archives, sans alimenter les nouveaux tirages.

Les cartes gardent nom, bonus, rareté, icônes d’affinité et éventuels effets.
Les quatre affinités sont Mêlée, À distance, Arcaniste et Divine. 38 pièces
épiques/légendaires ont deux affinités. Bénédiction et Malédiction sont des effets
indépendants des affinités et peuvent apparaître ensemble. Couleur, cadre, label
et découpes différencient les raretés. Les six finitions et leurs shaders restent
utilisables ; le masque animé respecte désormais le bas des armes longues.
La résolution du canvas passe de 1,4 à 2 pixels par pixel CSS pendant la lecture
sur les écrans denses, sans augmenter celle de l’animation du paquet.

## Compatibilité et jets

`catalog` contient 400 pièces disponibles. `allCatalog`, `byId` et `loot.json`
contiennent 484 pièces : les 84 objets expérimentaux `atelier-*` restent lisibles,
équipables et conservés lorsqu’ils sont possédés, mais ne sont plus tirés.
Le client annonce `catalogueVersion: 5`. Le catalogue serveur est synchronisé.
Aucun inventaire ni avis n’est réinitialisé par cette refonte.

| Rareté | Jet de base |
| --- | --- |
| Commune | 0,25–0,45 % |
| Peu commune | 0,50–0,80 % |
| Rare | 0,90–1,30 % |
| Épique | 1,50–2,10 % |
| Légendaire | 2,40–3,30 % |

Le jet est aléatoire à chaque acquisition puis sauvegardé. Finition, malédiction
et fusion s’appliquent ensuite sans le relancer. Les bonus de la galerie sont des
aperçus fixes. Les règles de combat et les probabilités de rareté ne changent pas
lors de cette passe visuelle. Une double affinité partage le poids d’une pièce.

## Recueillir les avis

- Galerie : `app/src/superhealth/assets/arsenal/review.html`.
- Copie autonome hors ligne : `review/arsenal-rework/index.html`.
- Notes de 1 à 5, Garder / Retoucher / Retirer, commentaire libre.
- Filtres de rareté, affinité, emplacement, ensemble, verdict et signature.
- Recherche par nom, type, matériau et ID ; reprise de la carte et des filtres.
- Export/import JSON avec validation des notes et verdicts. Un verdict « Retirer »
  ne supprime rien du jeu. Les anciens avis, y compris archivés, restent exportables.
- Sauvegarde locale `superhealth.arsenal.review.v2`, vue `.view`, export schema 1.

Les avis appartiennent au navigateur utilisé. Pour les transmettre, exporter
`arsenal-avis.json`. Aucun faux avis de QA n’est injecté dans le profil utilisateur.

## Contrôles et reconstruction

Les 36 planches avant et après se trouvent dans `review/arsenal-v5/`.
`audit.json` relie chaque ID à sa planche et sa cellule. La comparaison visuelle
porte sur les 400 silhouettes, leur dénomination et leur lisibilité ; l’évaluation
esthétique finale appartient au retour carte par carte de l’utilisateur.

Commandes de reconstruction :

```sh
node tools/build-refined-assets.cjs
node tools/equipment-catalogue.cjs
cp app/src/superhealth/assets/arsenal/loot.json ../SuperHealth-server/arsenal-catalogue.json
node tools/build-relic-review.cjs
```

Contrôles exécutés pour cette version :
- parité du manifeste et des données runtime ; 400 actifs / 84 archivés ;
- 30 ensembles complets, couverture des affinités et 2 400 rendus de finition ;
- rendu Chromium des 400 SVG : pas d’erreur de tracé, noms et bonus dans le cadre,
  silhouettes non coupées ; génération des vignettes de collection ;
- galerie sur ordinateur et à 390 px, persistance, reprise, export/import et rejet
  des entrées invalides ;
- ouverture des six finitions en WebGL et sans WebGL, couches animées et récapitulatif ;
- 39 tests Android ciblés et compilation de l’APK QA ;
- 12 tests serveur, dont jets aléatoires persistants et aller-retour d’inventaire
  avec objets expérimentaux archivés ;
- contrôle sur téléphone QA des six finitions et de la conservation des 147 objets
  présents ainsi que de l’équipement. La version de production n’est pas installée.

Les preuves de contrôle sont internes ; elles ne constituent pas un changelog public.

## Passe visuelle du 11 novembre — bottes, boucliers et raretés

Cette passe reste dans l’édition vectorielle 5 et ne modifie aucun ID, jet,
ensemble, effet de jeu ou inventaire. Les fichiers de comparaison de cette passe
sont regroupés dans `review/arsenal-v6/` ; « v6 » désigne ici l’audit graphique,
pas une migration du catalogue.

- Bottes reconstruites en vue de trois quarts : cheville resserrée, cou-de-pied,
  bout arrondi, trépointe et semelle continue ; paire gauche/droite, solerets à
  lames articulées, sandales ouvertes et ailes placées à l’extérieur.
- Boucliers avec épaisseur, bordure rivetée, panneaux, bandes et renforts. Les
  pièces précieuses reçoivent émaux, filigranes, sertissures et couronnes. Les
  carapaces, vitraux et lanternes ont également été affinés.
- Les 64 épiques et 48 légendaires ont un décor intrinsèque, même en finition
  normale : halo violet ou or, rayons, étoiles, et encadrement renforcé pour les
  légendaires. En lecture, CSS anime halo et scintillements ; WebGL réutilise
  discrètement le shader holographique sur les reflets métalliques, avec des
  particules latérales. Les textes et les icônes restent dégagés.
- Les six finitions restent indépendantes. Bénédiction et Malédiction gardent
  leur signalétique. La préférence de réduction des animations fige les effets.

Vérification de cette passe : 400 SVG rasterisés sans erreur de tracé ni
silhouette/texte hors cadre, 2 400 combinaisons de finition, parité du catalogue,
30 ensembles complets, galerie et persistance des avis à 390 px et sur ordinateur.
Les 36 planches ont été régénérées. Inspection visuelle des 100 protections,
des 100 pièces de jambes, et de toutes les épiques/légendaires des autres familles.

`tools/qa-relic-prestige.cjs` vérifie en plus les cartes normales de trois raretés,
leur récapitulatif, WebGL, le rendu de secours et la réduction des animations.
Le scénario attend le chargement du paquet sélectionné avant de simuler son
ouverture, comme le bouton réel ; un lancement prématuré déclenchait le délai
limite de secours sur le navigateur logiciel de test.

APK QA compilé et mis à jour sur le téléphone. Quatre cartes normales ont été
contrôlées en lecture (commune, épique et légendaires), avec rendu dense et aucune
erreur WebGL. Les courts échantillons requestAnimationFrame mesurent environ
8,33 ms entre callbacks ; ils ne constituent pas un benchmark GPU complet.
Les 147 objets possédés et l’équipement sont identiques avant/après installation
et essais. Seul l’état du donjon a été actualisé par l’application au lancement.


## Extension à 550 cartes — 11 novembre

150 objets ajoutés aux 400 conservés : 40 armes, 36 torses, 38 protections et
36 équipements de jambes. Ils forment 36 ensembles complets supplémentaires
et six pièces singulières. Le total atteint 66 ensembles, 106 épiques,
76 légendaires et 30 pièces signatures. Les 84 anciennes pièces expérimentales
restent archivées et lisibles pour les inventaires qui les possèdent.

`art/arsenal-expansion/items.json` décrit explicitement chaque ajout : nom,
construction, matières, métal, doublure, héraldique, rareté, affinités et ensemble.
Les nouvelles constructions comprennent lyres, harpes, vièles avec archet,
cors, bâtons-lanternes, maillets, vouges, pertuisanes, lances draconiques,
arbalètes, parchemins de garde et cristaux enchâssés. Armures à éclisses,
manteaux, épaulières cérémonielles, boucliers ailés ou à défenses, bottes à
revers et grèves draconiques complètent les familles existantes. Elles partagent
le langage graphique et certaines constructions validées ; chaque pièce n’est
pas une illustration entièrement indépendante.

Les quatre affinités restent indépendantes des effets. Les armes physiques,
instruments et objets rituels ont été vérifiés avec leurs icônes ; les affinités
doubles restent réservées aux pièces précieuses. Les objets d’un ensemble
partagent matières, emblème et affinités. Les épiques et légendaires gardent
leurs halos, rayons, scintillements et détails même sans finition spéciale.
La revue a corrigé notamment les cordes et attaches des instruments, les
ornements d’un fléau, les barreaux masquant les cristaux et les défenses d’un écu.

La galerie contient un filtre « Les 150 nouveautés ». Les clés de sauvegarde,
IDs historiques et schéma d’avis sont conservés. Les 484 définitions précédentes
sont identiques, y compris les valeurs de jeu. Les nouveaux jets utilisent les
mêmes plages par rareté, sont tirés à chaque acquisition et restent enregistrés.
Le serveur réserve les IDs `exp-*` aux clients annonçant la version 6 ; un ancien
client ne les voit pas, mais ils restent sauvegardés et réapparaissent avec un
client compatible. Cette passe modifie les sources du serveur, sans déploiement.

Contrôles de l’extension :
- inspection visuelle des 150 ajouts sur 14 planches, avec seconde inspection
  des dessins corrigés ; correspondance ID/planche/cellule dans
  `review/arsenal-550/audit.json` ;
- rendu des 550 SVG, cadrage des objets et textes, 3 300 combinaisons de finition,
  miniatures de collection régénérées et parité client/serveur ;
- 66 ensembles complets, unicité des noms et conservation des 484 définitions ;
- galerie ordinateur/mobile, filtres, reprise, notes, verdicts, export/import
  et validation des entrées dans un contexte de navigateur isolé ;
- six finitions sur de nouvelles cartes, WebGL et rendu de secours ; test
  complémentaire de raretés normales et de réduction des animations ;
- 15 tests Android ciblés (EquipmentStore et ArsenalGame), compilation APK QA ;
- 13 tests serveur, dont le passage version 5/6, conservation des 150 nouveaux
  objets injectés en fixture avec leurs jets, tirages et reçus persistants ;
- mise à jour de l’APK QA et quatre nouvelles cartes contrôlées sur téléphone :
  commune, épique, deux légendaires ; WebGL actif, aucune erreur, récapitulatif
  complet. Les 155 objets, jets, équipements, compteurs et numéros de série
  présents avant mise à jour sont identiques après essais. Les échantillons
  requestAnimationFrame sont de 8,33 à 8,45 ms, sans prétendre mesurer tout le GPU.
