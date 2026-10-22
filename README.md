# Modules SuperHealth

Branche `modules` du dépôt : le contenu des modules téléchargeables depuis l'app (Réglages > Modules).

Un module n'apporte **que des données** (JSON, PNG) lues par un moteur déjà présent dans l'app — jamais de code.
L'app lit `dist/index.json`, télécharge l'archive, vérifie son SHA-256 puis la décompresse dans ses fichiers privés.

| Module | Moteur | Contenu |
|---|---|---|
| `adventure` — Aventure | `adventure` | `world.json` (héros, boss, scénario), `sprites.png` (Kenney Tiny Dungeon, CC0) |
| `coach` — Coach IA | `coach` | `prompts.json` (consignes du coach, tons, point du jour, questions suggérées) |

## Publier une version

1. Modifier `src/<id>/…` et augmenter `version` dans `src/<id>/manifest.json`.
2. `node build.mjs` (régénère `dist/<id>-<version>.zip` et `dist/index.json`).
3. Commit et push de la branche `modules`. Les apps installées se mettent à jour dans la journée (option « Mises à jour automatiques »).

## Source non vérifiée

N'importe quel serveur HTTPS peut publier un `index.json` au même format ; l'app l'affiche comme « non vérifié ».
Champs d'un module : `id`, `kind` (`adventure` ou `coach`), `name`, `summary`, `nameEn`, `summaryEn`, `version`,
`size`, `sha256`, `url` (relative à l'index), `minApp`, `color`.

Crédits : sprites [Kenney](https://kenney.nl) — Tiny Dungeon (CC0).
