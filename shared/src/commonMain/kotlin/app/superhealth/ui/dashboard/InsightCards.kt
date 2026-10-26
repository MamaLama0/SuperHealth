package app.superhealth.ui.dashboard

import app.superhealth.platform.*

import app.superhealth.ui.theme.dashCardBackground
import app.superhealth.ui.charts.smooth
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.SuperPrefs
import app.superhealth.insights.Correlation
import app.superhealth.insights.InsightData
import app.superhealth.insights.Insights
import app.superhealth.insights.icon
import app.superhealth.insights.route
import app.superhealth.insights.source
import app.superhealth.insights.text
import app.superhealth.nutrition.domain.Settings as NutritionSettings
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.weightValue
import app.superhealth.training.WorkoutApp
import app.superhealth.training.ui.volume
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.formatDuration
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.platform.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Derniers constats lus : affichés tout de suite au retour sur l'onglet. */
private var lastInsights: List<Insights.Insight>? = null

/**
 * Insights : phrases calculées sur les données (voir [Insights]), les plus marquantes d'abord. Demi : 2 phrases,
 * pleine largeur : 4. Chaque phrase ouvre l'écran de sa mesure.
 */
@Composable
internal fun InsightsCard(c: DashCard, size: CardSize, vm: SuperViewModel, s: NutritionSettings, modifier: Modifier, nav: NavHostController) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val list by produceState(lastInsights, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { InsightData.insights(ctx, force = refresh > 0 && lastInsights != null) }.getOrDefault(emptyList()) }
            .also { lastInsights = it }
    }
    val shown = list.orEmpty().take(if (size == CardSize.L) 4 else 2)
    if (size != CardSize.L) {
        // moyen et bandeau : le constat le plus marquant seul, sans titre (le symbole et la couleur suffisent)
        val i = list.orEmpty().firstOrNull()
        val hue = i?.source?.color ?: c.ink()
        Row(modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).dashCardBackground()
            .cardClick(i?.let { { nav.go(it.route) } }).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(hue.container()), contentAlignment = Alignment.Center) {
                Icon(i?.icon ?: c.icon(), null, Modifier.size(19.dp), tint = hue)
            }
            Spacer(Modifier.width(10.dp))
            Text(i?.text(s) ?: tr("Les constats arrivent après quelques jours de suivi."), Modifier.weight(1f),
                style = if (size == CardSize.M) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                maxLines = if (size == CardSize.M) 4 else 2, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Tile(c, tr("Insights"), modifier, tinted = false, onClick = null, padding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)) {
        if (list != null && shown.isEmpty()) Text(tr("Les constats arrivent après quelques jours de suivi."), style = MaterialTheme.typography.bodyMedium,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        shown.forEach { i ->
            val hue = i.source.color
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.go(i.route) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(hue.container()), contentAlignment = Alignment.Center) {
                    Icon(i.icon, null, Modifier.size(17.dp), tint = hue)
                }
                Spacer(Modifier.width(10.dp))
                Text(i.text(s), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
            }
        }
    }
}

/** Valeur d'une série, avec son unité. */
internal fun InsightData.Source.format(v: Double, s: NutritionSettings): String = when (this) {
    InsightData.Source.SLEEP -> formatDuration(v)
    InsightData.Source.STEPS, InsightData.Source.ENERGY, InsightData.Source.STRESS -> formatNumber(v)
    InsightData.Source.WEIGHT -> formatNumber(s.weightValue(v), 1) + " " + tr(s.weightUnit.label)
    InsightData.Source.VOLUME -> WorkoutApp.store.state.value.settings.volume(v)
    InsightData.Source.KCAL -> formatNumber(s.energyValue(v)) + " " + tr(s.energyUnit.label)
    InsightData.Source.PROTEIN -> formatNumber(v) + " g"
    InsightData.Source.RESTING_HR -> formatNumber(v) + " " + tr("bpm")
}

/** Séries déjà lues : la carte réapparaît sans clignoter quand on y revient en faisant défiler. */
private val corrCache = HashMap<Triple<InsightData.Source, InsightData.Source, Int>, Pair<Map<LocalDate, Double>, Map<LocalDate, Double>>>()

private fun sources(names: List<String>): Pair<InsightData.Source, InsightData.Source> {
    val all = InsightData.Source.entries
    val a = all.firstOrNull { it.name == names.getOrNull(0) } ?: InsightData.Source.SLEEP
    val b = all.firstOrNull { it.name == names.getOrNull(1) }?.takeIf { it != a } ?: all.first { it != a }
    return a to b
}

/**
 * Corrélations : deux séries au choix superposées sur 30 ou 90 jours (chacune sur sa propre échelle), et le coefficient de
 * Pearson des jours où les deux existent. « Lien » seulement si |t| ≥ 2 sur au moins 7 jours communs.
 */
@Composable
internal fun CorrelationCard(c: DashCard, size: CardSize, vm: SuperViewModel, s: NutritionSettings, modifier: Modifier) {
    val ctx = LocalContext.current
    val sh by vm.settings.collectAsState()
    val refresh by vm.refresh.collectAsState()
    val (a, b) = sources(sh.corr)
    val days = sh.corrDays
    val key = Triple(a, b, days)
    val data by produceState(corrCache[key], a, b, days, refresh) {
        value = withContext(Dispatchers.IO) {
            coroutineScope {
                val x = async { runCatching { InsightData.series(ctx, a, days) }.getOrDefault(emptyMap()) }
                val y = async { runCatching { InsightData.series(ctx, b, days) }.getOrDefault(emptyMap()) }
                x.await() to y.await()
            }
        }.also { corrCache[key] = it }
    }
    fun pick(i: Int, src: InsightData.Source) = SuperPrefs.update { st ->
        val (pa, pb) = sources(st.corr)
        val l = if (i == 0) listOf(src, if (src == pb) pa else pb) else listOf(if (src == pa) pb else pa, src)
        st.copy(corr = l.map { it.name })
    }
    val r = data?.let { Correlation.pearson(it.first, it.second) }
    val rText = r?.takeIf { it.n >= Correlation.MIN_DAYS }?.let { "r " + formatNumber(it.r, 2) } ?: "—"
    if (size != CardSize.L) {
        // petit, moyen, bandeau : les deux symboles, les courbes et r ; les séries se choisissent en grand
        val pair = @Composable {
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(a, b).forEachIndexed { k, src ->
                    if (k > 0) Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(26.dp).clip(CircleShape).background(src.color.container()), contentAlignment = Alignment.Center) {
                        Icon(src.icon, null, Modifier.size(15.dp), tint = src.color)
                    }
                }
            }
        }
        val rLabel = @Composable {
            Text(rText, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = if (r?.significant == true) Mf.colors.text else Mf.colors.textSecondary, maxLines = 1)
        }
        val box = modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).dashCardBackground().cardClick(null)
        when (size) {
            CardSize.S -> Column(box.square().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                pair(); Spacer(Modifier.weight(1f)); rLabel(); Spacer(Modifier.weight(1f))
            }
            CardSize.W -> Row(box.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                pair(); Spacer(Modifier.width(10.dp))
                DualChart(data?.first.orEmpty(), data?.second.orEmpty(), a, b, days, Modifier.weight(1f).height(36.dp))
                Spacer(Modifier.width(10.dp)); rLabel()
            }
            else -> Column(box.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { pair(); Spacer(Modifier.weight(1f)); rLabel() }
                Spacer(Modifier.height(10.dp))
                DualChart(data?.first.orEmpty(), data?.second.orEmpty(), a, b, days, Modifier.fillMaxWidth().height(80.dp))
            }
        }
        return
    }
    Tile(c, tr("Corrélations"), modifier, tinted = false, onClick = null) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceChip(a, data?.first, s, Modifier.weight(1f)) { pick(0, it) }
            SourceChip(b, data?.second, s, Modifier.weight(1f)) { pick(1, it) }
        }
        Spacer(Modifier.height(10.dp))
        val d = data
        DualChart(d?.first.orEmpty(), d?.second.orEmpty(), a, b, days, Modifier.fillMaxWidth().height(120.dp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(when {
                d == null -> ""
                r == null || r.n < Correlation.MIN_DAYS -> tr("Pas assez de jours en commun")
                !r.significant || Correlation.strength(r.r) == Correlation.Strength.NONE -> trf("Pas de lien net · r = %1\$s sur %2\$s j", formatNumber(r.r, 2), r.n)
                r.r > 0 -> trf("Varient ensemble · r = %1\$s sur %2\$s j", formatNumber(r.r, 2), r.n)
                else -> trf("Varient en sens inverse · r = %1\$s sur %2\$s j", formatNumber(r.r, 2), r.n)
            }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            listOf(30, 90).forEach { n ->
                val on = n == days
                Text(trf("%1\$s j", n), style = MaterialTheme.typography.labelMedium, color = if (on) Mf.colors.onStrong else Mf.colors.text, maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp).clip(CircleShape).background(if (on) Mf.colors.strong else Mf.colors.chip)
                        .clickable { SuperPrefs.update { it.copy(corrDays = n) } }.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

/** Série choisie : pastille de couleur, nom, moyenne ; touche = liste des séries disponibles. */
@Composable
private fun SourceChip(src: InsightData.Source, values: Map<LocalDate, Double>?, s: NutritionSettings, modifier: Modifier, onPick: (InsightData.Source) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(src.color.container()).clickable { open = true }
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(src.icon, null, Modifier.size(16.dp), tint = src.color)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(src.label), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(values?.values?.takeIf { it.isNotEmpty() }?.average()?.let { trf("moy. %1\$s", src.format(it, s)) } ?: "—",
                    style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ArrowDropDown, null, tint = Mf.colors.textSecondary)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Mf.colors.card) {
            InsightData.available().forEach { o ->
                DropdownMenuItem(text = { Text(tr(o.label)) }, leadingIcon = { Icon(o.icon, null, tint = o.color) }, onClick = { open = false; onPick(o) })
            }
        }
    }
}

/** Deux courbes sur [days] jours, chacune sur sa propre échelle (min–max) ; une série de séances est tracée en points. */
@Composable
private fun DualChart(a: Map<LocalDate, Double>, b: Map<LocalDate, Double>, sa: InsightData.Source, sb: InsightData.Source, days: Int, modifier: Modifier) {
    val grid = Mf.colors.divider
    Canvas(modifier) {
        val today = LocalDate.now()
        val start = today.minusDays(days - 1L)
        val pad = 6.dp.toPx()
        val h = size.height - pad * 2
        for (k in 0..2) drawLine(grid, Offset(0f, pad + h * k / 2), Offset(size.width, pad + h * k / 2), strokeWidth = app.superhealth.ui.charts.ChartStyle.grid.toPx())
        fun x(d: LocalDate) = size.width * (d.toEpochDay() - start.toEpochDay()) / (days - 1).coerceAtLeast(1).toFloat()
        listOf(a to sa, b to sb).forEach { (m, src) ->
            val pts = m.filterKeys { !it.isBefore(start) }.toSortedMap()
            if (pts.isEmpty()) return@forEach
            val lo = pts.values.min(); val hi = pts.values.max()
            fun y(v: Double) = pad + h * (1 - if (hi > lo) ((v - lo) / (hi - lo)).toFloat() else 0.5f)
            if (src.sparse || pts.size < 2) pts.forEach { (d, v) -> drawCircle(src.color, app.superhealth.ui.charts.ChartStyle.endDot.toPx(), Offset(x(d), y(v))) }
            else {
                // trou de plus de 3 jours : la courbe s'interrompt ; tracé lisse et fidèle de la charte commune
                val segs = mutableListOf<MutableList<Offset>>()
                var prev: LocalDate? = null
                pts.forEach { (d, v) ->
                    if (prev == null || d.toEpochDay() - prev!!.toEpochDay() > 3) segs += mutableListOf<Offset>()
                    segs.last() += Offset(x(d), y(v)); prev = d
                }
                val path = Path()
                segs.forEach { seg -> if (seg.size == 1) drawCircle(src.color, app.superhealth.ui.charts.ChartStyle.dot.toPx(), seg[0]) else path.smooth(seg) }
                drawPath(path, src.color, style = Stroke(app.superhealth.ui.charts.ChartStyle.line.toPx(), cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawCircle(src.color, app.superhealth.ui.charts.ChartStyle.endDot.toPx(), segs.last().last())
            }
        }
    }
}
