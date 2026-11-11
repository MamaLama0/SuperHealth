# Arsenal — nouvelle forge

84 objets d’équipement, 21 ensembles complets. Direction : fantasy médiévale,
matériaux lisibles, silhouettes portables et mécaniques compréhensibles.
Les illustrations sont des rendus de géométries Blender écrites dans `build.py`.
Aucun résultat ImageGen, aucune illustration des livres D&D, aucune image du panel
peint précédent n’entre dans les assets de cette collection.

## Sources modifiables

- `items.json` : noms, constructions, matériaux, affinités et ensembles des 84 pièces.
- `build.py` : maillages, assemblages, gravures, matériaux, lumière et caméra.
- `source/*.blend` : un fichier éditable par objet, avec géométries et matériaux.
- `renders/*.png` : rendus 720 × 720, alpha réel, caméra orthographique ajustée aux dimensions.
- `renders/*.webp` : versions 640 × 640 optimisées, alpha conservé.

La génération est déterministe. Les objets ne sont ni étirés ni recadrés pour
remplir les cartes. Les épées possèdent une arête centrale ; les épaulières et
solerets sont composés de lames superposées ; les capes restent ouvertes ; les
courroies, chaînes, ferrures et supports sont explicitement construits.

Les références locales consultées sont le *Manuel des joueurs*, chapitre 5
(équipement, notamment armures, pages imprimées 144–145), et le *Guide du maître*,
chapitre 7 (objets magiques, matériaux, indices visuels de leur propriété).
Elles orientent les familles d’objets et leur construction ; les dessins ne sont
pas extraits des livres.

## Reproduction

Depuis la racine du dépôt Android :

```sh
blender -b -noaudio -t 8 --python art/arsenal-v4/build.py -- --samples 48
python3 tools/build-atelier-assets.py
node tools/equipment-catalogue.cjs
node tools/build-relic-review.cjs
```

`--ids id1,id2` permet de reprendre quelques modèles seulement. Le rendu utilise
AgX. Sur un hôte où Blender fournit un profil OCIO 2.5 mais où OpenColorIO est en
version 2.4, préparer une copie compatible du profil et définir `OCIO` avant le
lancement. Ne pas modifier l’installation système. Les chemins de recherche du
profil doivent pointer vers les LUT de l’installation Blender.

La fin du script quitte explicitement après la sauvegarde des fichiers pour
éviter le blocage de fermeture audio observé sur l’environnement de travail.
Les modèles `.blend` et les grands PNG ne sont pas embarqués dans l’APK : seuls
les WebP encodés, les données et les vignettes le sont.
