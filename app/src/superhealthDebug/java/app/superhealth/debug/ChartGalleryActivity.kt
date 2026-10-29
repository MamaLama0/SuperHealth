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
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind as K

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
            6 -> SleepGallery()
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

/** Page 6 : graphiques du sommeil et des constantes (2.1) sur des nuits fictives. */
@Composable
private fun SleepGallery() {
    val today = java.time.LocalDate.now()
    fun night(d: java.time.LocalDate, shift: Int, staged: Boolean = true): app.superhealth.health.data.NightSummary {
        var t = app.superhealth.health.data.NightSummary.noon(d) + (11 * 60 + shift) * 60_000L
        val seq = if (staged) listOf(K.AWAKE_SLEEP to 12, K.LIGHT_SLEEP to 25, K.DEEP_SLEEP to 50, K.LIGHT_SLEEP to 40, K.REM_SLEEP to 20, K.AWAKE_SLEEP to 6,
            K.LIGHT_SLEEP to 45, K.DEEP_SLEEP to 35, K.LIGHT_SLEEP to 50, K.REM_SLEEP to 35, K.LIGHT_SLEEP to 40, K.AWAKE_SLEEP to 4, K.REM_SLEEP to 40, K.LIGHT_SLEEP to 30 + shift % 40)
            else listOf(K.SLEEP_ANY to 420 + shift % 50)
        return app.superhealth.health.data.NightSummary(d, seq.map { (k, m) -> app.superhealth.health.data.HealthRepository.SleepPhase(k, t, t + m * 60_000L).also { t += m * 60_000L } })
    }
    val nights = (13 downTo 0).map { i -> today.minusDays(i.toLong()).let { d -> if (i == 9) null else night(d, (i * 37) % 70 - 20, staged = i != 5) } }
    val last = nights.last()!!
    val red = androidx.compose.ui.graphics.Color(0xFFFA114F)
    val green = androidx.compose.ui.graphics.Color(0xFF2EA043)
    val purple = androidx.compose.ui.graphics.Color(0xFFBF5AF2)
    Case("Hypnogramme détaillé") { app.superhealth.ui.health.Hypnogram(last.phases, Modifier.fillMaxWidth().height(150.dp), axis = true) }
    Case("Phases") { app.superhealth.ui.health.StageLegend(last, mapOf(K.DEEP_SLEEP to 70.0, K.REM_SLEEP to 90.0, K.LIGHT_SLEEP to 230.0, K.AWAKE_SLEEP to 25.0)) }
    Case("Phases nuit par nuit + besoin") { app.superhealth.ui.health.StageBars(nights.takeLast(7), 465.0, Modifier, listOf("L", "M", "M", "J", "V", "S", "D"), height = 150.dp) }
    Case("Horaires 14 nuits") { app.superhealth.ui.health.ScheduleChart(nights, Modifier, nights.indices.map { if (it % 2 == 1) "x" else "" }, height = 150.dp) }
    Case("Parties de Repos") { app.superhealth.ui.health.RestPartsRows(app.superhealth.health.data.Scores.RestParts(92.0, 88.0, 64.0, 41.0), app.superhealth.ui.theme.HealthHues.Sleep) }
    val heart = (0 until 460).map { m -> last.bedMs + m * 60_000L to (58 - 8 * sin(m / 140.0) + (m % 7) - 3).toInt() }
    val low = app.superhealth.health.data.Nights.lowest(heart)
    Case("Cœur la nuit, plus bas ${low?.second?.toInt()} à ${low?.first?.let { app.superhealth.ui.timeOf(it) }}") {
        HeartChart(app.superhealth.health.data.Nights.fiveMinutes(heart), red, height = 140.dp, from = last.bedMs, to = last.wakeMs, reference = low?.second, gapMs = 20 * 60_000L)
    }
    Case("Barre de plage") {
        app.superhealth.ui.health.RangeBar(52.0, 48.0, 58.0, green); app.superhealth.ui.health.RangeBar(61.0, 48.0, 58.0, purple)
    }
}
