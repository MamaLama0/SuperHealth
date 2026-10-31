Contenu des plugins Étirements et Méditation (voir branche `modules`).

1. Désassembler le bundle Hermes de l'APK GOWOD (`assets/index.android.bundle`) avec hermes-dec : `hbc-disassembler bundle dis.hasm`.
2. `python3 -I gowod_catalog.py dis.hasm catalog.json` : catalogue et consignes par langue.
3. `python3 -I build_stretch.py catalog.json <modules>/src/stretch` : exercises.json et vignettes.
4. Sons de Petit BamBou : `assets/audio/` de son APK, copiés dans `src/meditation/audio`.
5. `node build.mjs` dans la branche `modules`, puis copier `dist/` vers `app/src/superhealth/assets/plugins/` (plugins inclus dans l'APK).
