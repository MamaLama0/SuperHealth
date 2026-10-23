package app.superhealth.debug

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.ui.charts.BarChart
import app.superhealth.ui.charts.LineChart
import app.superhealth.ui.charts.Series
import app.superhealth.ui.charts.SignedBarChart
import app.superhealth.ui.charts.WaterfallChart
import app.superhealth.ui.health.HeartChart
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.SuperTheme
import app.superhealth.nutrition.domain.ThemeMode
import kotlin.math.sin

/** Galerie des graphiques avec des données de test et leurs cas limites, pour vérifier la charte (build debug). */
class ChartGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val page = intent.getIntExtra("page", 0).let { if (it == 2) 4 else it }
        setContent { SuperTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { Gallery(page) } }
    }
}

@Composable
private fun Case(title: String, content: @Composable () -> Unit) {
    MfCard { Text(title, color = Mf.colors.textSecondary); content() }
}

@Composable
private fun Gallery(page: Int) {
    val red = androidx.compose.ui.graphics.Color(0xFFFA114F)
    val blue = androidx.compose.ui.graphics.Color(0xFF0A84FF)
    val purple = androidx.compose.ui.graphics.Color(0xFFBF5AF2)
    val green = androidx.compose.ui.graphics.Color(0xFF2EA043)
    val weights = (0L until 60L).map { it to 82.4 - it * 0.05 + sin(it / 3.0) * 0.4 }
    val trend = (0L until 60L).map { it to 82.4 - it * 0.05 }
    val kcal = listOf(2140.0, 1890.0, null, 2410.0, 2205.0, 1650.0, 2380.0)
    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (page) {
            0 -> {
                Case("Poids : nuage + tendance + bande + projection") {
                    LineChart(listOf(Series(weights, Mf.colors.textSecondary, dots = true),
                        Series(trend, Mf.colors.accent, band = trend.map { Triple(it.first, it.second - 0.6, it.second + 0.6) }),
                        Series((59L..89L).map { it to 82.4 - it * 0.05 }, Mf.colors.accent.copy(alpha = 0.6f), dashed = true)), height = 230.dp)
                }
                Case("Dépense : 3 séries") {
                    LineChart(listOf(Series((0L..90).map { it to 2300 + sin(it / 9.0) * 120 }, purple.copy(alpha = 0.35f), width = 1.5f),
                        Series((0L..90).map { it to 2310 + sin(it / 12.0) * 60 }, purple.copy(alpha = 0.35f), width = 1.5f, dashed = true),
                        Series((0L..90).map { it to 2305 + sin(it / 10.0) * 90 }, purple)), height = 220.dp)
                }
                Case("Calories 7 j, objectif variable") {
                    BarChart(kcal, listOf(2200.0, 2200.0, 2200.0, 2500.0, 2500.0, 2200.0, 2200.0), red, height = 180.dp, labels = listOf("L", "M", "M", "J", "V", "S", "D"))
                }
                Case("Pas 30 j, objectif fixe (compact)") {
                    BarChart((0 until 30).map { 4000.0 + (it * 1371 % 9000) }, (0 until 30).map { 10000.0 }, green, height = 64.dp, dimBelowGoal = true,
                        labels = (0 until 30).map { if (it % 7 == 0) "${it + 1}" else "" })
                }
            }
            1 -> {
                Case("Balance énergétique 30 j") { SignedBarChart((0 until 30).map { if (it % 6 == 0) null else sin(it / 2.0) * 480 }, green, red, height = 180.dp) }
                Case("Balance compacte 7 j") { SignedBarChart(listOf(-320.0, 140.0, null, -510.0, 60.0, -90.0, 280.0), red, blue, height = 70.dp, showAxis = false, labels = listOf("L", "M", "M", "J", "V", "S", "D")) }
                Case("FC jour + repos + trou") {
                    val start = 0L
                    val pts = (0 until 288).filter { it !in 100..140 }.map { start + it * 300_000L to 62 + 25 * sin(it / 20.0).coerceAtLeast(0.0) + (it % 7) }
                    HeartChart(pts, red, height = 170.dp, from = start, to = start + 86_400_000L, reference = 58.0,
                        xLabels = listOf(0, 6, 12, 18).map { start + it * 3_600_000L to "${it}h" })
                }
                Case("Cascade objectif") { WaterfallChart(-8.0, listOf(-0.8, -0.6, 0.3, -1.1, -0.4), -4.8, { it < 0 }, height = 190.dp) }
            }
            3 -> {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val d = ctx.resources.displayMetrics.density
                val rings = app.superhealth.ui.dashboard.Rings.build(listOf(app.superhealth.ui.dashboard.Rings.Kind.STEPS,
                    app.superhealth.ui.dashboard.Rings.Kind.ENERGY, app.superhealth.ui.dashboard.Rings.Kind.KCAL, app.superhealth.ui.dashboard.Rings.Kind.PROTEIN),
                    { when (it) { app.superhealth.ui.dashboard.Rings.Kind.STEPS -> 12400.0; app.superhealth.ui.dashboard.Rings.Kind.ENERGY -> 46.0
                        app.superhealth.ui.dashboard.Rings.Kind.KCAL -> 1620.0; else -> null } },
                    { when (it) { app.superhealth.ui.dashboard.Rings.Kind.STEPS -> 10000.0; app.superhealth.ui.dashboard.Rings.Kind.ENERGY -> 100.0
                        app.superhealth.ui.dashboard.Rings.Kind.KCAL -> 2300.0; else -> 140.0 } })
                // fond « papier peint » sombre puis clair : le widget sans cadre doit rester lisible sur les deux
                listOf(androidx.compose.ui.graphics.Color(0xFF243B55), androidx.compose.ui.graphics.Color(0xFFE8D9C4)).forEach { wall ->
                    Column(Modifier.background(wall).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(57 to 57, 130 to 130, 130 to 270).forEach { (w, h) ->
                                val bmp = app.superhealth.ui.widget.RingsScene.draw(ctx, rings, (w * d).toInt(), (h * d).toInt(), d)
                                androidx.compose.foundation.Image(bmp.asImageBitmap(), null, Modifier.size(w.dp, h.dp).border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)))
                            }
                        }
                        listOf(330 to 57, 330 to 130).forEach { (w, h) ->
                            val bmp = app.superhealth.ui.widget.RingsScene.draw(ctx, rings, (w * d).toInt(), (h * d).toInt(), d)
                            androidx.compose.foundation.Image(bmp.asImageBitmap(), null, Modifier.size(w.dp, h.dp).border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)))
                        }
                    }
                }
            }
            else -> {
                Case("Vide") { LineChart(emptyList(), height = 120.dp) }
                Case("Un point") { LineChart(listOf(Series(listOf(1L to 72.0), blue)), height = 120.dp) }
                Case("Série plate") { LineChart(listOf(Series((0L..10).map { it to 60.0 }, blue)), height = 120.dp) }
                Case("Petites valeurs décimales (sommeil h)") { BarChart(listOf(6.5, 7.25, 8.1, 5.9, null, 7.7, 9.2), (0..6).map { 8.0 }, purple, height = 150.dp) }
                Case("Allure inversée, axe temps") {
                    LineChart(listOf(Series((0..100).map { it / 10.0 to 300 + sin(it / 8.0) * 40 }, blue, area = true)), height = 170.dp, invertY = true, timeAxis = true,
                        format = { "${it.toInt() / 60}:${"%02d".format(it.toInt() % 60)}" }, reference = 305.0, highlightEnd = false)
                }
                Row { Column(Modifier.width(160.dp)) { Case("Sparklines 28/40 dp") {
                    LineChart(listOf(Series((0L..27).map { it to 2300 + sin(it / 4.0) * 100 }, purple)), height = 28.dp, showAxis = false)
                    LineChart(listOf(Series((0L..27).map { it to 70 - it * 0.3 }, red)), height = 40.dp, showAxis = false)
                } } }
            }
        }
    }
}
