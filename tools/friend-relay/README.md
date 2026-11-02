# Serveur amis SuperHealth

Le serveur chiffré v2 est sur la branche autonome **server/homeserver**, commit initial `555e4434`.
Son [code et ses fichiers Compose](https://github.com/MamaLama0/SuperHealth/tree/server/homeserver) sont sur GitHub. Le worktree local est `../SuperHealth-server/` depuis la racine de ce dépôt.

Cette branche contient uniquement le serveur Node, ses tests, Dockerfile, les Compose TrueNAS et les instructions de déploiement à la racine. La branche est publiée séparément de l'app ; aucun déploiement automatique n'est effectué.

Le client Android publie des enveloppes AES-256-GCM sur `/v2/cards/<mailbox aléatoire>/<capacité de lecture>` ; la clé AES est transmise uniquement dans l'invitation entre amis. Le relais ne reçoit aucune donnée santé en clair. Consulter le README de la branche serveur pour les limites, métadonnées et ACL.

L'ancien relais v1 non chiffré est archivé hors de ce dépôt, dans `Update/friend-relay-v1-archive/`, pour éviter de le publier comme serveur courant. Les anciens QR restent lisibles localement ; le relais v2 nécessite un nouvel échange d'invitations.
