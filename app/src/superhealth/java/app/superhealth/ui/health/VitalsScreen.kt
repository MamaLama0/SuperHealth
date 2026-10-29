package app.superhealth.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Metric
import app.superhealth.health.data.NightVitals
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.charts.LineChart
import app.superhealth.ui.charts.Series
import app.superhealth.ui.charts.SignedBarChart
import app.superhealth.ui.formatNumber
import app.superhealth.ui.formatValue
import app.superhealth.ui.go
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.unitOf
import app.superhealth.vitalMetrics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/*
 * Écran Constantes : la nuit face à votre plage habituelle (comme l'app Signes vitaux de l'Apple Watch), puis chaque mesure
 * sur 7, 30 ou 90 jours avec sa plage habituelle en bande (comme les « Stats importantes » de Google Health). La température
 * se lit en écart à votre normale (comme Oura).
 */

/** Couleur d'un statut : vert dans l'habituel, ambre au-dehors, gris sans référence. */
internal fun NightVitals.Status.color(): Color = when (this) {
    NightVitals.Status.HIGH, NightVitals.Status.LOW -> HealthHues.Mind
    NightVitals.Status.TYPICAL -> HealthHues.Activity
    NightVitals.Status.UNKNOWN -> Color(0xFF8E9199)
}

internal fun NightVitals.Status.word(): String? = when (this) {
    NightVitals.Status.HIGH -> tr("Élevé"); NightVitals.Status.LOW -> tr("Bas"); NightVitals.Status.TYPICAL -> tr("Habituel"); else -> null
}

/** Dernière nuit lue (tableau de bord) : réaffichée au retour, le temps de relire. */
private var lastNight: NightVitals.Night? = null

@Composable
fun nightVitalsState(vm: SuperViewModel): NightVitals.Night? {
    val refresh by vm.refresh.collectAsState()
    val ctx = LocalContext.current
    val night by produceState(lastNight, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { NightVitals.of(ctx) }.getOrNull() }?.also { lastNight = it } ?: value
    }
    return night
}

/** En-tête (tout est dans la normale, ou nombre de mesures hors de l'habituel) puis une ligne par mesure. */
@Composable
fun NightVitalsBody(night: NightVitals.Night?, onMetric: ((Metric) -> Unit)?, compact: Boolean = false) {
    val n = night
    if (n == null || n.measured == 0) {
        Text(tr("Portez la montre la nuit : vos signes vitaux apparaissent après quelques nuits."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        return
    }
    val ok = n.outliers == 0
    val hue = if (ok) HealthHues.Activity else HealthHues.Mind
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, Modifier.size(22.dp), tint = hue)
        Spacer(Modifier.width(8.dp))
        Text(if (ok) tr("Dans votre plage habituelle") else if (n.outliers == 1) tr("1 mesure hors de l'habituel") else trf("%1\$s mesures hors de l'habituel", n.outliers),
            style = MaterialTheme.typography.titleMedium, color = hue.onContainer())
    }
    Spacer(Modifier.height(10.dp))
    val items = if (compact) n.items.filter { it.value != null }.take(4) else n.items
    items.forEachIndexed { i, it ->
        if (i > 0) Spacer(Modifier.height(10.dp))
        VitalRangeRow(it, onMetric)
    }
}

@Composable
private fun VitalRangeRow(item: NightVitals.Item, onClick: ((Metric) -> Unit)?) {
    val m = item.metric
    val c = item.status.color()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(if (onClick != null) Modifier.clickable { onClick(m) } else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(m.icon, null, Modifier.size(16.dp), tint = m.color)
            Spacer(Modifier.width(6.dp))
            Text(tr(m.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.value?.let { formatValue(m, it) + (unitOf(m)?.let { u -> " $u" } ?: "") } ?: "—", style = MaterialTheme.typography.titleSmall,
                color = if (item.status == NightVitals.Status.UNKNOWN) Mf.colors.textSecondary else c.onContainer())
        }
        RangeBar(item.value, item.low, item.high, c, Modifier.padding(top = 5.dp))
    }
}

/** Barre de plage : l'habituel occupe le milieu, la valeur s'y place ou dépasse d'un côté. */
@Composable
internal fun RangeBar(v: Double?, lo: Double?, hi: Double?, c: Color, modifier: Modifier = Modifier) {
    if (lo == null || hi == null || v == null) return
    val span = (hi - lo).coerceAtLeast(1e-6)
    val f = (((v - lo) / span) * 0.5 + 0.25).coerceIn(0.02, 0.98).toFloat()
    // dessin direct (pas de BoxWithConstraints : la carte du tableau de bord se mesure en hauteur intrinsèque)
    val track = Mf.colors.chip
    val band = HealthHues.Activity.container(1.6f)
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val h = 6.dp.toPx(); val y = (size.height - h) / 2
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), r)
        drawRoundRect(band, Offset(size.width * 0.25f, y), Size(size.width * 0.5f, h), r)
        drawCircle(c, size.height / 2, Offset(size.width * f, size.height / 2))
    }
}

/** Mesures de l'écran : celles des signes vitaux de la nuit, puis les constantes choisies pour la carte. */
internal fun vitalsList(): List<Metric> = (NightVitals.available() +
    app.superhealth.SuperPrefs.settings.value.vitalMetrics.filter { Bridge.hasSource(it) && it != Metric.STEPS }).distinct()

/** Une mesure sur la période et sa plage habituelle (28 jours avant la fin de la période). */
internal class VitalTrend(val metric: Metric, val days: List<Pair<LocalDate, Double?>>, val range: Pair<Double, Double>?) {
    val last: Pair<LocalDate, Double>? get() = days.lastOrNull { it.second != null }?.let { it.first to it.second!! }
    val status: NightVitals.Status get() = NightVitals.status(last?.second, range)
}

/** Séries des mesures sur [days] jours jusqu'à [end], plage calculée sur les 28 jours qui précèdent la dernière valeur. */
internal suspend fun loadTrends(ctx: Context, metrics: List<Metric>, end: LocalDate, days: Int): List<VitalTrend> = coroutineScope {
    metrics.map { m ->
        async {
            val s = NightVitals.series(ctx, m, end.minusDays(maxOf(days, 29).toLong() - 1), end)
            val shown = s.takeLast(days)
            val lastIdx = s.indexOfLast { it.second != null }
            val past = if (lastIdx > 0) s.subList(maxOf(0, lastIdx - 28), lastIdx).mapNotNull { it.second } else emptyList()
            VitalTrend(m, shown, NightVitals.range(past, m))
        }
    }.awaitAll()
}

private enum class VitalsSpan(val days: Int, val label: String) { WEEK(7, "7 j"), MONTH(30, "30 j"), QUARTER(90, "90 j") }

private var lastTrends: Pair<Int, List<VitalTrend>>? = null

@Composable
fun VitalsScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    var offset by remember { mutableIntStateOf(0) }
    var span by remember { mutableStateOf(VitalsSpan.MONTH) }
    val date = LocalDate.now().minusDays(offset.toLong())
    val night by produceState(lastNight?.takeIf { offset == 0 }, date, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { NightVitals.of(ctx, date) }.getOrNull() }
    }
    val trends by produceState(lastTrends?.takeIf { it.first == span.days }?.second, span, refresh) {
        value = withContext(Dispatchers.IO) { loadTrends(ctx, vitalsList(), LocalDate.now(), span.days) }.also { lastTrends = span.days to it }
    }
    DetailScaffold(tr("Constantes"), onBack = { nav.popBackStack() }, action = {
        IconButton(onClick = { nav.go("vitals/edit") }) { Icon(Icons.Rounded.Tune, tr("Personnaliser")) }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, tr("Précédent")) { offset++ }
            Text(if (offset == 0) tr("Dernière nuit") else date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", app.superhealth.ui.Lang.locale))
                .replaceFirstChar { it.uppercase(app.superhealth.ui.Lang.locale) },
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            RoundButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, tr("Suivant"), enabled = offset > 0) { if (offset > 0) offset-- }
        }
        Spacer(Modifier.height(12.dp))
        MfCard { NightVitalsBody(night, onMetric = { nav.go("h/metric/${it.name}") }) }
        app.superhealth.ui.coach.CoachChip(tr("Expliquer avec le coach"),
            tr("Explique mes constantes de la dernière nuit et leurs tendances sur 30 jours : qu'est-ce qui sort de ma normale et pourquoi (sommeil, entraînement, alimentation) ?"),
            nav, Modifier.padding(top = 10.dp))

        SectionLabel(tr("Tendances"))
        ChipChoice(VitalsSpan.entries, span, { tr(it.label) }) { span = it }
        trends.orEmpty().filter { t -> t.days.any { it.second != null } }.forEach { t ->
            Spacer(Modifier.height(10.dp))
            VitalTrendCard(t) { nav.go("h/metric/${t.metric.name}") }
        }
        Spacer(Modifier.height(14.dp))
        Text(tr("Plage habituelle : médiane de vos 28 nuits précédentes ± 2 écarts robustes (méthode SuperHealth). Ce ne sont pas des diagnostics médicaux."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** Une mesure : valeur, statut, courbe de la période avec la plage habituelle en bande ; la température en écart à la normale. */
@Composable
private fun VitalTrendCard(t: VitalTrend, onClick: () -> Unit) {
    val m = t.metric
    val last = t.last
    val st = t.status
    MfCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(m.icon, null, Modifier.size(18.dp), tint = m.color)
            Spacer(Modifier.width(8.dp))
            Text(tr(m.label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            st.word()?.let { w ->
                Text(w, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = st.color().onContainer(), maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(st.color().container(1.4f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(last?.let { formatValue(m, it.second) } ?: "—", style = MaterialTheme.typography.headlineMedium, color = m.color.onContainer(), maxLines = 1)
            unitOf(m)?.takeIf { last != null }?.let { Text(" $it", style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp)) }
            Spacer(Modifier.weight(1f))
            t.range?.let { (lo, hi) ->
                Text(trf("Habituel %1\$s–%2\$s", formatValue(m, lo), formatValue(m, hi)), style = MaterialTheme.typography.bodySmall,
                    color = Mf.colors.textSecondary, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        val pts = t.days.mapNotNull { (d, v) -> v?.let { d.toEpochDay() to it } }
        val x0 = t.days.first().first.toEpochDay().toDouble(); val x1 = t.days.last().first.toEpochDay().toDouble()
        val r = t.range
        if (m == Metric.TEMPERATURE && r != null) {
            // écart à la normale (milieu de la plage), en barres de part et d'autre de zéro
            val mid = (r.first + r.second) / 2
            SignedBarChart(t.days.map { it.second?.minus(mid) }, HealthHues.Fitness, HealthHues.Respiration, height = 110.dp,
                format = { (if (it > 0) "+" else "") + formatNumber(it, 1) })
        } else {
            val band = r?.let { (lo, hi) -> listOf(Triple(x0 as Number, lo, hi), Triple(x1 as Number, lo, hi)) }
            LineChart(listOf(Series(emptyList(), m.color, band = band), Series(pts, m.color, markers = pts.size <= 31, gap = 3.0)), height = 120.dp,
                xFrom = x0, xTo = x1, format = { formatValue(m, it) })
        }
        val vs = pts.map { it.second }
        if (vs.size >= 2) {
            Spacer(Modifier.height(10.dp))
            StatGrid(listOf(tr("Min.") to formatValue(m, vs.min()), tr("Moy.") to formatValue(m, vs.average()), tr("Max.") to formatValue(m, vs.max())))
        }
    }
}
