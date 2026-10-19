package app.superhealth.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.health.data.Performance
import app.superhealth.nutrition.ui.components.BarChart
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.Ring
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.formatNumber
import app.superhealth.ui.localDate
import app.superhealth.ui.relativeDay
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlin.math.roundToInt

/** Charge aiguë/chronique : libellé et couleur, comme le « statut d'entraînement » de Garmin Connect. */
fun loadStatus(acwr: Double): Pair<String, Color> = when {
    acwr < 0.8 -> "Récupération" to HealthHues.Body
    acwr <= 1.3 -> "Optimale" to HealthHues.Activity
    acwr <= 1.5 -> "Élevée" to HealthHues.Mind
    else -> "Très élevée" to HealthHues.Heart
}

/** Récupération restante (h) sur l'anneau : 72 h = anneau plein. */
@Composable
fun RecoveryRing(hours: Double?, size: androidx.compose.ui.unit.Dp, stroke: androidx.compose.ui.unit.Dp) {
    val hue = HealthHues.Sleep
    Ring(((hours ?: 0.0) / 72).toFloat().coerceIn(0f, 1f), hue, size, stroke = stroke, track = Mf.colors.card.copy(alpha = 0.6f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (hours == null) Text(tr("Prêt"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = HealthHues.Activity)
            else {
                Text("${hours.roundToInt()} h", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = hue.onContainer())
                Text(tr("restantes"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
            }
        }
    }
}

/** Performances : récupération, charge, volume, records personnels et prédictions de course. */
@Composable
fun PerformanceScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val snap by produceState<Performance.Snapshot?>(null, refresh) { value = runCatching { Performance.snapshot(ctx) }.getOrNull() }
    DetailScaffold(tr("Performances"), onBack = { nav.popBackStack() }) {
        val s = snap ?: run {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = HealthHues.Fitness)
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Analyse de vos séances…"), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            return@DetailScaffold
        }
        if (s.entries.isEmpty()) {
            MfCard { Text(tr("Synchronisez ou importez des activités de votre montre pour voir vos performances."), color = Mf.colors.textSecondary) }
            return@DetailScaffold
        }

        // Récupération et charge, côte à côte.
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tinted(HealthHues.Sleep, Modifier.weight(1f).fillMaxHeight()) {
                Text(tr("Récupération"), style = MaterialTheme.typography.titleSmall, color = HealthHues.Sleep.onContainer())
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { RecoveryRing(s.recoveryLeftH, 104.dp, 10.dp) }
                Spacer(Modifier.height(8.dp))
                Text(if (s.recoveryLeftH == null) tr("Prêt pour une séance intense") else tr("Privilégiez une séance facile"),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
            val (label, color) = s.acwr?.let(::loadStatus) ?: ("—" to Mf.colors.textSecondary)
            Tinted(color.takeIf { s.acwr != null } ?: HealthHues.Body, Modifier.weight(1f).fillMaxHeight()) {
                Text(tr("Charge"), style = MaterialTheme.typography.titleSmall, color = color.onContainer())
                Spacer(Modifier.height(10.dp))
                if (s.acwr == null) Text(tr("Aucune séance ces 4 dernières semaines"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
                else Text(tr(label), style = MaterialTheme.typography.headlineSmall, color = color.onContainer())
                s.acwr?.let { Text(trf("Ratio 7 j / 28 j : %1\$s", formatNumber(it, 2)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
                Spacer(Modifier.height(10.dp))
                // Jauge : récupération · optimale · élevée · très élevée.
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(HealthHues.Body to 0.8, HealthHues.Activity to 1.3, HealthHues.Mind to 1.5, HealthHues.Heart to 9.0).forEachIndexed { i, (c, max) ->
                        val active = s.acwr != null && s.acwr <= max && (i == 0 || s.acwr > listOf(0.0, 0.8, 1.3, 1.5)[i])
                        Box(Modifier.weight(1f).height(if (active) 10.dp else 6.dp).clip(CircleShape).background(if (active) c else c.copy(alpha = 0.3f)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(tr("Minutes d'effort pondérées par l'effet d'entraînement"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
            }
        }

        SectionLabel(tr("Course à pied par semaine"))
        MfCard {
            val cur = s.weeks.last()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatNumber(cur.runKm, 1), style = MaterialTheme.typography.headlineMedium, color = HealthHues.Fitness)
                Text(" km " + tr("cette semaine"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
            }
            val avg = s.weeks.dropLast(1).takeLast(4).map { it.runKm }.average()
            if (!avg.isNaN()) Text(trf("Moyenne des 4 semaines précédentes : %1\$s km", formatNumber(avg, 1)), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            BarChart(s.weeks.map { it.runKm.takeIf { k -> k > 0 } }, s.weeks.map { null }, HealthHues.Fitness, height = 120.dp,
                labels = s.weeks.mapIndexed { i, w -> if (i % 3 == 0 || i == s.weeks.lastIndex) "${w.start.dayOfMonth}/${w.start.monthValue}" else "" })
        }

        SectionLabel(tr("Records personnels"))
        MfCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            if (s.records.isEmpty() && s.longestRun == null) Text(tr("Vos records apparaîtront après vos premières courses avec GPS."),
                color = Mf.colors.textSecondary, modifier = Modifier.padding(16.dp))
            s.records.forEach { r ->
                RecordRow(tr(r.label), clock(r.sec), trf("%1\$s /km", pace(r.sec / (Performance.RECORD_DISTANCES.first { it.second == r.label }.first / 1000))),
                    relativeDay(localDate(r.date))) { nav.navigate("h/workout/${r.workoutId}") }
            }
            s.longestRun?.let { e ->
                RecordRow(tr("Plus longue course"), km(e.distanceM) + " km", clock(e.activeSec), relativeDay(localDate(e.start))) { nav.navigate("h/workout/${e.id}") }
            }
        }

        if (s.predictions.isNotEmpty()) {
            SectionLabel(tr("Prédictions de course"))
            MfCard {
                Row { s.predictions.forEach { (l, sec) ->
                    Column(Modifier.weight(1f)) {
                        Text(clock(sec), style = MaterialTheme.typography.titleMedium)
                        Text(tr(l), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                    }
                } }
                Text(tr("Estimées par la montre d'après votre VO₂ max et vos dernières courses."), style = MaterialTheme.typography.labelSmall,
                    color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Tinted(hue: Color, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(CardRadius)).background(Brush.verticalGradient(listOf(hue.container(1.35f), hue.container(0.8f)))).padding(14.dp),
        content = content)
}

@Composable
private fun RecordRow(title: String, value: String, sub: String, date: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(HealthHues.Mind.container()), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(20.dp), tint = HealthHues.Mind)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text("$sub · $date", style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
    }
}
