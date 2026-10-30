package app.superhealth.lab

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat

/** Rendu des maquettes : `./gradlew :shared:themelab -Pout=dossier` → <thème>-<onglet>.png (393 × 1000 pt). */
fun main(args: Array<String>) {
    val out = java.io.File(args.getOrElse(0) { "themelab" }).apply { mkdirs() }
    val w = 393; val h = 1000; val scale = 2.5f
    for (look in ALL) for ((tab, panel) in listOf<Pair<String, @androidx.compose.runtime.Composable (Look) -> Unit>>(
        "accueil" to { Home(it) }, "journal" to { Journal(it) }, "activites" to { Activities(it) })) {
        val scene = ImageComposeScene((w * scale).toInt(), (h * scale).toInt(), Density(scale)) { panel(look) }
        scene.render(0); val img = scene.render(16_000_000)
        java.io.File(out, "${look.name.lowercase()}-$tab.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("${look.name} $tab")
    }
}
