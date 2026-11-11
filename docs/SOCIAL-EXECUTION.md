# Exécution du programme social

Bilan du 4 novembre 2026. Les lots ci-dessous sont implémentés dans les trois dépôts Android, iOS et relais. Les tests locaux et les essais Android sont terminés. La compilation et l’exécution natives iOS ainsi que le déploiement distant du relais restent à effectuer avant distribution.

Les modifications sont présentes dans les espaces de travail, sans commit ni publication distante. Les essais ont utilisé deux profils factices dans l’application isolée `app.superhealth.socialtest`, version `3.2.1-social` (46). Les données de l’application officielle n’ont pas servi aux essais.

## Lots réalisés

### 1. Fil, profil et découverte

- Fil centré sur les publications, actions de partage explicites et états de première utilisation.
- Invitations temporaires, révocation des invitations et confirmation de la relation.
- Aperçu du profil réellement partageable à partir de sa carte signée et bornée.
- Réglages indépendants pour l’indice et ses composantes ; retrait effectif des champs masqués.
- Conservation de la position du fil et chargement progressif de l’historique.

### 2. Publication et fiabilité des envois

- Messages, photos, vidéos, séances, activités et records, avec aperçu de l’audience.
- Brouillons conservés, choix de statistiques et GPS désactivé pour un nouveau brouillon.
- File durable de médias chiffrés, reprise après arrêt du processus ou perte de réseau.
- Identifiants stables, réessais après réponse HTTP perdue, édition sans doublon.
- Suppression et abandon protégés contre une republication par une ancienne opération en attente.
- Écran des envois en attente pour les messages et médias. Limites : 20 publications et 250 Mo de médias en attente.

### 3. Conversations et contrôle de lecture

- Réponses, réactions, édition et suppression des publications personnelles.
- Relais de réponses signées entre amis non mutuels, sans créer de nouvelle amitié.
- Fermeture et réouverture des réponses par l’auteur, masquage d’une réponse.
- Posts masqués, discussions silencieuses, amis en sourdine et écran de gestion de ces choix.
- Notifications groupées et historique local ; ouverture de la publication et de la réponse ciblées.
- Préférences par catégorie et protection contre les alertes sur un ancien historique rechargé.

### 4. Blocage et révocation

- Blocage des identités connues, de leurs alias et des auteurs de réponses relayées.
- Renouvellement des capacités personnelles avec reprise après interruption.
- Exclusion des cercles possédés et départ des autres cercles communs.
- Prise en compte du blocage dans le fil, les réponses et les alertes.

### 5. Cercles privés

- Création, invitation, acceptation explicite, départ, exclusion et fermeture.
- Huit membres au maximum et douze cercles conservés.
- Aucune clé accordée avant acceptation ; nouvelle clé à chaque changement de membres.
- Photos, vidéos, activités, messages, réponses, réactions et modération dans le cercle.
- Flux et clés distincts des cartes de santé ; aperçu final de l’audience avant publication.
- Vérification de la dernière décision du propriétaire avant l’envoi ; attente si elle reste inaccessible.
- Liste de publications virtualisée, réponses chargées progressivement et navigation vers le compositeur.

### 6. Défis collectifs

- Objectif de 1 à 1 000 activités et durée de 1 à 90 jours.
- Choix explicite d’une activité locale admissible ; comptage unique par participant.
- Contribution portant un jeton dérivé d’un secret, propre à l’identité et au cercle.
- Absence de titre, heure de début, statistiques et parcours dans la contribution.
- Retrait possible après clôture ; aucun point de santé ou avantage de classement associé.

### 7. Récupération et données sociales

- Récupération enrichie : identité, contacts, préférences, contrôles de lecture et clés des cercles.
- Compatibilité des anciennes sauvegardes avec partages désactivés par défaut.
- Export chiffré et import avec vérification du code. Les médias distants restent référencés, sans être intégrés à l’archive.
- Suppression des boîtes distantes, révocation de la récupération et nettoyage des données sociales locales, en conservant les journaux de santé.
- Sécurisation après perte d’un appareil : gel des anciens accès, retraits confirmés, nouvelle identité indépendante, nouveau code et migration des publications et médias personnels.
- Parcours persistants, reprise après arrêt et garde contre les opérations concurrentes d’une ancienne identité.

### 8. Relais et protocole

- Événements opaques signés, paquets privés ECDH P-256 et contenu de cercle chiffré par époque.
- Compteur de transport monotone distinct de l’action signée ; reprise sans duplication.
- Pagination conservant plusieurs trous d’historique, y compris après arrivée de nouvelles publications.
- Événements authentifiés différés jusqu’à la réception de leur publication ou défi parent.
- Gel persistant d’un flux, état de capacité révoquée et révocation durable des anciennes boîtes de récupération.
- Réexamen des droits après les envois lents et PUT de médias idempotent par empreinte et taille.
- Compatibilité SH1/SHG1 conservée ; les nouvelles décisions de modération nécessitent des clients compatibles.

### 9. Portage iOS et cohérence entre plateformes

- Moteur portable commun pour cercles, fil, projections d’interface, alertes, pagination et encodage P-256.
- Dix sources synchronisées et contrôlables par `tools/sync-social-core.py --check` ; copie iOS autonome.
- Portage du fil, médias, réponses relayées, réactions, contrôles, cercles, défis, récupération et parcours de données.
- Identités PKCS#8 et ECDH normalisés ; vecteurs cryptographiques et tests d’échanges Android/desktop.
- Transactions des secrets iOS et routage des notifications intégrés au code natif.
- Le calcul local de score iOS reste au modèle 3 ; les comparaisons entre modèles différents sont désactivées.

### 10. Présentation, traduction et validation

- Interfaces françaises et anglaises, dispositions adaptées aux grandes polices et aux thèmes clair/sombre.
- Vérification sur deux téléphones, en portrait et paysage, avec la plus grande taille de texte proposée par l’application.
- APK de diagnostic et APK optimisé avec R8 et réduction des ressources, signés pour l’application de test isolée.
- Documentation fonctionnelle et notes de version courtes dans `SOCIAL.md` et `UPDATE-SOCIAL.md`.

## Vérifications réalisées

| Cible | Résultat |
| --- | --- |
| Suite Android complète | 2 931 cas déclarés : 2 892 réussis, 39 ignorés, aucun échec ni erreur |
| Suite partagée iOS sur desktop | 77 tests réussis, aucun échec ni erreur |
| Relais Node | 24 tests réussis |
| Sources portables | 10 fichiers vérifiés identiques |
| Construction Android | APK de diagnostic et optimisé construits avec succès |
| Derniers ajustements d’interface | Deux APK reconstruits et tests de traduction réussis |

La suite Android complète a été conservée avant les derniers ajustements d’affichage et de libellés. Ces ajustements ont ensuite été compilés dans les deux APK ; le moteur n’a pas changé. Les tests desktop ont été exécutés après l’adaptation des listes. Les parcours fonctionnels ci-dessous ont été exercés avec des profils factices.

- Échange entre deux profils : invitation, admission au cercle, renouvellement de clé, publications et contribution unique à un défi.
- Brouillon après arrêt du processus ; message hors ligne repris une seule fois ; édition conservant son identifiant ; abandon empêchant l’envoi.
- Photo et vidéo reçues ; lecture vidéo ; suppression distante d’une photo.
- Média en attente conservé après arrêt et suppression du fichier source, puis reçu par l’autre profil.
- Modération et fermeture des réponses ; consultation avec plusieurs pages d’historique.
- Récupération en ligne, restauration des réglages, export déchiffré par un outil indépendant, mauvais code refusé et import valide accepté.
- Sécurisation avec contact temporairement absent : ancien flux gelé, reprise après arrêt, retrait confirmé, anciens accès et code invalidés, nouvelle identité fonctionnelle, médias et journaux conservés.
- Suppression sociale : contacts, historique et clés locaux effacés ; anciennes écritures refusées ; journaux de santé conservés.
- Version optimisée lancée sur les deux téléphones, cercles et défi fonctionnels. Vérification indépendante de la signature et du jeton secret d’une contribution réellement produite par cette version.
- Affichage anglais/clair/grands caractères/paysage et français/sombre/portrait vérifiés.

Les fichiers factices de photo, vidéo et d’export créés dans le stockage partagé des téléphones sont retirés à la fin des essais. Les profils de démonstration restent disponibles dans l’application de test.

## Livrables locaux

Le dossier [`dist-social-test`](../../dist-social-test) contient :

- `SuperHealth-social-test-optimized.apk`, version conseillée pour examiner cette livraison ;
- `SuperHealth-social-test.apk`, version de diagnostic ;
- les empreintes SHA-256 correspondantes ;
- [`review/verification.json`](../../dist-social-test/review/verification.json), résultats, portée et empreintes des artefacts ;
- des captures d’écran factices, sans codes de récupération ni clés.

Les deux APK utilisent le relais local de développement. Ils ne constituent pas une configuration de distribution publique. Aucun serveur distant n’a été déployé et aucune version officielle n’a été remplacée.

## Limites et validation extérieure restante

1. **iOS natif** : Linux permet la compilation et les tests desktop du portage, mais pas la compilation Apple ni les essais iPhone. La CI macOS existante et un appareil ou simulateur doivent encore valider Security.framework, Keychain, le sélecteur de médias et les notifications natives avant distribution.
2. **Relais distant** : le serveur doit être déployé avec les nouvelles capacités `recoveryRevocation`, `streamFreeze` et `capabilityStatus`, puis vérifié sur l’environnement cible.
3. **Révocation distribuée** : un retrait peut attendre les autres participants. Les anciennes applications doivent être mises à jour ou révoquer elles-mêmes le lien. Les copies déjà reçues ne peuvent pas être effacées à distance.
4. **Archive sociale** : les fichiers multimédias distants ne sont pas embarqués ; l’export n’est pas une sauvegarde autonome de tous les médias.

Ces limites sont documentées dans les interfaces ou la documentation correspondante. Aucun succès de validation native iOS ni de déploiement distant n’est revendiqué dans ce bilan.
