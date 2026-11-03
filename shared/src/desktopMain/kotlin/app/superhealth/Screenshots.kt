package app.superhealth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.superhealth.health.data.HealthSources
import app.superhealth.league.*
import app.superhealth.platform.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat

/**
 * Captures d'écran hors écran au format iPhone 16 (393 × 852 pt, échelle 3) : `./gradlew :shared:screenshots`.
 * Arguments : dossier de sortie, puis des écrans « nom=route » (route vide : écran d'ouverture). « @onboarded » saute
 * le questionnaire d'accueil, « @dark » passe en thème sombre.
 */
fun main(args: Array<String>) {
    HealthSources.current = DemoHealthSource
    val out = java.io.File(args.getOrElse(0) { "screenshots" }).apply { mkdirs() }
    val shots = args.drop(1)
    val phone=shots.firstOrNull { it.startsWith("@phone=") }?.substringAfter('=')?.split('x')
    val w=phone?.getOrNull(0)?.toIntOrNull() ?: 393
    val h=phone?.getOrNull(1)?.toIntOrNull() ?: 852
    val scale=3f
    runBlocking(Dispatchers.Main) {
        SuperHealth.init()
        if ("@onboarded" in shots) SuperPrefs.update { it.copy(onboarded = true, healthConnect = true) }
        if ("@dark" in shots) SuperHealth.vm.nutrition.repo.prefs.let { p ->
            p.setSettings(p.settings.value.copy(themeMode = app.superhealth.nutrition.domain.ThemeMode.DARK))
        }
        if("@large" in shots) SuperHealth.vm.nutrition.repo.prefs.let { p ->
            p.setSettings(p.settings.value.copy(textSize=app.superhealth.nutrition.domain.TextSize.LARGE))
        }
        if("@social" in shots) {
            app.superhealth.training.WorkoutApp.db.first {it!=null}
            League.configure(appContext) {it.copy(relayEnabled=false,questFocus=WeeklyQuests.Focus.entries.toSet())}
            val score=League.compute(appContext)
            val today=LocalDate.now().toEpochDay()
            val friends=listOf("Camille","Alexandre au nom long","Sam").mapIndexed { i,name ->
                League.Friend("preview-$i","preview-key-$i",name,score.week,nowMs()/1000,820-i*105,
                    model=HealthIndex.MODEL,ready=true,endDay=score.endDay,coverage=score.coverage,
                    progress=Progression.Summary(totalXp=1400-i*200L,weekXp=380-i*60,level=6-i))
            }
            League.configure(appContext) {it.copy(friends=friends)}
        }
        val scene = ImageComposeScene((w * scale).toInt(), (h * scale).toInt(), Density(scale)) { App() }
        var t = 0L
        suspend fun frames(ms: Long) {
            val until = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < until) {
                scene.render(t * 1_000_000L); t += 16
                withContext(Dispatchers.IO) { Thread.sleep(16) }
            }
        }
        frames(2500)
        for (spec in shots.filter { !it.startsWith("@") }) {
            val name = spec.substringBefore('=')
            val route = spec.substringAfter('=', "")
            if(route.startsWith("preview:cards:")) {
                val size=route.substringAfterLast(':')
                val cards=listOf(DashCard.LEAGUE,DashCard.PROGRESSION,DashCard.FRIENDS,DashCard.QUESTS,DashCard.STREAKS)
                SuperPrefs.update {it.copy(dashboard=cards,dashSizes=cards.associate {c->c.name to size})}
                SuperHealth.vm.pendingRoute.value="home"
            } else if (route.isNotEmpty()) SuperHealth.vm.pendingRoute.value = route
            frames(2200)
            val img = scene.render(t * 1_000_000L)
            val png = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
            java.io.File(out, "$name.png").writeBytes(png)
            println("capture : $name")
        }
        scene.close()
    }
    kotlin.system.exitProcess(0)
}
