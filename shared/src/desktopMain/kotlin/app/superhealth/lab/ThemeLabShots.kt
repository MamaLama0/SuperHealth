package app.superhealth.lab

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat

/** Rendu des maquettes : `./gradlew :shared:themelab -Pout=dossier [-Pround=1]` → <thème>-<écran>.png (393 × 1000 pt). */
fun main(args: Array<String>) {
    val out = java.io.File(args.getOrElse(0) { "themelab" }).apply { mkdirs() }
    val round = args.getOrElse(1) { "2" }
    val w = 393; val h = 1000; val scale = 2.5f
    fun shot(name: String, content: @Composable () -> Unit) {
        val scene = ImageComposeScene((w * scale).toInt(), (h * scale).toInt(), Density(scale)) { content() }
        scene.render(0); val img = scene.render(16_000_000)
        java.io.File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close(); println(name)
    }
    if (round == "1") for (look in ALL) {
        shot("${look.name.lowercase()}-accueil") { Home(look) }
        shot("${look.name.lowercase()}-journal") { Journal(look) }
        shot("${look.name.lowercase()}-activites") { Activities(look) }
    } else for (kit in KITS) {
        val n = kit.name.lowercase()
        shot("$n-1-accueil") { Home2(kit) }
        shot("$n-2-journal") { Journal2(kit) }
        shot("$n-3-activites") { Activities2(kit) }
        shot("$n-4-seance") { Session2(kit) }
    }
}
