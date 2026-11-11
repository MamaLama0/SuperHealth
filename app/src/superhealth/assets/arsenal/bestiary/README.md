# Bestiaire intégré

Les onze GLB sont copiés sans transformation depuis le pack `3D/models` du projet.
Ils embarquent leurs textures, leur rig et les clips d’origine. `catalogue.json`
reprend les affinités du pack, adaptées aux quatre types de dégâts actuels.

`creature-gltf.js` charge les GLB locaux avec WebGL 2 : skinning GPU (32 os au
maximum), textures embarquées, facteurs de matériaux, interpolation LINEAR/STEP,
repos en boucle, attaque/coup reçu non bouclés, mort conservée à sa dernière pose.
Les slimes sont rendus après les objets opaques, par faces arrière puis avant,
avec alpha et éclairage brillant. La transmission physique glTF n’est pas simulée.
Les WebP servent de repli si WebGL n’est pas disponible.

Les étages alternent slimes et gobelins selon les cinq couples d’affinités
existants. Brisefer occupe les étages 4, 12, 20, etc. Les élites sont vulnérables à Arcaniste ;
les boss hebdomadaires alternent Arcaniste et Divine. Les mêmes règles sont
appliquées par le client et le serveur. Bénédiction et Malédiction sont des effets
de carte, jamais des types de dégâts.

Les anciens avatars et leurs duels continuent d’utiliser le renderer `.mesh`.
Voir `CREDITS.txt` et les licences CC0 conservées dans ce dossier.
