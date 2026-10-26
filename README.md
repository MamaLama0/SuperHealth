# SuperHealth

Nutrition, musculation et santé dans une seule app, sans compte ni cloud : vos données restent sur le téléphone.

## Fonctionnalités

- **Nutrition** : journal (recherche, code-barres, description ou photo analysée par IA, recettes), calories et macros
  ajustées chaque semaine à votre dépense réelle, tendance du poids, objectif avec un rythme choisi, mesures et photos
  de progression.
- **Musculation** : programmes générés selon vos objectifs, vos disponibilités et votre salle ; séances guidées avec
  charges et répétitions calculées, échauffements et minuteur de repos ; progression, records, muscles travaillés.
- **Santé et activités** : montre connectée (pas, cœur, sommeil, VFC, SpO₂…), scores de récupération, d'effort et
  dette de sommeil, courses avec tracé, zones cardiaques et splits, import GPX, TCX et FIT.
- **Tableau de bord** à composer : cartes en quatre tailles, anneaux du jour, constantes, constats et corrélations.
- **Plugins** : coach IA, planificateur de repas, rappels de médicaments.
- **Widgets**, sauvegardes chiffrées (fichier ou WebDAV), français et anglais.

## Android

APK dans les [versions](https://github.com/MamaLama0/SuperHealth/releases/latest).

## iOS (expérimental)

Build **expérimental**, pas encore testé sur iPhone : même interface et mêmes fonctions qu'Android. Les données de la
montre viennent de la santé de l'iPhone (Apple Watch), avec en plus les anneaux d'activité, les signes vitaux de la
nuit, les alertes cardiaques et ECG, la dynamique de course et la séance en cours sur l'écran verrouillé.

L'`.ipa` n'est pas signée : installez-la avec un outil de sideload, qui la signe avec votre identifiant Apple.
Elle est publiée dans les versions marquées « iOS » ; le code est sur la branche [`ios`](https://github.com/MamaLama0/SuperHealth/tree/ios).
