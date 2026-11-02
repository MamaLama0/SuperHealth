# Relais amis SuperHealth — serveur chiffré

Branche autonome `server/homeserver` : uniquement le petit serveur et son déploiement, sans l'application Android. Aucun serveur n'est déployé automatiquement.

Le téléphone calcule son score et chiffre sa carte signée avant envoi. Le relais garde **un seul objet opaque par boîte**, sans compte, adresse e-mail, nom, clé publique, score, PR, journal ou parcours GPS en clair. La clé AES-256 de lecture est transmise à un ami dans le QR signé ; elle n'est jamais envoyée au relais. Les URL utilisent deux valeurs aléatoires de 256 bits, sans empreinte d'identité. Les IP et horaires de connexion restent nécessairement visibles dans le transport réseau en cours ; aucune promesse d'anonymat réseau n'est faite.

Le processus n'enregistre ni requêtes, ni IP, ni URL, ni secrets. Désactiver aussi les journaux d'accès du reverse proxy et des outils d'observabilité placés devant lui. Le disque contient des noms de fichiers hachés, le dernier chiffré, sa révision, un horodatage d'expiration, un ETag et le hash du secret d'écriture. Le gestionnaire du NAS peut supprimer ou retarder une carte, mais ne peut pas la déchiffrer sans la clé détenue par les amis ; les téléphones vérifient aussi la signature interne et refusent les révisions anciennes.

## Charge et limites

- Un conteneur Node 24, aucune dépendance npm, base SQL, Redis, file de tâches ou websocket.
- CPU plafonné à 0,5 cœur et mémoire à 256 Mio dans Compose ; tas Node plafonné à 96 Mio. Ce sont des limites, pas une mesure de consommation réelle.
- 500 boîtes par défaut, corps de 96 Kio maximum, dernière carte seulement, expiration après 35 jours sans nouvelle publication. Au maximum environ 48 Mio de corps de cartes, plus les petits fichiers de métadonnées ; utiliser un quota ZFS d'au moins 128 Mio, avec marge séparée si les snapshots ZFS sont conservés.
- Index des métadonnées en mémoire : un GET conditionnel inchangé renvoie 304 sans lire le fichier. Aucun calcul santé ni déchiffrement sur le NAS.
- 8 requêtes actives et 32 connexions maximum ; 240 requêtes/minute par IP réseau directe. Derrière un proxy, cette dernière limite s'applique à l'IP du proxy donc peut être commune à tous les utilisateurs. `X-Forwarded-For` n'est pas utilisé.
- `MAX_CARDS` (1..10000), `MAX_IN_FLIGHT` (1..64) et `RATE_LIMIT_PER_MINUTE` (1..10000) sont configurables. `429/503` demandent un nouvel essai via `Retry-After`, `507` signale le quota de boîtes.

Pour un petit groupe qui actualise 2 à 4 fois par jour et à l'ouverture, le trafic reste de simples petites lectures HTTP. Les clients doivent limiter la fréquence, envoyer `If-None-Match`, conserver les clés en stockage sécurisé et ne publier que les statistiques que leur utilisateur veut partager. Aucune mesure sur un vrai TrueNAS n'a encore été effectuée.

## Déploiement TrueNAS

Cible : TrueNAS SCALE/Community **24.10 ou ultérieur**, avec moteur Apps Docker Compose. TrueNAS CORE et les anciennes Apps Kubernetes ne sont pas couverts. Le mode **Apps → Discover → Install via YAML** accepte les configurations Compose ; voir la [documentation TrueNAS](https://apps.truenas.com/managing-apps/installing-custom-apps/).

1. Copier/cloner cette branche dans un dataset dédié, par exemple `/mnt/tank/apps/superhealth/source`. Le nom `tank` est un exemple à remplacer.
2. Créer des répertoires/datasets `data` et `secrets` à côté de `source`, hors partage SMB public. Dans les ACL TrueNAS, donner au UID/GID utilisé dans Compose (568:568 par défaut) le droit de modifier `data` et de lire le fichier secret. Le fichier secret doit rester inaccessible aux autres comptes. Les parents doivent permettre la traversée. Ne pas rendre ces dossiers accessibles à tous pour contourner une erreur d'ACL.
3. Générer le code de publication, sans l'afficher dans les logs :

   ```sh
   cd /mnt/tank/apps/superhealth/source
   node scripts/create-secret.mjs /mnt/tank/apps/superhealth/secrets/publish_key
   ```

   Si Node n'est pas installé sur l'hôte, utiliser son conteneur existant sans monter les données :

   ```sh
   docker run --rm --user 568:568 -v /mnt/tank/apps/superhealth/secrets:/secrets node:24-alpine node -e "require('node:fs').writeFileSync('/secrets/publish_key',require('node:crypto').randomBytes(32).toString('hex')+'\\n',{mode:0o600,flag:'wx'})"
   ```

   La commande refuse d'écraser un secret existant. Si le premier script tourne sous un autre UID, ajuster l'ACL du fichier ensuite pour le UID 568 ; les paramètres `uid/mode` d'un secret Compose ne remplacent pas les permissions du fichier hôte.
4. Construire l'image locale sur le NAS (ou sur une autre machine et l'importer dans le moteur Docker du NAS) :

   ```sh
   docker build -t superhealth-friend-relay:2 /mnt/tank/apps/superhealth/source
   ```

5. Dans `compose.truenas.yaml`, remplacer les deux chemins `/mnt/POOL/apps/superhealth` par les chemins réels. Coller le fichier dans **Install via YAML**. Il n'a pas besoin d'un `.env`, d'un contexte de build relatif ni d'une image publiée. `pull_policy: never` utilise l'image locale construite à l'étape précédente.
6. Le service HTTP est lié à `127.0.0.1:8787` et accessible aux conteneurs du réseau Docker `superhealth-relay` sous `friend-relay:8787`. Placer un reverse proxy HTTPS devant, sur un domaine dédié sans sous-chemin. Un proxy dans un autre conteneur doit rejoindre ce réseau via sa propre configuration ; son `127.0.0.1` désigne son propre conteneur, pas le NAS.
7. Tester `GET /health` : réponse `{"status":"ok","protocol":2}`. Le contrôle de santé ne teste ni un compte ni une carte personnelle. Configurer ensuite l'adresse HTTPS et le code de publication dans l'app, puis échanger les nouveaux QR entre amis.

Ne pas ouvrir directement le port HTTP sur Internet. Le code de publication autorise les personnes invitées à créer des boîtes ; chaque boîte a en plus son secret d'écriture privé, absent du QR. Un utilisateur qui connaît seulement la capacité de lecture et le code global ne peut pas écraser une boîte existante.

La validation YAML est syntaxique dans TrueNAS ; elle ne certifie pas les chemins, ACL, DNS, pare-feu ou reverse proxy. Le lancement réel doit encore être vérifié sur le NAS de l'utilisateur.

## Docker Compose classique et HTTPS facultatif

```sh
cp .env.example .env
node scripts/create-secret.mjs
# Préparer data et les ACL pour le UID choisi avant le démarrage.
docker compose config --quiet
docker compose up -d --build
```

Si aucun reverse proxy n'existe, `compose.https.yaml` ajoute Caddy. Définir `DOMAIN` dans `.env`, monter le `Caddyfile` fourni, faire pointer le DNS public vers le domicile, puis rediriger les ports du routeur **WAN 80 → NAS 8080** et **WAN 443 → NAS 8443** :

```sh
docker compose -f compose.yaml -f compose.https.yaml up -d --build
```

Les ports NAS 8080/8443 évitent de reprendre ceux de l'interface TrueNAS. Ils restent à adapter si déjà occupés. Caddy utilise deux petits volumes pour ses certificats/configurations ; sa limite mémoire est 128 Mio et CPU 0,25 cœur. Les logs de requêtes et les logs globaux de Caddy sont désactivés pour ne pas conserver les URL de capacité. L'accès HTTPS public et la validation ACME demandent DNS/routage corrects ; un accès résidentiel derrière CGNAT nécessite une autre solution réseau fournie par l'hébergeur. Le fichier HTTPS est un complément Compose CLI, pas un YAML autonome à coller seul dans TrueNAS.

Pour Caddy dans l'interface TrueNAS, utiliser directement `compose.truenas.https.yaml` : remplacer les trois chemins `POOL` et le domaine `amis.exemple.fr`, puis coller ce fichier complet dans Install via YAML. Il combine le relais et HTTPS, sans `.env` ni chemin relatif. Faire les mêmes redirections 80→8080 et 443→8443 sur le routeur. Une configuration de proxy existante peut aussi être utilisée avec le fichier TrueNAS minimal.

## Contrat du client Android

**Route :** `/v2/cards/<mailbox64hex>/<readCapability64hex>`, deux valeurs aléatoires indépendantes. Ni nom ni empreinte de clé dans ce chemin. Le chemin entier est une capacité de lecture à protéger.

**Chiffrement sur le téléphone :** AES-256-GCM, nonce aléatoire de 12 octets à chaque publication, tag 16 octets ajouté à la fin du ciphertext, base64url canonique sans `=`. AAD UTF-8 exacte :

```text
SuperHealth relay v2:/v2/cards/<mailbox64hex>/<readCapability64hex>
```

Le texte chiffré est la carte SH1 signée, avec l'identité et les statistiques. La clé AES de 32 octets est incluse seulement dans le partage signé vers un ami, jamais dans la route, les en-têtes ou l'enveloppe HTTP.

**PUT** : deux en-têtes : `Authorization: Bearer <code de publication>` et `X-Write-Token: <secret propriétaire 64hex>`. Ce dernier est généré sur le téléphone, distinct de la capacité de lecture et de la clé AES ; il ne quitte pas le téléphone sauf pour les opérations d'écriture sur son relais HTTPS. Corps exact :

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

Références consultées : [Apps personnalisées TrueNAS](https://apps.truenas.com/managing-apps/installing-custom-apps/), [stockage Apps](https://apps.truenas.com/getting-started/app-storage/), [services Compose et limites](https://docs.docker.com/reference/compose-file/services/), [secrets Compose](https://docs.docker.com/compose/how-tos/use-secrets/). Aucun secret, image ni donnée utilisateur n'est fourni dans cette branche.

Licence : AGPL-3.0-or-later, comme le projet SuperHealth ; voir `LICENSE`.
