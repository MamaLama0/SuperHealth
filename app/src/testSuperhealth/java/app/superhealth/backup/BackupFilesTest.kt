package app.superhealth.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** La sauvegarde emporte tous les fichiers de l'app, caches exceptés, et les restaure à l'identique. */
class BackupFilesTest {
    private val userData = listOf(
        "photos/1700000000-front.jpg", "runs/index.json", "runs/42.json", "coach/week.json", "coach/chat.json", "league/group-draft.json",
        "league/state.json", "hiking/library.json", "hiking/photos/" + "a".repeat(64) + ".jpg", "wellbeing/journal.json", "meds/meds.json",
        "meals/plans.json", "cards/state.json", "profile.jpg", "stretch_log.json", "meditation_log.json", "un-futur-module/donnees.json",
    )
    private val caches = listOf(
        "state.json", "routes/x.txt", "hc-heart/20000.txt", "modules/hiking/hiking.json", "models/sam/m.onnx", "elevation_tiles/t.hgt",
        "performance-cache-v3.json", "league/outbox/1.bin", "league/space-media/m.jpg", "wellbeing/journal.json.new",
    )

    private fun tree(): File = Files.createTempDirectory("files").toFile().also { root ->
        (userData + caches).forEach { File(root, it).apply { parentFile?.mkdirs(); writeText("contenu de $it") } }
    }

    @Test fun everyUserFileIsSavedAndCachesAreSkipped() {
        val names = BackupFiles.list(tree()).map { it.first }
        assertEquals(userData.map { BackupFiles.PREFIX + it }.sorted(), names)
    }

    @Test fun roundTripRestoresIdenticalFiles() {
        val src = tree()
        val zipped = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> BackupFiles.list(src).forEach { (n, f) -> z.putNextEntry(ZipEntry(n)); z.write(f.readBytes()); z.closeEntry() } }
        }.toByteArray()
        val dst = Files.createTempDirectory("restore").toFile()
        ZipInputStream(ByteArrayInputStream(zipped)).use { z ->
            generateSequence { z.nextEntry }.forEach { e -> BackupFiles.target(dst, e.name)!!.apply { parentFile?.mkdirs() }.outputStream().use { z.copyTo(it) } }
        }
        userData.forEach { assertEquals(File(src, it).readText(), File(dst, it).readText()) }
        caches.forEach { assertTrue("$it ne doit pas être restauré", !File(dst, it).exists()) }
    }

    @Test fun entriesCannotEscapeTheAppFolder() {
        val root = Files.createTempDirectory("files").toFile()
        assertNull(BackupFiles.target(root, "files/../escape.txt"))
        assertNull(BackupFiles.target(root, "files//etc/passwd"))
        assertNull(BackupFiles.target(root, "nutrition.json"))
        assertNull(BackupFiles.target(root, "files/state.json"))
    }

    /** Garde-fou : chaque dossier écrit par le code dans filesDir est sauvegardé, ou déclaré comme cache ici. */
    @Test fun everyFolderWrittenByTheAppIsCovered() {
        val knownCaches = setOf("state.json", "routes", "hc-heart", "modules", "models", "performance-cache-v3.json", "fit-import", "league/outbox", "league/space-media", "league/space-drafts")
        val pattern = Regex("""filesDir\s*,\s*"([^"$]+)""")
        val written = File("src/superhealth/java").walk().filter { it.extension == "kt" }
            .flatMap { f -> pattern.findAll(f.readText()).map { it.groupValues[1].trimEnd('/') } }.toSet()
        assertTrue("aucun chemin trouvé", written.isNotEmpty())
        val uncovered = written.filter { p -> p !in knownCaches && knownCaches.none { p.startsWith("$it/") } && !BackupFiles.keep(if ('.' in p.substringAfterLast('/')) p else "$p/x.json") }
        assertTrue("Chemins ni sauvegardés ni déclarés comme cache : $uncovered", uncovered.isEmpty())
    }
}
