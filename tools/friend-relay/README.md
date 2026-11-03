# Serveur amis SuperHealth

Le serveur chiffré v2 est sur la branche autonome **server/homeserver**.
Son [code et ses fichiers Compose](https://github.com/MamaLama0/SuperHealth/tree/server/homeserver) sont sur GitHub.

Cette branche contient uniquement le serveur Node, ses tests, Dockerfile, les Compose TrueNAS et les instructions de déploiement à la racine. La branche est publiée séparément de l'app ; aucun déploiement automatique n'est effectué.

Le client Android publie des enveloppes AES-256-GCM sur `/v2/cards/<mailbox aléatoire>/<capacité de lecture>` ; la clé AES est transmise uniquement dans l'invitation entre amis. Le relais ne reçoit aucune donnée santé en clair. Consulter le README de la branche serveur pour les limites, métadonnées et ACL.

Le relais v1 est obsolète. Les anciens QR restent lisibles localement ; le relais v2 nécessite un nouvel échange d’invitations.
