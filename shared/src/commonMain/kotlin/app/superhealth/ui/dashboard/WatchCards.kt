package app.superhealth.ui.dashboard

import app.superhealth.platform.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.DashCard
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Point
import app.superhealth.health.data.Scores
import app.superhealth.ui.charts.BarChart
import app.superhealth.ui.charts.ChartStyle
import app.superhealth.ui.charts.LineChart
import app.superhealth.ui.charts.Series
import app.superhealth.ui.charts.smooth
import app.superhealth.ui.dayLetter
import app.superhealth.ui.formatDuration
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.platform.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import app.superhealth.health.data.ActivityKind

/**
 * Graphiques que l'on retrouve dans les applis de montre (Garmin Connect, WHOOP, Santé) : Charge et Effort, phases de
 * la nuit, variabilité cardiaque face à sa référence, stress de la journée, zones cardiaques, charge d'entraînement aiguë
 * et chronique, et la journée heure par heure (pas et pouls). Toutes les montres et Health Connect les alimentent.
 */
internal class WatchSnap(
    val charge: Scores.Charge? = null,
    val chargeHistory: List<Double?> = emptyList(),
    val effort: Double? = null,
    val efforts: List<Double?> = emptyList(),
    val zones: IntArray? = null,
    val night: List<HealthRepository.SleepPhase>? = null,
    val debt: Double? = null,
    /** Repos de la nuit (score de sommeil SuperHealth), montré en mots à l'accueil. */
    val sleepScore: Double? = null,
    val needMin: Double? = null,
    val hrv: List<Pair<LocalDate, Double?>> = emptyList(),
    val stress: List<Point> = emptyList(),
    val hourly: List<Point> = emptyList(),
)

private val WATCH_CARDS = setOf(DashCard.CHARGE, DashCard.EFFORT, DashCard.SLEEP_STAGES, DashCard.HRV_STATUS, DashCard.STRESS_DAY,
    DashCard.HR_ZONES, DashCard.LOAD, DashCard.DAY_TIMELINE)

internal val DashCard.isWatchChart: Boolean get() = this in WATCH_CARDS

/** Une source existe-t-elle pour la carte ? (sinon elle reste masquée, comme les autres cartes de montre) */
internal fun watchSource(c: DashCard): Boolean = when (c) {
    DashCard.CHARGE, DashCard.HRV_STATUS -> Bridge.hasSource(Metric.HRV)
    DashCard.EFFORT, DashCard.HR_ZONES, DashCard.LOAD -> HealthRepository.isSupported(Metric.HEART_RATE)
    DashCard.SLEEP_STAGES -> Bridge.hasSource(Metric.SLEEP)
    DashCard.STRESS_DAY -> HealthRepository.isSupported(Metric.STRESS)
    else -> true
}

/** Lecture des seules données des cartes affichées. */
internal suspend fun loadWatch(ctx: Context, cards: List<DashCard>): WatchSnap = coroutineScope {
    val today = LocalDate.now()
    val want = cards.filter { it.isWatchChart && watchSource(it) }.toSet()
    fun has(vararg c: DashCard) = c.any { it in want }
    val charge = async { if (has(DashCard.CHARGE)) runCatching { Scores.chargeOf(ctx, today) }.getOrNull() else null }
    val chargeHist = async {
        if (has(DashCard.CHARGE)) (13L downTo 0L).map { d -> async { runCatching { Scores.chargeOf(ctx, today.minusDays(d))?.score?.toDouble() }.getOrNull() } }.awaitAll()
        else emptyList()
    }
    val efforts = async {
        // 28 jours d'avant + aujourd'hui : charge chronique (Charge d'entraînement, Effort à viser)
        val n = if (has(DashCard.LOAD) || has(DashCard.EFFORT)) 29L else 0L
        (n - 1 downTo 0L).map { d -> async { runCatching { Scores.effortOf(ctx, today.minusDays(d)) }.getOrNull() } }.awaitAll()
    }
    val zones = async { if (has(DashCard.HR_ZONES)) runCatching { Scores.zonesOf(ctx, today) }.getOrNull() else null }
    val night = async { if (has(DashCard.SLEEP_STAGES)) runCatching { Bridge.sleepNight(today) }.getOrNull() else null }
    val debt = async { if (has(DashCard.SLEEP_STAGES)) runCatching { Scores.sleepDebtOf(ctx, today) }.getOrNull() else null }
    val score = async { if (has(DashCard.SLEEP_STAGES)) runCatching { Bridge.healthValue(ctx, Metric.SLEEP_SCORE, today) }.getOrNull() else null }
    val need = async { if (has(DashCard.SLEEP_STAGES)) runCatching { Scores.sleepHabits(ctx).needMin }.getOrNull() else null }
    val hrv = async {
        if (has(DashCard.HRV_STATUS)) (29L downTo 0L).map { today.minusDays(it) }.map { d -> async { d to runCatching { Bridge.healthValue(ctx, Metric.HRV, d) }.getOrNull() } }.awaitAll()
        else emptyList()
    }
    val stress = async { if (has(DashCard.STRESS_DAY)) runCatching { HealthRepository.intraday(Metric.STRESS, today) }.getOrDefault(emptyList()) else emptyList() }
    val hourly = async { if (has(DashCard.DAY_TIMELINE)) runCatching { Bridge.hourlySteps(ctx, today) }.getOrDefault(emptyList()) else emptyList() }
    val ef = efforts.await()
    WatchSnap(charge.await(), chargeHist.await(), ef.lastOrNull(), ef, zones.await(), night.await(), debt.await(), score.await(), need.await(), hrv.await(), stress.await(), hourly.await())
}

// ---- Couleurs ------------------------------------------------------------------------------

private val Red = app.superhealth.ui.theme.Base.Red
private val Yellow = app.superhealth.ui.theme.Base.Yellow
private val Green = app.superhealth.ui.theme.Base.Green
internal fun chargeColor(score: Int) = when (Scores.band(score)) { 0 -> Red; 1 -> Yellow; else -> Green }
private val EffortBlue = app.superhealth.ui.theme.Base.Blue

private fun stageColor(k: ActivityKind) = when (k) {
    ActivityKind.DEEP_SLEEP -> Color(0xFF3634A3)
    ActivityKind.REM_SLEEP -> Color(0xFF5AC8FA)
    ActivityKind.AWAKE_SLEEP -> Color(0xFFFF7F66)
    else -> Color(0xFF0A84FF)
}

private val zoneColors = listOf(Color(0xFFB8BEC8), Color(0xFF7FB9F0), Color(0xFF3FBF8F), Color(0xFFF2C14E), Color(0xFFF08A3E), Color(0xFFE5484D))

/** Couleur d'un niveau de stress, comme Garmin : repos < 26, faible < 51, moyen < 76, élevé. */
private fun stressColor(v: Double) = when { v < 26 -> Color(0xFF3F8CFF); v < 51 -> Color(0xFFF2C14E); v < 76 -> Color(0xFFF08A3E); else -> Color(0xFFE5484D) }

// ---- Petites tailles -----------------------------------------------------------------------

internal fun watchCompact(c: DashCard, w: WatchSnap, nav: NavHostController): CompactData? = when (c) {
    DashCard.CHARGE -> CompactData(w.charge?.score?.toString() ?: "—", "%", tr("Charge"), w.charge?.score?.div(100f), w.chargeHistory, bars = false) {
        nav.go("h/metric/RECOVERY")
    }
    DashCard.EFFORT -> CompactData(w.effort?.let { formatNumber(it) } ?: "—", null, tr("Effort"), w.effort?.div(100)?.toFloat(), w.efforts.takeLast(7)) {
        nav.go("h/metric/EFFORT")
    }
    DashCard.SLEEP_STAGES -> {
        val asleep = w.night?.filter { it.kind != ActivityKind.AWAKE_SLEEP }?.sumOf { it.end - it.start }?.div(60_000.0)
        CompactData(asleep?.let { formatDuration(it) } ?: "—", null, tr("Sommeil"), asleep?.let { (it / Scores.sleepNeed()).toFloat() }) { nav.go("h/metric/SLEEP") }
    }
    DashCard.HRV_STATUS -> {
        val v = w.hrv.lastOrNull { it.second != null }?.second
        CompactData(v?.let { formatNumber(it) } ?: "—", "ms", tr("VFC"), null, w.hrv.map { it.second }, bars = false) { nav.go("h/metric/HRV") }
    }
    DashCard.STRESS_DAY -> {
        val avg = w.stress.map { it.value }.takeIf { it.isNotEmpty() }?.average()
        CompactData(avg?.let { formatNumber(it) } ?: "—", null, tr("Stress"), avg?.div(100)?.toFloat(), hourlyStress(w.stress).map { it }) { nav.go("h/metric/STRESS") }
    }
    DashCard.HR_ZONES -> {
        val active = w.zones?.drop(1)?.sum()
        CompactData(active?.let { trf("%1\$s min", it) } ?: "—", null, tr("Zones cardiaques"), null, w.zones?.drop(1)?.map { it.toDouble() }.orEmpty()) {
            nav.go("h/metric/EFFORT")
        }
    }
    DashCard.LOAD -> {
        val l = Scores.load(w.efforts)
        CompactData(l?.let { formatNumber(it.first / it.second.coerceAtLeast(1.0), 2) } ?: "—", null, tr("Aiguë / chronique"), null, w.efforts, bars = true) {
            nav.go("h/metric/EFFORT")
        }
    }
    DashCard.DAY_TIMELINE -> {
        val total = w.hourly.sumOf { it.value }
        CompactData(formatNumber(total), tr("pas"), tr("Ma journée"), null, hourlySteps(w.hourly)) { nav.go("h/metric/STEPS") }
    }
    else -> null
}

/** Pas des 24 heures du jour (null avant la première heure mesurée). */
private fun hourlySteps(pts: List<Point>): List<Double?> {
    val start = HealthRepository.startMs(LocalDate.now())
    val byHour = pts.associate { ((it.timestampMs - start) / 3_600_000).toInt() to it.value }
    return (0 until 24).map { byHour[it] }
}

private fun hourlyStress(pts: List<Point>): List<Double?> {
    val start = HealthRepository.startMs(LocalDate.now())
    val byHour = pts.groupBy { ((it.timestampMs - start) / 3_600_000).toInt() }.mapValues { e -> e.value.map { it.value }.average() }
    return (0 until 24).map { byHour[it] }
}

// ---- Grandes cartes ------------------------------------------------------------------------

/** Charge : jauge en arc colorée selon la bande, les moteurs du score et les 14 derniers matins. */
@Composable
internal fun ChargeCard(c: DashCard, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val score = w.charge?.score
    val col = score?.let(::chargeColor) ?: c.ink()
    Tile(c, tr("Charge"), modifier, onClick = { nav.go("h/metric/RECOVERY") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Gauge(score?.div(100f), col, Modifier.size(112.dp)) {
                Text(score?.let { "$it" } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = col)
                Text(if (score == null) tr("Calibrage") else if (w.charge?.withoutHrv == true) tr("sans VFC") else "%", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val d = w.charge?.drivers.orEmpty()
                listOf("hrv" to "VFC", "rhr" to "Pouls au repos", "sleep" to "Sommeil", "resp" to "Respiration").forEach { (k, label) ->
                    val z = d[k] ?: return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(label), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text(if (z >= 0.5) "▲" else if (z <= -0.5) "▼" else "•", color = if (z >= 0.5) Green else if (z <= -0.5) Red else Mf.colors.textSecondary,
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (d.isEmpty()) Text(tr("Quatre nuits de pouls au repos suffisent pour la calculer."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                // VFC de la semaine contre la zone normale, et phase du cycle quand elle sert de référence
                val note = listOfNotNull(
                    w.charge?.hrv?.let { when (it.zone) { -1 -> tr("VFC 7 j sous votre zone"); 1 -> tr("VFC 7 j au-dessus de votre zone"); else -> tr("VFC 7 j dans votre zone") } },
                    w.charge?.luteal?.let { if (it) tr("comparée à votre phase lutéale") else tr("comparée à votre phase folliculaire") },
                    // montre changée : la VFC n'est comparée qu'aux nuits du même appareil
                    w.charge?.hrvSource?.let { trf("VFC comparée aux nuits de %1\$s", it) },
                ).joinToString(" · ")
                if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 2)
            }
        }
        if (w.chargeHistory.count { it != null } >= 2) {
            Spacer(Modifier.height(10.dp))
            DotsChart(w.chargeHistory, Modifier.fillMaxWidth().height(40.dp)) { chargeColor(it.roundToInt()) }
        }
    }
}

/** Effort : jauge sur 100, minutes par zone du jour et barres des 7 derniers jours. */
@Composable
internal fun EffortCard(c: DashCard, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    Tile(c, tr("Effort"), modifier, onClick = { nav.go("h/metric/EFFORT") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Gauge(w.effort?.div(100)?.toFloat(), EffortBlue, Modifier.size(112.dp)) {
                Text(w.effort?.let { formatNumber(it) } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = EffortBlue)
                Text("/ 100", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                val days = (6L downTo 0L).map { LocalDate.now().minusDays(it) }
                BarChart(w.efforts.takeLast(7), emptyList(), EffortBlue, height = 70.dp, track = softTrack(), labels = days.map { dayLetter(it) })
            }
        }
        // Effort à viser selon la Forme du jour (seulement quand la Forme a un conseil complet)
        val form = app.superhealth.ui.wellbeing.rememberForm() as? app.superhealth.wellbeing.Form.Advice
        form?.let { a -> app.superhealth.wellbeing.Form.effortRange(a.verdict, w.efforts.dropLast(1)) }?.let { (lo, hi) ->
            Spacer(Modifier.height(8.dp))
            Text(trf("À viser aujourd'hui : %1\$s–%2\$s, selon votre forme", formatNumber(lo), formatNumber(hi)),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
}

/** Arc de jauge (270°) avec le contenu au centre. */
@Composable
private fun Gauge(fraction: Float?, color: Color, modifier: Modifier, content: @Composable () -> Unit) {
    val track = softTrack()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension * 0.10f
            val box = Size(size.minDimension - s, size.minDimension - s)
            val tl = Offset((size.width - box.width) / 2, (size.height - box.height) / 2)
            drawArc(track, 135f, 270f, false, tl, box, style = Stroke(s, cap = StrokeCap.Round))
            fraction?.takeIf { it > 0f }?.let { drawArc(color, 135f, 270f * it.coerceAtMost(1f), false, tl, box, style = Stroke(s, cap = StrokeCap.Round)) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/** Points colorés par valeur (une journée par point), reliés par un trait discret. */
@Composable
private fun DotsChart(values: List<Double?>, modifier: Modifier, color: (Double) -> Color) {
    val line = Mf.colors.divider
    Canvas(modifier) {
        val pts = values.withIndex().mapNotNull { (i, v) -> v?.let { i to it } }
        val lo = 0.0; val hi = 100.0
        fun x(i: Int) = 6.dp.toPx() + (size.width - 12.dp.toPx()) * i / (values.size - 1).coerceAtLeast(1)
        fun y(v: Double) = 5.dp.toPx() + (size.height - 10.dp.toPx()) * (1 - ((v - lo) / (hi - lo)).toFloat())
        val p = Path(); p.smooth(pts.map { Offset(x(it.first), y(it.second)) })
        drawPath(p, line, style = Stroke(ChartStyle.reference.toPx()))
        pts.forEach { (i, v) -> drawCircle(color(v), 4.dp.toPx(), Offset(x(i), y(v))) }
    }
}

/**
 * Phases de la nuit : hypnogramme (éveil en haut, profond en bas), heures de coucher et de lever, durée et part de
 * chaque phase, et la dette de sommeil à rattraper.
 */
@Composable
internal fun SleepStagesCard(c: DashCard, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val night = w.night
    Tile(c, tr("Phases de sommeil"), modifier, onClick = { nav.go("h/metric/SLEEP") },
        trailing = night?.let { timeOf(it.first().start) + " – " + timeOf(it.last().end) }) {
        if (night.isNullOrEmpty()) {
            Text(tr("La nuit apparaîtra après la prochaine synchronisation."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
            return@Tile
        }
        val total = night.sumOf { it.end - it.start }.toDouble()
        val asleep = night.filter { it.kind != ActivityKind.AWAKE_SLEEP }.sumOf { it.end - it.start } / 60_000.0
        Row(verticalAlignment = Alignment.Bottom) {
            Value(formatDuration(asleep), color = c.ink().onContainer())
            w.sleepScore?.let { Sub(" · " + tr(Scores.restWord(it, asleep, w.needMin ?: Scores.sleepNeed()).label)) }
            Spacer(Modifier.weight(1f))
            w.debt?.let { d -> Sub(if (d <= 0) tr("Aucune dette de sommeil") else trf("Dette %1\$s", formatDuration(d))) }
        }
        // effet nocebo : un score bas annoncé à quelqu'un qui se sent reposé dégrade sa journée (Gavriloff 2018,
        // Draganich 2014) ; le ressenti prédit mieux la fatigue du lendemain que la mesure
        val feel = app.superhealth.wellbeing.Checkin.today()
        if (feel != null && feel.sleep >= 4 && (w.sleepScore ?: 100.0) < 55) {
            Spacer(Modifier.height(4.dp))
            Text(tr("Vous vous sentez reposé : votre ressenti compte plus que le score."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Hypnogram(night, Modifier.fillMaxWidth().height(86.dp))
        Spacer(Modifier.height(10.dp))
        // Appli qui ne note que la durée (pas de phases) : coucher, lever et temps au lit plutôt que des phases vides.
        if (!Bridge.hasStages(night)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(tr("Coucher") to timeOf(night.first().start), tr("Lever") to timeOf(night.last().end),
                tr("Au lit") to formatDuration((night.last().end - night.first().start) / 60_000.0)).forEach { (l, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(l, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1)
                    Text(v, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(ActivityKind.AWAKE_SLEEP to "Éveil", ActivityKind.REM_SLEEP to "Paradoxal", ActivityKind.LIGHT_SLEEP to "Léger", ActivityKind.DEEP_SLEEP to "Profond").forEach { (k, l) ->
                val ms = night.filter { it.kind == k || (k == ActivityKind.LIGHT_SLEEP && it.kind == ActivityKind.SLEEP_ANY) }.sumOf { it.end - it.start }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(stageColor(k)))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(l), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                    Text(formatDuration(ms / 60_000.0), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("${(100 * ms / total).roundToInt()} %", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
                }
            }
        }
    }
}

/** Hypnogramme : un palier par phase, arrondi, de la couleur de la phase. */
@Composable
private fun Hypnogram(night: List<HealthRepository.SleepPhase>, modifier: Modifier) {
    val grid = Mf.colors.divider
    Canvas(modifier) {
        val t0 = night.first().start; val t1 = night.last().end
        // sans phases : une seule bande « endormi », les réveils en creux
        val staged = Bridge.hasStages(night)
        val levels = if (staged) 4 else 1
        val rowH = size.height / levels
        for (k in 0 until levels) drawLine(grid, Offset(0f, rowH * (k + 0.5f)), Offset(size.width, rowH * (k + 0.5f)), ChartStyle.grid.toPx(), pathEffect = ChartStyle.dash(this))
        fun row(k: ActivityKind) = if (!staged) 0 else when (k) { ActivityKind.AWAKE_SLEEP -> 0; ActivityKind.REM_SLEEP -> 1; ActivityKind.DEEP_SLEEP -> 3; else -> 2 }
        night.filter { staged || it.kind != ActivityKind.AWAKE_SLEEP }.forEach { p ->
            val x0 = size.width * (p.start - t0) / (t1 - t0).toFloat()
            val x1 = size.width * (p.end - t0) / (t1 - t0).toFloat()
            val r = row(p.kind)
            val h = if (staged) rowH * 0.62f else rowH * 0.42f
            drawRoundRect(stageColor(p.kind), Offset(x0, rowH * r + (rowH - h) / 2), Size((x1 - x0).coerceAtLeast(2f), h), CornerRadius(h / 2))
        }
    }
}

/** Variabilité cardiaque : 30 nuits, bande de référence (moyenne ± écart des nuits précédentes) et moyenne sur 7 jours. */
@Composable
internal fun HrvStatusCard(c: DashCard, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val pts = w.hrv.mapNotNull { (d, v) -> v?.let { d.toEpochDay() to it } }
    val last = pts.lastOrNull()?.second
    val week = pts.filter { it.first > LocalDate.now().toEpochDay() - 7 }.map { it.second }.takeIf { it.isNotEmpty() }?.average()
    val base = Scores.baseline(pts.dropLast(1).map { it.second }, Scores.HRV)
    Tile(c, tr("Variabilité cardiaque"), modifier, onClick = { nav.go("h/metric/HRV") }, bottom = {
        if (pts.size >= 2) {
            val band = base?.let { b -> pts.map { Triple(it.first as Number, b.mean - 1.253 * b.spread, b.mean + 1.253 * b.spread) } }
            LineChart(listOf(Series(pts.map { it.first to it.second }, c.ink(), dots = true),
                Series(pts.windowed(7, partialWindows = true).map { win -> win.last().first to win.map { it.second }.average() }, c.ink(), band = band)),
                height = 64.dp, showAxis = false)
        }
    }) {
        Value(last?.let { formatNumber(it) } ?: "—", "ms", color = c.ink().onContainer())
        Sub(when {
            base == null || week == null -> week?.let { trf("Moy. 7 j %1\$s ms", formatNumber(it)) }
            week > base.mean + 1.253 * base.spread -> tr("Au-dessus de votre référence")
            week < base.mean - 1.253 * base.spread -> tr("Sous votre référence")
            else -> tr("Dans votre référence")
        })
    }
}

/** Stress de la journée : barres par quart d'heure aux couleurs des niveaux, moyenne et temps au repos. */
@Composable
internal fun StressDayCard(c: DashCard, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val start = HealthRepository.startMs(LocalDate.now())
    val quarters = w.stress.groupBy { ((it.timestampMs - start) / 900_000).toInt() }.mapValues { e -> e.value.map { it.value }.average() }
    val avg = w.stress.map { it.value }.takeIf { it.isNotEmpty() }?.average()
    val track = softTrack()
    Tile(c, tr("Stress de la journée"), modifier, onClick = { nav.go("h/metric/STRESS") }, bottom = {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val n = 96; val slot = size.width / n; val bw = slot * 0.7f
            drawRoundRect(track, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
            quarters.forEach { (q, v) ->
                if (q !in 0 until n) return@forEach
                val h = (size.height * v / 100).toFloat().coerceAtLeast(2.dp.toPx())
                drawRoundRect(stressColor(v), Offset(q * slot, size.height - h), Size(bw, h), CornerRadius(bw / 2))
            }
        }
    }) {
        Value(avg?.let { formatNumber(it) } ?: "—", "/ 100", color = c.ink().onContainer())
        val rest = w.stress.count { it.value < 26 }
        Sub(if (w.stress.isEmpty()) null else trf("Au repos %1\$s %%", (100.0 * rest / w.stress.size).roundToInt()))
    }
}

/** Zones cardiaques du jour : barre empilée et minutes par zone (réserve cardiaque, comme l'Effort). */
@Composable
internal fun HrZonesCard(c: DashCard, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val z = w.zones
    Tile(c, tr("Zones cardiaques"), modifier, onClick = { nav.go("h/metric/EFFORT") }) {
        if (z == null || z.sum() == 0) { Text(tr("Pas encore de pouls aujourd'hui."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary); return@Tile }
        val active = z.drop(1)
        Canvas(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
            val tot = active.sum().coerceAtLeast(1).toFloat()
            var x = 0f
            active.forEachIndexed { i, m -> val w2 = size.width * m / tot; drawRect(zoneColors[i + 1], Offset(x, 0f), Size(w2, size.height)); x += w2 }
            if (active.sum() == 0) drawRect(zoneColors[0], Offset.Zero, size)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (1..5).forEach { k ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(zoneColors[k]), contentAlignment = Alignment.Center) {
                        Text("$k", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(trf("%1\$s min", z[k]), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

/** Charge d'entraînement : Effort des 28 jours, moyennes aiguë (7 j) et chronique (28 j) et leur rapport. */
@Composable
internal fun LoadCard(c: DashCard, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val l = Scores.load(w.efforts)
    Tile(c, tr("Charge d'entraînement"), modifier, onClick = { nav.go("h/metric/EFFORT") }, bottom = {
        val pts = w.efforts.withIndex().mapNotNull { (i, v) -> v?.let { i to it } }
        val acute = w.efforts.indices.map { i -> i to w.efforts.subList(maxOf(0, i - 6), i + 1).map { it ?: 0.0 }.average() }
        val chronic = w.efforts.indices.map { i -> i to w.efforts.subList(0, i + 1).map { it ?: 0.0 }.average() }
        if (pts.size >= 2) LineChart(listOf(Series(chronic, Mf.colors.textSecondary, dashed = true), Series(pts, c.ink(), dots = true), Series(acute, c.ink())),
            height = 56.dp, showAxis = false)
    }) {
        val ratio = l?.let { it.first / it.second.coerceAtLeast(1.0) }
        Value(ratio?.let { formatNumber(it, 2) } ?: "—", color = c.ink().onContainer())
        Sub(when {
            ratio == null -> tr("Deux semaines de pouls suffisent")
            ratio > 1.5 -> tr("Hausse brutale")
            ratio > 1.3 -> tr("Charge en hausse")
            ratio < 0.8 -> tr("Charge en baisse")
            else -> tr("Charge stable")
        })
    }
}

/** Ma journée : pas heure par heure et pouls par-dessus. */
@Composable
internal fun DayTimelineCard(c: DashCard, h: HealthSnap, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val steps = hourlySteps(w.hourly)
    val hue = HealthHues.Activity
    val heartHue = HealthHues.Heart
    val start = HealthRepository.startMs(LocalDate.now())
    Tile(c, tr("Ma journée"), modifier, onClick = { nav.go("h/metric/STEPS") }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Value(formatNumber(steps.sumOf { it ?: 0.0 }), tr("pas"), color = hue.onContainer())
            Spacer(Modifier.weight(1f))
            h.heartToday.map { it.value }.takeIf { it.isNotEmpty() }?.let { Sub(trf("%1\$s–%2\$s bpm", it.min().roundToInt(), it.max().roundToInt())) }
        }
        Spacer(Modifier.height(10.dp))
        val track = softTrack()
        Box(Modifier.fillMaxWidth().height(84.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val slot = size.width / 24; val bw = (slot * ChartStyle.BAR_FILL).coerceAtMost(ChartStyle.barMax.toPx())
                val max = steps.filterNotNull().maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                steps.forEachIndexed { i, v ->
                    val x = i * slot + (slot - bw) / 2
                    drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
                    if (v != null && v > 0) { val bh = (size.height * v / max).toFloat().coerceAtLeast(bw); drawRoundRect(hue, Offset(x, size.height - bh), Size(bw, bh), CornerRadius(bw / 2)) }
                }
                val hr = h.heartToday
                if (hr.size >= 2) {
                    val lo = hr.minOf { it.value } - 5; val hi = hr.maxOf { it.value } + 5
                    val p = Path()
                    p.smooth(hr.map { Offset(size.width * (it.timestampMs - start) / 86_400_000f, size.height * (1 - ((it.value - lo) / (hi - lo)).toFloat())) })
                    drawPath(p, heartHue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("0 h", "6 h", "12 h", "18 h", "24 h").forEachIndexed { i, l ->
                Text(l, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
                if (i < 4) Spacer(Modifier.weight(1f))
            }
        }
    }
}
