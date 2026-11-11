# Social, personnage et Arsenal — plan consolidé

Périmètre du 11 novembre 2026 : Android et relais. Le fil reste l’écran social principal. Les duels servent d’abord à comparer la progression sportive. Les types de dégâts restent indépendants des caractéristiques.

## 1. Revue et compatibilité

Examiner les commits locaux récents et la refonte sociale déjà présente dans l’espace de travail. Les derniers commits Android concernent principalement les versions nutrition 3.9/3.9.1 ; l’Arsenal et une partie du social étaient déjà modifiés sans commit. Préserver ce travail, les inventaires, les jets et les sauvegardes existantes.

Les points corrigés pendant cette passe comprennent : anciens libellés de caractéristiques dans les cartes étendues et archivées, incohérence du résumé lors d’une comparaison avec un ancien profil, accès aux combats trop volumineux dans le fil, séances hebdomadaires exclues du déblocage du donjon et riposte malgré le gel.

## 2. Caractéristiques issues du tracking

Trois caractéristiques de personnage, entre 8 et 20 :

| Caractéristique | Pondération nominale |
| --- | --- |
| Force | Performances musculaires 80 %, objectif de musculation 20 % |
| Dextérité | Cardio 65 %, objectif de pas 20 %, esprit et mobilité 15 % |
| Constitution | Objectif de sommeil 45 %, nutrition 25 %, régularité sportive 30 % |

Chaque contribution est ramenée entre 0 et 1. La caractéristique vaut `8 + arrondi(12 × score pondéré)`. Le modificateur vaut `plancher((stat − 10) / 2)`.

Les performances reprennent le moteur sportif existant : séries, force estimée et barèmes, poids corporel, muscles principaux et secondaires, volume, progression et ancienneté ; le cardio combine niveau, fréquence, volume, pas, distance et allure ; la régularité utilise jusqu’à 26 semaines terminées.

Les objectifs utilisent une fenêtre glissante de 28 jours. Les suivis quotidiens comptent les jours validés sur 28 ; la musculation compte les jours avec séance validée sur quatre fois l’objectif hebdomadaire. Une seconde séance le même jour ne crée pas un deuxième jour validé. Les rapports sont plafonnés à 1. Les sources indisponibles ou désactivées sont exclues et les poids disponibles redistribués ; sans source, la valeur est 8. Cela produit des caractéristiques de jeu, pas une évaluation médicale ni une mesure directe de coordination.

La fiche du personnage donne accès à « Comment progressent mes stats ? » : valeurs réellement utilisées, pondérations effectives, contribution de chaque source, résultat, modificateur, date de calcul et description des sous-scores. Les valeurs provisoires sont signalées.

## 3. Deux boosters et adaptation de toutes les cartes

- Deux choix : Armes et Armures. Armures regroupe torses, protections et jambes ; les quatre emplacements d’équipement demeurent.
- Conserver les modèles 3D et l’ouverture ; refaire les habillages, avec une identité cuivre/bleu nuit pour les armes et turquoise pour les armures, plus leurs versions dorées.
- Armes → FOR ; jambes → DEX ; torses et protections → CON. Adapter les malédictions, descriptions, inventaires, vignettes et aperçus.
- Garder Mêlée, À distance, Arcaniste, Divine et les doubles affinités, ainsi que raretés, finitions, ensembles et fusion.
- Conserver les 550 cartes actives et les 84 objets archivés lisibles, avec leurs identifiants et jets. Les bonus complets (finition, fusion et malédiction comprises) restent distincts des stats de base.

## 4. Duels entre amis simples

Un accès compact depuis le fil, des accès depuis le profil et les paquets, puis une arène dédiée. Choisir un adversaire, comparer, voir trois échanges FOR/DEX/CON et un résultat expliqué. Aucun choix de posture, hasard ou avantage de rapidité dans les duels.

Pour chaque caractéristique, la puissance est `stat × (1 + bonus d’équipement / 100)` avec le bonus exprimé en pourcentage. Chaque échange répartit 52 points de dégâts proportionnellement aux deux puissances ; les calculs utilisent des entiers et sont identiques sur Android et le relais. Le résultat compare la santé restante après les trois échanges. L’équipement garde une influence limitée et les statistiques sportives restent prépondérantes.

La comparaison immédiate utilise les derniers profils partagés. Un ancien profil propose un aperçu d’entraînement sans équipement, calculé de façon identique pour les deux participants. Un défi envoyé puis accepté fige les deux profils et les équipements vérifiés côté serveur. Aucun gain ou mise n’encourage à multiplier les duels.

## 5. Combats contre les monstres

Conserver les faiblesses et résistances liées aux types de dégâts. Montrer l’intention du monstre et proposer Assaut, Feinte ou Garde, avec aperçu des dégâts et de la riposte. La Force et la Dextérité participent à l’attaque, la Constitution à la résistance ; la Garde réduit la riposte, le gel l’annule. Rendre le résultat, la progression et le butin lisibles. Une action quotidienne ; une séance de musculation peut la débloquer.

## 6. Feed prioritaire et interactions

Ouvrir le social sur le fil. Garder une petite rangée Arène/Donjon sans grande scène de combat dans le fil. Améliorer la composition de publication, les filtres et l’accès aux réactions avec compteurs ; préserver réponses, audiences et contrôles existants. Les réactions peuvent revenir à la ligne pour éviter le débordement.

## 7. Serveur, migration et vérifications

Le relais annonce les capacités des deux boosters et des combats. Il vérifie l’amitié réciproque, l’appartenance des objets, les bonus complets, les droits d’acceptation/annulation et les réessais. Les invitations expirent après trois jours, les résultats après sept ; les défis restent visibles uniquement par leurs participants. Les caractéristiques sont déclarées par le téléphone et bornées ; elles ne constituent pas une certification des performances. Aucun journal de santé brut n’est envoyé.

Les anciens clients et objets restent lisibles. Le moteur de combat est couvert par des vecteurs communs Kotlin/JavaScript. L’image serveur inclut les nouveaux fichiers et sa CI exécute les tests correspondants.

## État vérifié

- Implémentation présente dans les sources Android et serveur, sans commit ni publication.
- 33 tests Android ciblés réussis : caractéristiques, donjon, équipement, ancien moteur de duel, budget des cartes partagées et traductions.
- 61 tests serveur réussis : relais, économie, équipement, défis, réessais, permissions et combats.
- Catalogue et quatre rendus de boosters contrôlés dans Chromium ; 550 vignettes régénérées. Les contrôles portent aussi sur les 84 cartes archivées et les affinités.
- APK QA compilé et installé sans remise à zéro. Contrôle visuel du fil compact, des boosters, du profil, de la fiche de calcul, d’une comparaison complète et de l’écran du donjon.

Le relais distant n’est pas déployé. Les défis entre deux comptes sont vérifiés par les tests HTTP locaux ; aucun défi réel ni publication n’a été envoyé pendant les essais. L’action de combat contre un monstre est couverte par les tests mais n’a pas été exercée sur le téléphone sans suivi admissible. Aucun test iOS natif ni validation exhaustive d’accessibilité n’est revendiqué pour cette passe.
