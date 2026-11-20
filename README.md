# Relais amis SuperHealth — serveur chiffré

Branche autonome `server/homeserver` : uniquement le petit serveur et son déploiement, sans l'application. L’image GHCR est publiée depuis cette branche ; aucun serveur n’est déployé automatiquement.

Le téléphone calcule son score et chiffre sa carte signée avant envoi. Le relais garde des objets opaques par boîte, sans compte, adresse e-mail, nom, clé publique, score, PR, journal ou parcours GPS en clair. La clé AES-256 de lecture est transmise à un ami dans le QR signé ; elle n'est jamais envoyée au relais. Les URL utilisent deux valeurs aléatoires de 256 bits, sans empreinte d'identité. Les IP et horaires de connexion restent nécessairement visibles dans le transport réseau en cours ; aucune promesse d'anonymat réseau n'est faite.

Le processus n'enregistre ni requêtes, ni IP, ni URL, ni secrets. Désactiver aussi les journaux d'accès du reverse proxy et des outils d'observabilité placés devant lui. Le disque contient des noms de fichiers hachés, le dernier chiffré, sa révision, un horodatage d'expiration, un ETag et le hash du secret d'écriture. Le gestionnaire du NAS peut supprimer ou retarder une carte, mais ne peut pas la déchiffrer sans la clé détenue par les amis ; les téléphones vérifient aussi la signature interne et refusent les révisions anciennes.

Sommaire : [Démarrer](#démarrer) · [Image GitHub](#image-github-et-branche) · [Charge et limites](#charge-et-limites) · [Tests](#tests-et-limites-de-validation) · [Protocole](#protocole) · [Cartes Athlète et récompenses](#économie-des-cartes-athlète) · [Arsenal](#arsenal) · [Base d'aliments](#base-daliments-facultative) · [Retours anonymes](#retours-anonymes-vers-un-github-privé)

## Démarrer

Depuis ce dossier :

```sh
docker compose up -d
```

Le service écoute sur le port 8787. Le volume Docker `relay-data` est créé avec les droits du conteneur : aucun répertoire ni fichier secret à préparer. `GET /health` renvoie `"status":"ok"`, `"protocol":2` et la liste des capacités du serveur (`media`, `social`, `recovery`, `economy`, `arsenal`, `foods`, `feedback`…). Les données persistent après un redémarrage ou `docker compose down` (sans `-v`). Pour mettre à jour une installation existante qui utilisait un dossier monté, conserver ce dossier en définissant `DATA_PATH` vers son chemin absolu et, si nécessaire, `PUID`/`PGID` avec ses propriétaires actuels. Ne pas remplacer ce dossier par un volume vide si ses cartes doivent être conservées.

Mettre à jour = récupérer la nouvelle image puis recréer le service avec le même volume. Ni les cartes existantes, ni leurs clés, ni les amitiés ne sont réinitialisées, et aucune variable nouvelle n'est requise. Une ancienne valeur explicite d'une variable (`MEDIA_QUOTA_BYTES`, `MAX_MEDIA_FILES`…) dans la configuration prend priorité sur les valeurs par défaut.

Un Compose adapté au NAS peut aussi utiliser directement `node:24-alpine` et monter les sources en lecture seule (`server.mjs` et tous les modules `*.mjs` qu'il importe, avec `arsenal-catalogue.json`, dans le même dossier) et le dossier de données en écriture. Cette variante ne nécessite aucune construction d’image ; les chemins, le port et les UID/GID se règlent dans le fichier de déploiement local.

Placer votre reverse proxy HTTPS habituel devant le port choisi. Il doit autoriser 51 Mio par requête et 300 secondes de transfert (vidéos), sans journaliser les URL de capacité. Pour limiter l'écoute à la machine locale, définir `RELAY_BIND=127.0.0.1`. Sans proxy existant, définir `DOMAIN` dans un fichier `.env` local puis lancer :

```sh
docker compose -f compose.yaml -f compose.https.yaml up -d
```

Les ports externes du proxy sont 8080/8443 par défaut, configurables avec `HTTP_PORT` et `HTTPS_PORT`. DNS et routage HTTPS doivent pointer vers l'hôte. Docker démarre le service ; il ne configure pas le DNS ni le routeur.

Les fichiers `compose.truenas*.yaml` restent disponibles pour les installations TrueNAS à chemins et ACL spécifiques (moteur Compose de SCALE/Community 24.10+) ; ils conservent leur configuration de secret explicite.

### Serveur d'essai isolé

`docker compose -f compose.test.yaml up --build -d` construit une image à partir des sources locales, avec son propre réseau et un volume séparé ; elle ne touche pas l’installation existante. Le port local par défaut est 18788 (`GROUP_TEST_PORT` pour le modifier). Un accès depuis un téléphone hors des essais ADB demande votre proxy HTTPS.

### Serveur par défaut et serveur personnalisé

L'app propose le serveur SuperHealth sans en afficher l'adresse. La configuration de distribution vient de `SUPERHEALTH_RELAY_URL` (et éventuellement `SUPERHEALTH_RELAY_KEY`) dans les secrets CI, ou de `local.server.properties` exclu de Git dans le projet Android. Le fichier `.example` contient uniquement les noms des paramètres. Les sources générées sont dans `build/`, également exclu de Git. Une adresse embarquée dans un client reste récupérable techniquement ; elle ne constitue pas un secret d'authentification.

Un serveur personnalisé se règle dans l'app avec une adresse HTTPS et, facultativement, un code de publication. Le serveur standard accepte la création de boîtes sans code partagé ; chaque boîte reste protégée par son secret propriétaire de 256 bits. Pour réserver un serveur privé à un groupe, définir `PUBLISH_KEY` dans le `.env` du serveur (32 à 512 caractères) et saisir ce code dans les clients. Les limites de débit, de capacité et de taille restent actives dans les deux modes.

## Image GitHub et branche

Le workflow `.github/workflows/homeserver-image.yml` ne publie que la branche `server/homeserver`, après les tests HTTP et un démarrage réel du conteneur. L’image multiarchitecture `linux/amd64` et `linux/arm64` est :

```yaml
image: ghcr.io/mamalama0/superhealth-server:server-homeserver
pull_policy: always
```

Le tag `server-homeserver` suit cette branche. Chaque publication fournit aussi un tag `sha-<commit complet>` et l’étiquette OCI de révision. La première publication GHCR peut être privée : dans les paramètres du package GitHub, sélectionner la visibilité publique pour permettre le téléchargement anonyme depuis le NAS. L’image ne contient ni adresse de service privé ni secret.

## Charge et limites

- Un conteneur Node 24, aucune dépendance npm, base SQL, Redis, file de tâches ou websocket. Aucun calcul santé ni déchiffrement sur le NAS.
- CPU plafonné à 0,5 cœur et mémoire à 256 Mio dans Compose ; tas Node plafonné à 160 Mio dans l’image. Ce sont des limites, pas une mesure de consommation réelle.
- Cartes : 500 boîtes par défaut (`MAX_CARDS`, 1..10000, partagé avec les invitations), corps de 96 Kio maximum, dernière carte seulement, expiration après 35 jours sans nouvelle publication. Index des métadonnées en mémoire : un GET conditionnel inchangé renvoie 304 sans lire le fichier.
- Fil social et médias : conservés cinq ans (1830 jours, `SOCIAL_RETENTION_DAYS`, de 365 à 3650). 200 000 événements sociaux (`MAX_SOCIAL_ENTRIES`), dont 50 000 par boîte, plafonnés à 2 Go de chiffrés et métadonnées. Médias : quota global de 100 Go (`MEDIA_QUOTA_BYTES`), 100 000 fichiers (`MAX_MEDIA_FILES`) et 20 000 par boîte (`MAX_MEDIA_PER_MAILBOX`).
- Création : 12 nouvelles boîtes ou copies de récupération par heure et par adresse réseau directe (`MAX_NEW_MAILBOXES_PER_HOUR`).
- Requêtes : 8 actives (`MAX_IN_FLIGHT`, 1..64) et 32 connexions maximum ; 240 requêtes/minute par IP réseau directe (`RATE_LIMIT_PER_MINUTE`, 1..10000). Derrière un proxy, ces limites par adresse s'appliquent à l'IP du proxy donc peuvent être communes à tous les utilisateurs. `X-Forwarded-For` n'est pas utilisé.
- `429/503` demandent un nouvel essai via `Retry-After`, `507` signale un quota atteint.

Prévoir un quota disque supérieur à 100 Go, avec une marge pour les cartes, métadonnées et snapshots. Pour un petit groupe qui actualise 2 à 4 fois par jour et à l'ouverture, le trafic reste de simples petites lectures HTTP. Les clients doivent limiter la fréquence, envoyer `If-None-Match`, conserver les clés en stockage sécurisé et ne publier que les statistiques que leur utilisateur veut partager. Aucune mesure de charge sur un vrai NAS n'a encore été effectuée.

Pour un serveur privé, conserver le contrôle d’admission par `PUBLISH_KEY` ; une distribution publique demande une stratégie d’admission supplémentaire.

## Tests et limites de validation

```sh
node --test server.test.mjs economy.test.mjs arsenal.test.mjs combat.test.mjs faces.test.mjs foods.test.mjs
```

Aucune dépendance npm. Les tests lancent de vrais clients HTTP locaux et chiffrent/déchiffrent en AES-GCM : lecture conditionnelle, redémarrage, absence de nom/clé/URL/code dans le stockage, secrets propriétaires, rejeux/concurrence, taille même en transfert chunked, capacité, expiration et limitation de fréquence. Le test croisé Android → Node → second Android vérifie le chiffrement AES-GCM, la signature et les GET conditionnels.

Les tests Node ne prouvent pas le fonctionnement d'un vrai reverse proxy, du moteur Docker d'un NAS, des ACL TrueNAS ou de la connexion Internet entre deux téléphones.

## Protocole

### Cartes amis

**Route :** `/v2/cards/<mailbox64hex>/<readCapability64hex>`, deux valeurs aléatoires indépendantes. Ni nom ni empreinte de clé dans ce chemin. Le chemin entier est une capacité de lecture à protéger.

**Chiffrement sur le téléphone :** AES-256-GCM, nonce aléatoire de 12 octets à chaque publication, tag 16 octets ajouté à la fin du ciphertext, base64url canonique sans `=`. AAD UTF-8 exacte :

```text
SuperHealth relay v2:/v2/cards/<mailbox64hex>/<readCapability64hex>
```

Le texte chiffré est la carte SH1 signée, avec l'identité et les statistiques (y compris le calcul de progression, que le serveur ne lit pas). La clé AES de 32 octets est incluse seulement dans le partage signé vers un ami, jamais dans la route, les en-têtes ou l'enveloppe HTTP.

**PUT** : `X-Write-Token: <secret propriétaire 64hex>` obligatoire. `Authorization: Bearer <code de publication>` est requis uniquement si `PUBLISH_KEY` est configuré. Le secret est généré sur le téléphone, distinct de la capacité de lecture et de la clé AES ; il ne quitte pas le téléphone sauf pour les opérations d'écriture sur son relais HTTPS. Corps exact :

```json
{"v":2,"revision":1793628000000,"nonce":"base64url-12-octets","ciphertext":"base64url-ciphertext-et-tag"}
```

La révision est un entier de millisecondes monotone par boîte, entre maintenant−7 jours et maintenant+5 minutes à la réception. Le premier PUT réserve la boîte au hash du secret d'écriture. Réponse `204` et `ETag`. Ancienne révision ou valeur différente à révision identique : `409`. Un PUT strictement identique est idempotent, sans écriture disque ni prolongation de la durée de rétention.

**GET** : pas de code global ; l'URL complète suffit. Réponse `200` avec l'enveloppe opaque et `ETag`, ou `304` si `If-None-Match` correspond. Le téléphone déchiffre, vérifie GCM, signature, identité de l'ami et révision avant d'appliquer. Le serveur ne peut pas faire ces vérifications internes. Aucune option de listing ou de recherche d'utilisateurs.

**DELETE** : corps vide, les deux mêmes en-têtes que PUT. `204` idempotent. Supprimer une boîte révoque ses accès et supprime ses médias et son archive (les anciens médias sans rattachement sont collectés à leur expiration). Pour révoquer un ancien lien, supprimer l'ancienne boîte, créer de nouvelles capacité/clé/boîte et rééchanger le QR. Retirer un ami de sa liste n'efface pas une clé qu'il possédait déjà. Une personne à qui des données ont déjà été déchiffrées ne peut pas être forcée à les oublier.

**Ancien protocole :** `/v1/cards/...` et les anciennes cartes en clair ne sont pas acceptés ; les relais v1 ne sont pas compatibles. Ne pas copier un ancien répertoire v1 dans celui-ci : créer un dataset vide, configurer l'app mise à jour, puis échanger un nouveau QR. Les QR/messages/P2P locaux restent séparés ; Google Drive ne sert pas de dépôt amis.

### Codes amis

Les codes comportent 12 caractères base32 sans `I`, `O`, `0` ou `1`, affichés par groupes de quatre. Ils sont générés sur le client, valables 24 heures et renouvelables. Le code lui-même n'est jamais transmis au serveur : une dérivation SHA-256 désigne `/v2/invites/<hash64hex>`, et une dérivation distincte chiffre la carte d'invitation. Les invitations expirent aussi côté serveur.

La confirmation dépose une réponse signée, chiffrée pour l'auteur de l'invitation, dans `/v2/invites/<hash64hex>/responses/<sender64hex>`. Seul le secret propriétaire de l'invitation permet de lire et supprimer ces réponses. Leur collecte permet l'ajout réciproque sans second code. Le serveur conserve au plus 16 réponses de 16 Kio par invitation, limite les routes d'invitation à 12 requêtes/minute par connexion réseau directe et supprime le tout à l'expiration.

Le client consulte les réponses quand son code est affiché, à l'ouverture de la liste d'amis et pendant les synchronisations. La consultation du code s'arrête quand l'écran passe en arrière-plan. Les statistiques et badges restent transmis séparément dans les cartes amis chiffrées.

### Fil social

`social.mjs` conserve séparément les publications, réponses, réactions et profils signés et chiffrés pendant cinq ans. Les journaux de santé ne sont pas envoyés à cette archive. Les résumés d’activités et les PR n’y entrent que lorsqu’une personne choisit de les publier. Les cartes de classement restent des instantanés temporaires de 35 jours.

`GET /v2/cards/<boîte>/<capacité>/social` renvoie au plus 20 événements opaques et un curseur `next`. Reprendre avec `?before=<curseur encodé>`. Un `PUT /social/<id32hex>` requiert le propriétaire ; les révisions anciennes et les reprises divergentes sont refusées. Le client vérifie AES-GCM et la signature SHG1 de l’auteur. Un événement contient une publication, un profil, une réponse, une réaction ou un marqueur de suppression, sans copie des journaux. Le maximum d’un événement de transport est 24 Ko.

Les cercles utilisent un second flux `/social`, avec des capacités indépendantes des cartes de santé. Le relais ne connaît ni les cercles, ni leurs membres, ni leurs clés.

### Médias

Les pièces jointes utilisent `/v2/cards/<mailbox>/<capacité>/media/<identifiant-aléatoire-64hex>`. L'envoi et la suppression exigent la capacité propriétaire de la boîte existante et le code global si configuré. La lecture exige l'URL complète. Chaque fichier est chiffré séparément en AES-256-GCM, avec une clé aléatoire contenue uniquement dans la carte signée chiffrée ; son SHA-256 est vérifié avant déchiffrement. Le format binaire est nonce de 12 octets, ciphertext puis tag de 16 octets, avec AAD `SuperHealth media v1:<identifiant>`. Les fichiers portent seulement un nom dérivé du hash de la route ; les métadonnées sur disque contiennent taille, empreinte du chiffré et expiration.

Photo de 15 Mio et vidéo de 50 Mio côté app, corps chiffré de 50 Mio + 28 octets côté serveur, un seul envoi en cours. Les envois et les lectures sont en flux ; les médias ne sont pas chargés intégralement en mémoire. Les médias restent accessibles quand seule la carte de classement expire ; la réservation propriétaire persiste indépendamment du contenu temporaire.

Un nouvel essai de `PUT /media/<id64hex>` peut fournir `X-Content-SHA256`. Le relais renvoie 204 lorsque la taille et le hachage correspondent exactement au fichier chiffré déjà validé pour cette boîte. Une reprise divergente reçoit 409.

`GET /v2/faces/<id>/card` sert la photo détourée de la carte Athlète (WebP ou PNG transparent, signée par l'auteur), visible par tous les membres comme le visage.

### Changement d'adresse, gel et récupération

`/v2/cards/<ancienne-boîte>/<capacité>/handoffs/<destinataire32hex>` conserve les changements d’adresse individuellement chiffrés pour les amis conservés. Il n’existe pas de liste de ces messages. Après révocation, seule leur lecture reste possible. Les amis ayant déjà échangé leurs nouvelles clés suivent automatiquement le changement ; les anciennes versions nécessitent une nouvelle invitation.

`POST /v2/cards/<boîte>/<capacité>/freeze` nécessite le jeton propriétaire et l’autorisation de publication habituelle. Il fige définitivement les écritures de cette boîte, de ses événements et médias. Les lectures restent possibles pour que les destinataires récupèrent les dernières décisions de départ. `DELETE` reste disponible et révoque ensuite les lectures. Le gel et la révocation persistent après redémarrage. Une autorisation est vérifiée de nouveau après une réception lente pour empêcher une validation après gel ou suppression. `GET /v2/cards/<boîte>/<capacité>/status` renvoie uniquement `revoked` et `frozen` pour une capacité réservée, sans réactiver la boîte.

La rotation des liens personnels utilise ces handoffs. La découverte d’un retrait reste lisible après suppression de la carte principale. Le flux étendu est gelé avant d’attendre les confirmations distantes, puis supprimé. Les données déjà copiées par un destinataire ne peuvent pas être effacées à distance.

`/v2/recovery/<capacité64hex>` conserve une copie chiffrée de l’identité sociale, des contacts, des titres, badges et références d’avatar. La capacité, la clé AES et le secret propriétaire sont dérivés séparément d’un code aléatoire. Ni le code ni les journaux de santé ne sont envoyés. Le propriétaire peut supprimer définitivement cette capacité. Les tombstones de propriété empêchent un ancien appareil de recréer la même boîte après redémarrage du serveur.

## Économie des cartes Athlète

Le portefeuille des cartes, la collection, la fusion des doublons, les échanges entre joueurs, le compteur du pack doré, les quêtes et le tirage des packs sont tenus par le serveur, pour qu'aucune modification locale de l'app ne puisse créer des packs ou des cartes. Code : `economy.mjs`, tests : `node --test economy.test.mjs`. Données : `DATA_DIR/economy/*.json`, sauvegardées avec le reste du volume.

- Route `POST /v2/economy/{compte}` (64 hex), jeton d'écriture `X-Write-Token` (64 hex), et `Authorization` si `PUBLISH_KEY` est défini. Le compte est créé au premier appel, avec les mêmes limites de création par heure que les boîtes.
- Opérations : `state`, `events` (séances et quêtes déclarées, 40 au plus), `open` (booster 0 à 3), `fuse` (fusion des doublons : 2, 4, 16 puis 256 exemplaires, et 20, 40, 80 puis 160 éclats), `trade-browse`, `trade-offer`, `trade-accept`, `trade-decline`, `trade-cancel`. Pas de boutique : une carte ne s'échange que contre une carte de même rareté ou contre des éclats, entre deux comptes ; la mise est gardée de côté sept jours au plus. L'identifiant d'échange d'un compte (son identifiant de stockage) ne permet pas d'y écrire. Chaque requête porte `today`, le jour local de l'app, accepté à un jour près.
- Catalogue : 4 biomes de 36 fonds et 36 duos (180 fonds), aligné sur `Catalog.kt`. Trois cartes par pack, une quatrième une fois sur quatre. Chaque carte est soit un duo (0,5 %, finition normale), soit normale, brillante, animée ou chromatique (voir `RATES.effects`) : rien ne se cumule. Un pack sur dix est doré et triple les chances de rare, d’épique, de duo, d'animée, de chromatique et de Prisme.
- Quêtes : deux ou trois par jour, générées par le serveur pour chaque compte et gardées une fois créées ; leur palier suit les semaines passées. Les quêtes de la semaine rapportent des paquets.
- Défis entre amis : l'app envoie les identifiants d'échange de ses amis (`friends`) ; une fois par semaine, un compte qui a des amis réciproques libres a une chance sur deux de recevoir un défi commun (deux participants, puis 35 %, 12 %, 4 %… pour chaque personne de plus, tous amis entre eux). En course, le premier qui le finit gagne ; ensemble, chacun est récompensé quand tous l'ont fini (`challenge-done`). Données : `DATA_DIR/economy/challenges.json`.
- Badges et séries (événements `badge`, `streak`, `baseline`) : chaque palier de badge est payé une seule fois selon son rang ; chaque série hebdomadaire tenue rapporte 10 éclats par semaine d'affilée (100 au plus), bornée par les semaines écoulées depuis le dernier bonus. `baseline`, une fois par compte, marque comme payés les badges obtenus avant la mise à jour.
- L'app ne déclare que des identifiants opaques et des dates. Le serveur fixe les montants et refuse les doublons et les dates hors fenêtre.

### Récompenses réglables sans mise à jour

Les montants des quêtes, les paquets offerts par les quêtes de la semaine et les défis, le prix d'un pack (100 or par défaut) et les cadeaux de paquets viennent de `rewards.mjs`. Le serveur lit, dans l'ordre : `rewards.json` dans le dossier de données (relu dès qu'il change), puis `REWARDS_URL` (par défaut `rewards.json` de cette branche sur GitHub, relu toutes les 10 minutes ; `REWARDS_URL=` le désactive), sinon les valeurs par défaut. Chaque valeur est bornée. Les applications affichent les montants envoyés par le serveur ; changer une récompense ne demande donc ni nouvelle version de l'app ni nouvelle image.

Un cadeau (`gifts`) est versé une seule fois par compte au premier contact : `{ "id": "noel-2026", "packs": 3, "note": "Joyeux Noël" }`, éventuellement limité à des comptes (`accounts` : identifiants d'échange de 64 caractères hexadécimaux) et à une période (`from`, `until`).

Pour offrir des paquets à une personne précise : dans l'app, ouvrez sa carte Athlète et touchez « ID cadeau » sous la carte pour copier son identifiant, puis `node give.mjs <identifiant> --packs 2 --note "Bravo !"` (plusieurs identifiants possibles, `--eclats`, `--until`, `--file data/rewards.json` pour le fichier du NAS).

## Arsenal

L’image contient `arsenal.mjs`, `combat.mjs` et `arsenal-catalogue.json`. Les comptes et inventaires existants sont conservés d'une version à l'autre.

- Deux boosters : les ouvertures acceptent `booster: WEAPON | ARMOR`. Le champ historique `slot` reste `WEAPON` ou `CHEST`. ARMOR tire parmi CHEST, GUARD et LEGS. Sans `booster`, les anciens clients conservent leur sélection par emplacement. Un réessai restitue le reçu initial, même si la sélection a changé. Un paquet coûte 100 or (`packPrice` de `rewards.json`) ; l'or vient des quêtes et les paquets peuvent être ouverts à la suite.
- Les objets gardent leurs IDs, jets, finitions, niveaux et types de dégâts. `attribute` et `penaltyAttribute` ajoutent FOR/DEX/CON au catalogue ; les anciennes clés de malédiction restent lisibles.
- Duels : `arsenal-v3-duel-invite` (`to`, UUID `request`, `character`, `loadout` facultatif de quatre clés d’objets au plus), puis `arsenal-v3-duel-accept`, `-decline`, `-cancel` (`duel` ; accepter fournit aussi `character` et `loadout`). Invitation et acceptation vérifient l’amitié réciproque. Le duel compare de façon déterministe trois caractéristiques notées sur 100 ; un set complet donne un léger bonus. Aucun or ni objet n’est misé. Les défis expirent après trois jours, les résultats après sept ; huit invitations en attente au maximum par émetteur et 3 000 duels au total. Acceptation et réessais ne rejouent jamais les récompenses.
- Les caractéristiques sont calculées sur le téléphone et déclarées ; le relais borne ces déclarations mais ne certifie pas les performances physiques. Les bonus/malus d’équipement sont recalculés à partir des objets réellement possédés, avec finitions et fusion, puis figés avec les stats pour le résultat. Les journaux de santé ne sont pas transmis.
- Donjon : une seule action par jour, y compris pour reprendre ses forces. Un monstre survivant riposte, la garde réduit les dégâts et le gel les annule. Les champs `dealt`, `received`, `defeatedFloor` décrivent le dernier résultat.
- Boss d'équipe : il tourne chaque lundi. Un membre frappe après avoir terminé ses suivis du jour. Pour le vaincre, l’équipe doit réduire ses PV à zéro et une majorité stricte de ses membres doit terminer trois journées dans la semaine. Chaque membre reçoit alors 300 pièces et deux glaçons, une seule fois.
- Marché : ventes à prix fixe, enchères et offres privées. Les sceaux et paliers de la semaine récompensent les suivis avec des paquets.

Au premier démarrage du serveur 4.0, les cartes de l’Arsenal, leurs ouvertures enregistrées et les annonces ont été remises à zéro (mises d’enchères remboursées, 15 paquets offerts une fois par compte). Garder le volume de données pour conserver les comptes et l’historique de cette migration.

## Base d'aliments (facultative)

Le serveur peut servir une base d'aliments en lecture seule (`GET /v2/foods/search?q=…`, `GET /v2/foods/barcode/<code>`),
cherchée en premier par l'app (« Base SuperHealth »). Elle n'est ni dans le dépôt ni dans l'image : c'est un fichier
`foods.sqlite` placé dans le volume de données.

1. Construire le fichier depuis la base complète (NCC, MND, USDA base et marques ; ≈ 330 Mo, quelques minutes) :
   `node scripts/build-foods.mjs /chemin/vers/foods.db data/foods.sqlite`
2. Copier `data/foods.sqlite` dans le dossier monté sur `/data` (`DATA_PATH`), puis redémarrer le conteneur.
   `/health` répond alors `"foods": 1`. Sans le fichier, la fonction reste désactivée.

Classement : noms qui commencent par la requête d'abord, puis NCC, MND, USDA base et USDA marques. Les fiches
incohérentes (énergie ≠ macros) sont écartées et les colonnes mal nommées de la base source sont corrigées
(voir l'en-tête de `scripts/build-foods.mjs`). Les requêtes ne sont ni journalisées ni conservées.

## Retours anonymes vers un GitHub privé

La route `POST /v2/feedback` accepte une note facultative, un type de retour, un message et une version d’app. Elle ne demande pas de compte GitHub à la personne qui écrit. Le serveur ajoute ces seuls éléments dans une issue du dépôt privé configuré. Les adresses réseau ne sont ni enregistrées ni envoyées à GitHub. La taille est limitée à 8 Kio et le message à 3 000 caractères ; les envois sont limités et un identifiant de soumission évite les doubles clics au sein du processus courant.

Configurer `FEEDBACK_GITHUB_REPO=owner/private-repo` et un jeton dédié `FEEDBACK_GITHUB_TOKEN` limité à ce dépôt avec **Issues: Read and write**. Le jeton reste dans l’environnement privé du serveur, jamais dans l’app ou Git. Une installation gérant les secrets fichiers peut employer `FEEDBACK_GITHUB_TOKEN_FILE` à la place. Le serveur vérifie que le dépôt est privé avant chaque création d’issue. Sans configuration ou si le dépôt devient public, l’envoi est refusé et le relais continue de fonctionner. `/health` indique `feedback:1` quand la passerelle est configurée, sans révéler le dépôt ou le jeton.

## Références et licence

Références consultées : [Apps personnalisées TrueNAS](https://apps.truenas.com/managing-apps/installing-custom-apps/), [stockage Apps](https://apps.truenas.com/getting-started/app-storage/), [services Compose et limites](https://docs.docker.com/reference/compose-file/services/), [secrets Compose](https://docs.docker.com/compose/how-tos/use-secrets/). Aucun secret ni donnée utilisateur n’est fourni dans cette branche. L’image contient uniquement Node, le serveur et sa licence.

Licence : AGPL-3.0-or-later, comme le projet SuperHealth ; voir `LICENSE`. Les PR sur le serveur ciblent `server/homeserver` ([guide de contribution](https://github.com/MamaLama0/SuperHealth/blob/superhealth/CONTRIBUTING.md)).
