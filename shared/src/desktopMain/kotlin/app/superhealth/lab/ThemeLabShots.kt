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
    if (round == "10") {
        val only = args.getOrNull(2)
        for (dark in listOf(false, true)) { val m = if (dark) "sombre" else "clair"
            if (only == null || only == "ecrans") {
                shot("score-$m-1-accueil") { Score10Home(dark) }; shot("score-$m-2-nutrition") { Score10Food(dark) }; shot("score-$m-3-activites") { Score10Act(dark) } }
            if (only == null || only == "planche") listOf(0 to 7, 7 to 13).forEachIndexed { k, (a, b) ->
                val scene = ImageComposeScene((3 * w * 2f).toInt(), (2700 * 2f).toInt(), Density(2f)) { Score10Sheet(dark, a, b) }
                scene.render(0); val img = scene.render(16_000_000)
                java.io.File(out, "planche-$m-${k + 1}.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes); scene.close(); println("planche $m $k")
            }
        }
    } else if (round == "9") {
        val only = args.getOrNull(2)
        val styles = listOf<Triple<String, String, @Composable (Boolean, Int) -> Unit>>(
            Triple("score", "", { d, t -> when (t) { 0 -> ScoreHome(d); 1 -> ScoreFood(d); else -> ScoreAct(d) } }),
            Triple("sante", "", { d, t -> when (t) { 0 -> SanteHome(d); 1 -> SanteFood(d); else -> SanteAct(d) } }),
            Triple("doux", "", { d, t -> when (t) { 0 -> DouxHome(d); 1 -> DouxFood(d); else -> DouxAct(d) } }),
        )
        val tabs = listOf("1-accueil", "2-nutrition", "3-activites")
        for ((name, _, c) in styles) for (dark in listOf(false, true)) for (t in 0..2) {
            val n = "$name-${if (dark) "sombre" else "clair"}-${tabs[t]}"
            if (only == null || n.startsWith(only)) shot(n) { c(dark, t) }
        }
    } else if (round == "8") {
        for (dark in listOf(false, true)) { val m = if (dark) "sombre" else "clair"
            shot("compact-$m-1-accueil") { CompactHome(dark) }; shot("compact-$m-2-nutrition") { CompactFood(dark) }; shot("compact-$m-3-activites") { CompactAct(dark) } }
    } else if (round == "7") {
        val only = args.getOrNull(2)
        val all = listOf<Pair<String, @Composable () -> Unit>>("instrument-1-accueil" to { InstHome() }, "instrument-2-nutrition" to { InstFood() }, "instrument-3-activites" to { InstAct() },
            "affiche-1-accueil" to { AffHome() }, "affiche-2-nutrition" to { AffFood() }, "affiche-3-activites" to { AffAct() },
            "galet-1-accueil" to { GalHome() }, "galet-2-nutrition" to { GalFood() }, "galet-3-activites" to { GalAct() })
        all.filter { only == null || it.first.startsWith(only) }.forEach { (n, c) -> shot(n, c) }
    } else if (round == "6") {
        val only = args.getOrNull(2)
        for (st in STYLES.filter { only == null || it.name == only }) {
            shot("s-${st.name}-1-accueil") { StyleHome(st) }
            val scene = ImageComposeScene((w * scale).toInt(), (2200 * scale).toInt(), Density(scale)) { StyleSheet(st) }
            scene.render(0); val img = scene.render(16_000_000)
            java.io.File(out, "s-${st.name}-2-planche.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes); scene.close(); println("${st.name} planche")
        }
    } else if (round == "5") {
        // catalogue : args[2] = spécification de design (« base=porcelaine glow=0.3 … »), sinon graphite puis porcelaine
        val specs = args.getOrNull(2)?.let { listOf(it) } ?: listOf("base=graphite", "base=porcelaine")
        for (spec in specs) { val dz = parseDesign(spec)
            for (f in FAMILIES) {
                val scene = ImageComposeScene((w * scale).toInt(), (3000 * scale).toInt(), Density(scale)) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalD provides dz) { CatalogSheet(f) }
                }
                scene.render(0); val img = scene.render(16_000_000)
                java.io.File(out, "w-${f.key}-${dz.base.name}.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes); scene.close(); println("${f.key} ${dz.base.name}")
            }
        }
    } else if (round == "4") {
        val only = args.getOrNull(2)
        fun pick(n: String, c: @Composable () -> Unit) { if (only == null || n.startsWith(only)) shot(n, c) }
        pick("keynote-1-accueil") { KeynoteHome() }; pick("keynote-2-journal") { KeynoteJournal() }; pick("keynote-3-activites") { KeynoteActivities() }
        pick("paysage-1-accueil") { PaysageHome() }; pick("paysage-2-journal") { PaysageJournal() }; pick("paysage-3-activites") { PaysageActivities() }
        pick("matrice-1-accueil") { MatriceHome() }; pick("matrice-2-journal") { MatriceJournal() }; pick("matrice-3-activites") { MatriceActivities() }
    } else if (round == "3") {
        shot("pixel-1-accueil") { PixelHome() }; shot("pixel-2-journal") { PixelJournal() }
        shot("pixel-3-activites") { PixelActivities() }; shot("pixel-4-recherche") { PixelSearch() }
        shot("bulles-1-accueil") { BullesHome() }; shot("bulles-2-journal") { BullesJournal() }
        shot("bulles-3-activites") { BullesActivities() }; shot("bulles-4-recherche") { BullesSearch() }
        shot("fil-1-accueil") { FilHome() }; shot("fil-2-journal") { FilJournal() }
        shot("fil-3-activites") { FilActivities() }; shot("fil-4-recherche") { FilSearch() }
    } else if (round == "1") for (look in ALL) {
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
