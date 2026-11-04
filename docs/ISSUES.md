# Solutions étudiées pour les issues

Revue du 3 novembre 2026. Ce document distingue ce qui est livré sur Android des propositions qui restent à développer. Une issue n’est pas fermée sur la seule base d’une proposition.

## #1 — Créer une recette depuis une sélection

[Demande](https://github.com/MamaLama0/SuperHealth/issues/1) : sélectionner le lait, la banane et le miel déjà journalisés pour sauvegarder un milkshake.

Ajouté pour Android 3.0.4 : appui long dans le journal, sélection des ingrédients, **Créer une recette**, puis nom et portions dans l’éditeur existant. Les poids et nutriments journalisés sont copiés dans le brouillon ; les entrées du journal ne sont pas supprimées ou ajoutées une seconde fois. Si une entrée rapide n’a pas de poids, l’interface demande de le renseigner au préalable. Aucun poids arbitraire n’est inventé.

Vérifications : total de 410 g, calories et micronutriments inchangés pour l’exemple, brouillon indépendant, rejet des poids absents ou invalides. L’équivalent sur iOS reste à porter.

## #2 — Programmes d’étirements, aperçu musculaire et préférences

[Demande](https://github.com/MamaLama0/SuperHealth/issues/2). L’app Android possède déjà `StretchEditor`, des routines sauvegardées, des exercices personnels, une sélection de zones et muscles précis, le choix du matériel, des durées et des favoris. Le générateur de `Stretch.routine` pondère les favoris lorsqu’ils lui sont transmis. Il s’agit d’une génération locale, pas d’une génération IA.

Solution proposée : garder ce moteur comme validateur des routines, ajouter un écran de programme hebdomadaire avec aperçu par groupe musculaire, puis un choix « À privilégier / À éviter ». Enregistrer ces préférences par identifiant d’exercice et les appliquer à tous les points d’entrée de génération. Prévenir si les exclusions ne laissent aucun exercice compatible avec la zone et le matériel. Une IA peut proposer une structure, mais ses identifiants et durées doivent être validés par le catalogue local avant sauvegarde.

Reste à développer : programme hebdomadaire, vue de couverture, exclusions et génération assistée par IA. Les tests de planification existants couvrent déjà le budget de durée, les zones, le matériel et les remplacements.

## #3 — Connecter Strava sans réexporter chaque course

[Demande](https://github.com/MamaLama0/SuperHealth/issues/3). Aujourd’hui, `RunImport` lit GPX, TCX, FIT et archives ZIP ; ce n’est pas une connexion de compte. `HealthConnectHub` et le port iOS lisent aussi les données autorisées des plateformes santé, avec les limites de leurs sources.

Sur la branche Android, les améliorations préparées après la 3.3.1 conservent les séances déjà importées au-delà de la fenêtre relue dans Health Connect. Le retour dans l’app vérifie les nouvelles séances. L’accès de fond peut être accordé séparément dans les réglages sur les versions Android compatibles. Les mesures sont rapprochées par origine et identifiant, et les calories totales de Strava sont conservées séparément des calories actives. Il faut activer le partage Health Connect dans l’app source. Strava peut nécessiter une actualisation de son fil pour envoyer une sortie. [Aide Strava](https://support.strava.com/en-us/articles/15401554-health-connect-and-strava).

Solution proposée : connexion Strava facultative en OAuth dans le navigateur système, lecture des activités puis import incrémental dans la même liste d’activités que les imports de fichiers. Dédupliquer par identifiant Strava et rapprocher les activités déjà importées ; conserver les suppressions et modifications. Les autorisations doivent être explicites, avec déconnexion et révocation. Le flux de jetons doit être conçu avant l’interface : le `client_secret` ne doit pas être inclus dans l’APK ou l’IPA. Un relais OAuth distinct peut effectuer l’échange ; cela demande de préciser sa conservation des jetons et son hébergement. Le relais actuel de cartes chiffrées n’est pas un service OAuth.

Le secret et les échanges requis sont décrits dans la [documentation OAuth Strava](https://developers.strava.com/docs/authentication/). Les [limites de débit et de capacité](https://developers.strava.com/docs/rate-limits/) imposent pagination, reprises sur 429 et validation de l’application avant un déploiement public. Utiliser les [webhooks Strava](https://developers.strava.com/docs/webhooks/) pour les événements plutôt qu’interroger en permanence toutes les activités.

Ne pas réutiliser automatiquement ces données pour le coach IA ou le partage social : vérifier les consentements et les conditions de l’API pour ces usages avant développement. Les activités locales conservent leur partage explicite par aperçu.
