# Contenu des plugins

Scripts qui préparent les données des plugins Randonnée, Étirements et Méditation. Les plugins eux-mêmes sont publiés depuis la branche `modules` (`node build.mjs`, voir son README) ; l'app les télécharge depuis son `dist/index.json`.

## Étirements

1. Désassembler le bundle Hermes de l'APK GOWOD (`assets/index.android.bundle`) avec hermes-dec : `hbc-disassembler bundle dis.hasm`.
2. `python3 -I gowod_catalog.py dis.hasm catalog.json` : catalogue et consignes par langue.
3. `python3 -I build_stretch.py catalog.json <modules>/src/stretch` : `exercises.json` et vignettes (curl requis, chaque vidéo est vérifiée).
4. Facultatif : `python3 -I gowod_mobility.py dis.hasm strings.json mobility.json` pour le test de mobilité.

## Méditation

`python3 -I build_meditation_audio.py <modules>/src/meditation <dossier des voix .onnx>` : voix pré-enregistrées avec Piper (fr_FR-siwis-medium, CC BY 4.0 ; en_US-ljspeech-medium, domaine public) et sons de synthèse, sans fichier d'une autre app.

## Randonnée

- `build_hiking_catalog.py` : rafraîchit la couche OpenStreetMap de `src/hiking` en gardant les parcours enrichis (`--offline` pour n'utiliser que les réponses déjà enregistrées dans `Reference/Rando`).
- `build_hiking_bundle.py` : construit `hiking-<version>.zip` dans `dist/` de la branche `modules` et copie la même archive, avec `index.json`, dans `app/src/superhealth/assets/plugins/`. C'est le seul plugin embarqué dans l'APK ; les autres sont téléchargés à la demande.

Ces scripts supposent la branche `modules` extraite dans `../SuperHealth-modules`, à côté de ce dépôt.
