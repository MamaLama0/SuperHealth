## Groupe 3.0.6 en test

Essai isolé avec les sources locales : `docker compose -f compose.test.yaml up --build -d`.
Cette variante crée sa propre image, son réseau et un volume séparé ; elle ne met pas à jour l’installation existante. Le port local par défaut est 18788 (`GROUP_TEST_PORT` pour le modifier). Un accès depuis un téléphone hors des essais ADB demande votre proxy HTTPS. `node --test server.test.mjs` exécute les contrôles du protocole sans dépendance npm.

Le nouveau module `social.mjs` conserve séparément les publications, réponses, réactions et profils signés et chiffrés pendant cinq ans (1830 jours par défaut, `SOCIAL_RETENTION_DAYS`, de 365 à 3650). Les journaux de santé ne sont pas envoyés à cette archive. Les résumés d’activités et les PR n’y entrent que lorsqu’une personne choisit de les publier. Les cartes de classement restent des instantanés temporaires de 35 jours.

`GET /v2/cards/<boîte>/<capacité>/social` renvoie au plus 20 événements opaques et un curseur `next`. Reprendre avec `?before=<curseur encodé>`. Un `PUT /social/<id32hex>` requiert le propriétaire ; les révisions anciennes et les reprises divergentes sont refusées. Le client vérifie AES-GCM et la signature SHG1 de l’auteur. Un événement contient une publication, un profil, une réponse, une réaction ou un marqueur de suppression, sans copie des journaux.

Les médias ont la même rétention de cinq ans et restent accessibles quand seule la carte de classement expire. La réservation propriétaire persiste indépendamment du contenu temporaire. Supprimer une boîte révoque ses accès et supprime ses nouveaux médias et son archive. Les anciens médias sans métadonnée de rattachement seront encore collectés à leur expiration ; une migration de liens recopie les médias connus avant la révocation.

`/v2/cards/<ancienne-boîte>/<capacité>/handoffs/<destinataire32hex>` conserve les changements d’adresse individuellement chiffrés pour les amis conservés. Il n’existe pas de liste de ces messages. Après révocation, seule leur lecture reste possible. Les amis ayant déjà échangé leurs nouvelles clés suivent automatiquement le changement ; les anciennes versions nécessitent une nouvelle invitation.

`/v2/recovery/<capacité64hex>` conserve une copie chiffrée de l’identité sociale, des contacts, des titres, badges et références d’avatar. La capacité, la clé AES et le secret propriétaire sont dérivés séparément d’un code aléatoire. Ni le code ni les journaux de santé ne sont envoyés. Cette récupération ne remplace pas un système complet de gestion et révocation des appareils.

Les paramètres par défaut sont : quota médias 100 Go, 100000 fichiers, 20000 médias par boîte, 200000 événements sociaux et 50000 événements par boîte. Le quota des événements est séparé, plafonné à 2 Go de chiffrés et métadonnées. Le tas Node est plafonné à 160 Mio dans l’image, dans le conteneur de 256 Mio. Ces limites ne sont pas des mesures de charge. Le quota de création est de 12 nouvelles boîtes ou copies de récupération par heure et par adresse réseau directe (`MAX_NEW_MAILBOXES_PER_HOUR`). Derrière un proxy, cette limite peut être partagée. Pour un serveur privé, conserver le contrôle d’admission par `PUBLISH_KEY` ; une distribution publique demande une stratégie d’admission supplémentaire.

`GET /health` annonce `social:1`, `recovery:1` et `socialRetentionDays`. Une app 3.0.6 doit utiliser ce serveur pour le nouveau fil. Le volume existant est conservé ; de nouveaux dossiers `owners/`, `social/` et `handoffs/` sont créés. Le Dockerfile doit embarquer `social.mjs` en plus des trois autres modules. Aucun déploiement n’est effectué par la préparation locale de la version de test.

Les limites et comportements ci-dessus remplacent les anciennes descriptions du fil à 12 posts et des médias à 35 jours figurant dans l’historique ci-dessous. Les formats v2 existants sont conservés. Les 12 publications encore disponibles sont importées vers le nouvel historique ; un contenu déjà effacé par une ancienne version n’est pas recréé.

---

# Relais amis SuperHealth — serveur chiffré

Branche autonome `server/homeserver` : uniquement le petit serveur et son déploiement, sans l'application Android. L’image GHCR est publiée depuis cette branche ; aucun serveur n’est déployé automatiquement.

Le téléphone calcule son score et chiffre sa carte signée avant envoi. Le relais garde **un seul objet opaque par boîte**, sans compte, adresse e-mail, nom, clé publique, score, PR, journal ou parcours GPS en clair. La clé AES-256 de lecture est transmise à un ami dans le QR signé ; elle n'est jamais envoyée au relais. Les URL utilisent deux valeurs aléatoires de 256 bits, sans empreinte d'identité. Les IP et horaires de connexion restent nécessairement visibles dans le transport réseau en cours ; aucune promesse d'anonymat réseau n'est faite.

Le processus n'enregistre ni requêtes, ni IP, ni URL, ni secrets. Désactiver aussi les journaux d'accès du reverse proxy et des outils d'observabilité placés devant lui. Le disque contient des noms de fichiers hachés, le dernier chiffré, sa révision, un horodatage d'expiration, un ETag et le hash du secret d'écriture. Le gestionnaire du NAS peut supprimer ou retarder une carte, mais ne peut pas la déchiffrer sans la clé détenue par les amis ; les téléphones vérifient aussi la signature interne et refusent les révisions anciennes.

## Image GitHub et branche

Le workflow `.github/workflows/homeserver-image.yml` ne publie que la branche `server/homeserver`, après les tests HTTP et un démarrage réel du conteneur. L’image multiarchitecture `linux/amd64` et `linux/arm64` est :

```yaml
image: ghcr.io/mamalama0/superhealth-server:server-homeserver
pull_policy: always
```

Le tag `server-homeserver` suit cette branche. Chaque publication fournit aussi un tag `sha-<commit complet>` et l’étiquette OCI de révision. La première publication GHCR peut être privée : dans les paramètres du package GitHub, sélectionner la visibilité publique pour permettre le téléchargement anonyme depuis le NAS. L’image ne contient ni adresse de service privé ni secret.

## Charge et limites

- Un conteneur Node 24, aucune dépendance npm, base SQL, Redis, file de tâches ou websocket.
- CPU plafonné à 0,5 cœur et mémoire à 256 Mio dans Compose ; tas Node plafonné à 96 Mio. Ce sont des limites, pas une mesure de consommation réelle.
- 500 boîtes par défaut, corps de 96 Kio maximum, dernière carte seulement, expiration après 35 jours sans nouvelle publication. Au maximum environ 48 Mio de corps de cartes, plus les petits fichiers de métadonnées ; ce budget ne couvre pas les médias ni les réponses aux invitations. Prévoir un quota disque supérieur au quota médias, avec une marge pour ces données et les snapshots.
- Index des métadonnées en mémoire : un GET conditionnel inchangé renvoie 304 sans lire le fichier. Aucun calcul santé ni déchiffrement sur le NAS.
- 8 requêtes actives et 32 connexions maximum ; 240 requêtes/minute par IP réseau directe. Derrière un proxy, cette dernière limite s'applique à l'IP du proxy donc peut être commune à tous les utilisateurs. `X-Forwarded-For` n'est pas utilisé.
- `MAX_CARDS` (1..10000), `MAX_IN_FLIGHT` (1..64) et `RATE_LIMIT_PER_MINUTE` (1..10000) sont configurables. `429/503` demandent un nouvel essai via `Retry-After`, `507` signale le quota de boîtes.

Pour un petit groupe qui actualise 2 à 4 fois par jour et à l'ouverture, le trafic reste de simples petites lectures HTTP. Les clients doivent limiter la fréquence, envoyer `If-None-Match`, conserver les clés en stockage sécurisé et ne publier que les statistiques que leur utilisateur veut partager. Aucune mesure sur un vrai TrueNAS n'a encore été effectuée.

## Démarrer

Depuis ce dossier :

```sh
docker compose up -d
```

Le service écoute sur le port 8787. Le volume Docker `relay-data` est créé avec les droits du conteneur : aucun répertoire ni fichier secret à préparer. `GET /health` renvoie `{"status":"ok","protocol":2}`. Les données persistent après un redémarrage ou `docker compose down` (sans `-v`). Pour mettre à jour une installation existante qui utilisait un dossier monté, conserver ce dossier en définissant `DATA_PATH` vers son chemin absolu et, si nécessaire, `PUID`/`PGID` avec ses propriétaires actuels. Ne pas remplacer ce dossier par un volume vide si ses cartes doivent être conservées.

Un Compose adapté au NAS peut aussi utiliser directement `node:24-alpine`, monter `server.mjs` en lecture seule et le dossier de données en écriture. Cette variante ne nécessite aucune construction d’image ; les chemins, le port et les UID/GID se règlent dans le fichier de déploiement local.

Placer votre reverse proxy HTTPS habituel devant le port choisi. Pour limiter l'écoute à la machine locale, définir `RELAY_BIND=127.0.0.1`. Sans proxy existant, définir `DOMAIN` dans un fichier `.env` local puis lancer :

```sh
docker compose -f compose.yaml -f compose.https.yaml up -d
```

Les ports externes du proxy sont 8080/8443 par défaut, configurables avec `HTTP_PORT` et `HTTPS_PORT`. DNS et routage HTTPS doivent pointer vers l'hôte. Docker démarre le service ; il ne configure pas le DNS ni le routeur.

### Serveur par défaut et serveur personnalisé

L'app propose le serveur SuperHealth sans en afficher l'adresse. La configuration de distribution vient de `SUPERHEALTH_RELAY_URL` (et éventuellement `SUPERHEALTH_RELAY_KEY`) dans les secrets CI, ou de `local.server.properties` exclu de Git dans le projet Android. Le fichier `.example` contient uniquement les noms des paramètres. Les sources générées sont dans `build/`, également exclu de Git. Une adresse embarquée dans un client reste récupérable techniquement ; elle ne constitue pas un secret d'authentification.

Un serveur personnalisé se règle dans l'app avec une adresse HTTPS et, facultativement, un code de publication. Le serveur standard accepte la création de boîtes sans code partagé ; chaque boîte reste protégée par son secret propriétaire de 256 bits. Pour réserver un serveur privé à un groupe, définir `PUBLISH_KEY` dans le `.env` du serveur (32 à 512 caractères) et saisir ce code dans les clients. Les limites de débit, de capacité et de taille restent actives dans les deux modes.

Les fichiers `compose.truenas*.yaml` restent disponibles pour les installations TrueNAS à chemins et ACL spécifiques ; ils conservent leur configuration de secret explicite.

## Compatibilité SuperHealth 2.8

Le client Android 2.8 utilise le contrat chiffré v2 ci-dessous, y compris avec le nouveau calcul de progression modèle 2. Ce modèle reste à l'intérieur de la carte chiffrée : le serveur ne lit ni les XP, ni les pourcentages nutritionnels, ni les objectifs de séances. Aucun changement de base ou de conteneur n'est nécessaire pour ces règles. Les anciens relais v1 ne sont pas compatibles.

Le test croisé Android → Node → second Android vérifie le chiffrement AES-GCM, la signature et les GET conditionnels. Les tests HTTP sont relançables avec `node --test server.test.mjs`. Les fichiers TrueNAS visent le moteur Compose de SCALE/Community 24.10+ ; les chemins, ACL et HTTPS restent à configurer sur le NAS.

## Contrat du client Android

**Route :** `/v2/cards/<mailbox64hex>/<readCapability64hex>`, deux valeurs aléatoires indépendantes. Ni nom ni empreinte de clé dans ce chemin. Le chemin entier est une capacité de lecture à protéger.

**Chiffrement sur le téléphone :** AES-256-GCM, nonce aléatoire de 12 octets à chaque publication, tag 16 octets ajouté à la fin du ciphertext, base64url canonique sans `=`. AAD UTF-8 exacte :

```text
SuperHealth relay v2:/v2/cards/<mailbox64hex>/<readCapability64hex>
```

Le texte chiffré est la carte SH1 signée, avec l'identité et les statistiques. La clé AES de 32 octets est incluse seulement dans le partage signé vers un ami, jamais dans la route, les en-têtes ou l'enveloppe HTTP.

**PUT** : `X-Write-Token: <secret propriétaire 64hex>` obligatoire. `Authorization: Bearer <code de publication>` est requis uniquement si `PUBLISH_KEY` est configuré. Ce dernier est généré sur le téléphone, distinct de la capacité de lecture et de la clé AES ; il ne quitte pas le téléphone sauf pour les opérations d'écriture sur son relais HTTPS. Corps exact :

```json
{"v":2,"revision":1793628000000,"nonce":"base64url-12-octets","ciphertext":"base64url-ciphertext-et-tag"}
```

La révision est un entier de millisecondes monotone par boîte, entre maintenant−7 jours et maintenant+5 minutes à la réception. Le premier PUT réserve la boîte au hash du secret d'écriture. Réponse `204` et `ETag`. Ancienne révision ou valeur différente à révision identique : `409`. Un PUT strictement identique est idempotent, sans écriture disque ni prolongation de la durée de rétention.

**GET** : pas de code global ; l'URL complète suffit. Réponse `200` avec l'enveloppe opaque et `ETag`, ou `304` si `If-None-Match` correspond. Le téléphone déchiffre, vérifie GCM, signature, identité de l'ami et révision avant d'appliquer. Le serveur ne peut pas faire ces vérifications internes. Aucune option de listing ou de recherche d'utilisateurs.

**DELETE** : corps vide, les deux mêmes en-têtes que PUT. `204` idempotent. Pour révoquer un ancien lien, supprimer l'ancienne boîte, créer de nouvelles capacité/clé/boîte et rééchanger le QR. Retirer un ami de sa liste n'efface pas une clé qu'il possédait déjà. Une personne à qui des données ont déjà été déchiffrées ne peut pas être forcée à les oublier.

**Migration :** `/v1/cards/...` et les anciennes cartes en clair ne sont pas acceptés. Ne pas copier l'ancien répertoire de données dans celui-ci : les formats sont volontairement distincts. Créer un dataset vide pour v2, configurer l'app mise à jour, puis échanger un nouveau QR. Les QR/messages/P2P locaux restent séparés ; Google Drive ne sert pas de dépôt amis.

## Tests et limites de validation

```sh
node --test server.test.mjs
```

Les tests lancent de vrais clients HTTP locaux et chiffrent/déchiffrent en AES-GCM : lecture conditionnelle, redémarrage, absence de nom/clé/URL/code dans le stockage, secrets propriétaires, rejeux/concurrence, taille même en transfert chunked, capacité, expiration et limitation de fréquence.

Le protocole et les fichiers Compose sont livrés sans déploiement ni compte réel. Les tests Node ne prouvent pas le fonctionnement d'un vrai reverse proxy, du moteur Docker d'un NAS, des ACL TrueNAS ou de la connexion Internet entre deux téléphones.

Références consultées : [Apps personnalisées TrueNAS](https://apps.truenas.com/managing-apps/installing-custom-apps/), [stockage Apps](https://apps.truenas.com/getting-started/app-storage/), [services Compose et limites](https://docs.docker.com/reference/compose-file/services/), [secrets Compose](https://docs.docker.com/compose/how-tos/use-secrets/). Aucun secret ni donnée utilisateur n’est fourni dans cette branche. L’image contient uniquement Node, le serveur et sa licence.

Licence : AGPL-3.0-or-later, comme le projet SuperHealth ; voir `LICENSE`.

## Codes amis

Les codes comportent 12 caractères base32 sans `I`, `O`, `0` ou `1`, affichés par groupes de quatre. Ils sont générés sur le client, valables 24 heures et renouvelables. Le code lui-même n'est jamais transmis au serveur : une dérivation SHA-256 désigne `/v2/invites/<hash64hex>`, et une dérivation distincte chiffre la carte d'invitation. Les invitations expirent aussi côté serveur.

La confirmation dépose une réponse signée, chiffrée pour l'auteur de l'invitation, dans `/v2/invites/<hash64hex>/responses/<sender64hex>`. Seul le secret propriétaire de l'invitation permet de lire et supprimer ces réponses. Leur collecte permet l'ajout réciproque sans second code. Le serveur conserve au plus 16 réponses de 16 Kio par invitation, limite les routes d'invitation à 12 requêtes/minute par connexion réseau directe et supprime le tout à l'expiration. Les boîtes et invitations partagent le quota `MAX_CARDS`.

Le client consulte les réponses quand son code est affiché, à l'ouverture de la liste d'amis et pendant les synchronisations. La consultation du code s'arrête quand l'écran passe en arrière-plan. Les statistiques et badges restent transmis séparément dans les cartes amis chiffrées.

## SuperHealth 3.0.1 — publications entre amis

Les publications choisies par l'utilisateur font partie de sa carte signée et chiffrée : message, résumé de séance/course/série et descripteur de média. Au plus 12 publications des 34 derniers jours, sans tracé GPS dans les cartes d'activité. Le serveur ne lit jamais ces informations. Les clients d'avant 3.0.1 conservent leurs amitiés ; ils doivent être mis à jour pour lire le nouveau modèle de score et le fil.

Les pièces jointes utilisent `/v2/cards/<mailbox>/<capacité>/media/<identifiant-aléatoire-64hex>`. L'envoi et la suppression exigent la capacité propriétaire de la boîte existante et le code global si configuré. La lecture exige l'URL complète. Chaque fichier est chiffré séparément en AES-256-GCM, avec une clé aléatoire contenue uniquement dans la carte signée chiffrée ; son SHA-256 est vérifié avant déchiffrement. Le format binaire est nonce de 12 octets, ciphertext puis tag de 16 octets, avec AAD `SuperHealth media v1:<identifiant>`. Les fichiers portent seulement un nom dérivé du hash de la route ; les métadonnées sur disque contiennent taille, empreinte du chiffré et expiration.

Limites : photo de 15 Mio et vidéo de 50 Mio côté app, corps chiffré de 50 Mio + 28 octets côté serveur, un seul envoi en cours, quota global de **100 Go** (100 000 000 000 octets, configurable par `MEDIA_QUOTA_BYTES`) et de 10 000 fichiers (`MAX_MEDIA_FILES`, de 1 à 10 000), expiration après 35 jours. Les envois sont écrits en flux, les lectures aussi. Le répertoire `media/` est créé dans le volume existant. Prévoir plus de 100 Go de quota disque, avec une marge pour les cartes, métadonnées et snapshots. La limite de fichiers permet d’utiliser les 100 Go ; les médias ne sont pas chargés intégralement en mémoire. Le reverse proxy doit autoriser 51 Mio par requête et 300 secondes de transfert, sans journaliser les URL de capacité.

`GET /health` ajoute `media:1`. Pour mettre à jour, récupérer l'image de la branche `server/homeserver` et recréer le même service avec le même volume. Ni les cartes existantes, ni leurs clés, ni les amitiés ne sont réinitialisées. Le Dockerfile embarque désormais `server.mjs` **et** `media.mjs` ; une installation qui monte directement les sources doit monter les deux fichiers dans le même dossier.

Pour appliquer le nouveau quota à une installation existante, récupérer la nouvelle image puis recréer le conteneur en conservant son volume de données. Une ancienne valeur explicite de `MEDIA_QUOTA_BYTES` ou `MAX_MEDIA_FILES` dans la configuration prend priorité sur les valeurs par défaut.

## Retours anonymes vers un GitHub privé

La route `POST /v2/feedback` accepte une note facultative, un type de retour, un message et une version d’app. Elle ne demande pas de compte GitHub à la personne qui écrit. Le serveur ajoute ces seuls éléments dans une issue du dépôt privé configuré. Les adresses réseau ne sont ni enregistrées ni envoyées à GitHub. La taille est limitée à 8 Kio et le message à 3 000 caractères ; les envois sont limités et un identifiant de soumission évite les doubles clics au sein du processus courant.

Configurer `FEEDBACK_GITHUB_REPO=owner/private-repo` et un jeton dédié `FEEDBACK_GITHUB_TOKEN` limité à ce dépôt avec **Issues: Read and write**. Le jeton reste dans l’environnement privé du serveur, jamais dans l’app ou Git. Une installation gérant les secrets fichiers peut employer `FEEDBACK_GITHUB_TOKEN_FILE` à la place. Le serveur vérifie que le dépôt est privé avant chaque création d’issue. Sans configuration ou si le dépôt devient public, l’envoi est refusé et le relais continue de fonctionner. `/health` indique `feedback:1` quand la passerelle est configurée, sans révéler le dépôt ou le jeton.

Après mise à jour de l’image, recréer le service avec le même volume. Les médias autorisent désormais les vidéos de 50 Mio (+28 octets de chiffrement), en flux, avec un délai maximal de requête de 300 s. Adapter également le reverse proxy à 51 Mio et 300 s. Le quota global de 100 Go, les propriétaires des fichiers et les cartes restent conservés.
