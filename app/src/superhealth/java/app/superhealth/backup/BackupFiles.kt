package app.superhealth.backup

import java.io.File

/**
 * Fichiers privés de l'app emportés par la sauvegarde : tout filesDir, sauf les caches relus ou retéléchargés et les fichiers
 * déjà sérialisés à part (musculation). Une nouvelle fonction qui écrit dans filesDir est donc sauvegardée sans rien ajouter ici.
 * Les entrées de l'archive s'appellent « files/<chemin relatif> ».
 */
internal object BackupFiles {
    const val PREFIX = "files/"

    /** Premier dossier (ou fichier) du chemin : caches, contenus retéléchargeables et état déjà exporté en JSON. */
    private val SKIP_TOP = setOf(
        "state.json", // musculation : musculation.json
        "routes", "hc-heart", // tracés et pouls relus depuis la montre ou Health Connect
        "modules", "models", "elevation_tiles", // catalogues, modèles et tuiles retéléchargés
        "datastore", "fit-import",
    )

    /** Sous-dossiers transitoires du Groupe : envois en attente et médias reçus (gardés par le relais). */
    private val SKIP_PATHS = listOf("league/outbox", "league/space-media", "league/space-drafts")

    fun keep(rel: String): Boolean {
        if (rel.isEmpty() || rel.startsWith("/") || ".." in rel.split('/')) return false
        if (rel.substringBefore('/') in SKIP_TOP) return false
        if (SKIP_PATHS.any { rel == it || rel.startsWith("$it/") }) return false
        if (rel.startsWith("performance-cache")) return false
        val name = rel.substringAfterLast('/')
        return !(name.endsWith(".new") || name.endsWith(".tmp") || name.endsWith(".lock"))
    }

    /** (nom d'entrée, fichier) de tout ce qui doit être sauvegardé sous [root], dans un ordre stable. */
    fun list(root: File): List<Pair<String, File>> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .onEnter { dir -> dir == root || keep(dir.relativeTo(root).invariantSeparatorsPath) }
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath to it }
            .filter { (rel, _) -> keep(rel) }
            .sortedBy { it.first }
            .map { (rel, f) -> PREFIX + rel to f }
            .toList()
    }

    /** Fichier où restaurer l'entrée [entry] ; null si ce n'est pas un fichier de l'app ou s'il sortirait de [root]. */
    fun target(root: File, entry: String): File? {
        if (!entry.startsWith(PREFIX)) return null
        val rel = entry.removePrefix(PREFIX)
        if (!keep(rel)) return null
        val f = File(root, rel)
        val base = root.canonicalFile.path + File.separator
        return f.takeIf { it.canonicalFile.path.startsWith(base) }
    }
}
