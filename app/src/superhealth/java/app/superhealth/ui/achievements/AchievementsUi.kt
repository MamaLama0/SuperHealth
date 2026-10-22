package app.superhealth.ui.achievements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EggAlt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.achievements.AchievementData
import app.superhealth.achievements.Achievements
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.ProgressBar
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.charts.BarChart
import app.superhealth.ui.dashboard.Tile
import app.superhealth.ui.dashboard.cardClick
import app.superhealth.ui.dashboard.square
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dernier résumé lu : affiché tout de suite au retour sur le tableau de bord. */
private var lastSummary: Achievements.Summary? = null

@Composable
private fun summaryState(vm: SuperViewModel): Achievements.Summary? {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val s by produceState(lastSummary, refresh) {
        value = withContext(Dispatchers.Default) { runCatching { AchievementData.summary(ctx, force = refresh > 0 && lastSummary != null) }.getOrNull() }
            ?.also { lastSummary = it } ?: lastSummary
    }
    return s
}

internal fun Achievements.Trophy.icon(): ImageVector = when (this) {
    Achievements.Trophy.WALKER -> Icons.AutoMirrored.Rounded.DirectionsWalk
    Achievements.Trophy.STEP_GOAL -> Icons.Rounded.Flag
    Achievements.Trophy.STEP_STREAK -> Icons.Rounded.Whatshot
    Achievements.Trophy.LOG_STREAK -> Icons.Rounded.EventAvailable
    Achievements.Trophy.ON_TARGET -> Icons.Rounded.TrackChanges
    Achievements.Trophy.PROTEIN -> Icons.Rounded.EggAlt
    Achievements.Trophy.WORKOUTS -> Icons.Rounded.FitnessCenter
    Achievements.Trophy.RECORDS -> Icons.Rounded.EmojiEvents
    Achievements.Trophy.RUNNER -> Icons.AutoMirrored.Rounded.DirectionsRun
    Achievements.Trophy.SLEEPER -> Icons.Rounded.Bedtime
    Achievements.Trophy.WEIGH_IN -> Icons.Rounded.MonitorWeight
    Achievements.Trophy.WATER -> Icons.Rounded.WaterDrop
    Achievements.Trophy.TRAVELLER -> Icons.Rounded.RocketLaunch
    Achievements.Trophy.LOYAL -> Icons.Rounded.EventAvailable
    Achievements.Trophy.LEVEL -> Icons.Rounded.EmojiEvents
}

/** Distance du voyage, lisible à toutes les échelles (km, millions, milliards, années-lumière). */
internal fun journeyKm(km: Double): String = when {
    km < 100_000 -> trf("%1\$s km", formatNumber(km))
    km < 1e9 -> trf("%1\$s millions de km", formatNumber(km / 1e6, if (km < 1e7) 1 else 0))
    km < 9.461e12 -> trf("%1\$s milliards de km", formatNumber(km / 1e9, 1))
    else -> trf("%1\$s années-lumière", formatNumber(km / 9.461e12, 2))
}

/**
 * Carte Progression : petit (niveau dans son anneau), moyen (la scène et le niveau), bandeau (étape et barre), grand
 * (scène, étape, XP, derniers trophées). Toucher : l'écran des succès.
 */
@Composable
fun ProgressCard(c: DashCard, size: CardSize, vm: SuperViewModel, modifier: Modifier, nav: NavHostController) {
    val s = summaryState(vm)
    val hue = Space.Gold
    val (lvl, inLvl, need) = s?.level ?: Triple(1, 0, 100)
    val open: () -> Unit = { nav.go("achievements") }
    val box = modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(Mf.colors.card).cardClick(open)
    when (size) {
        CardSize.S -> Box(box.square().padding(10.dp), contentAlignment = Alignment.Center) {
            LevelRing(lvl, inLvl.toFloat() / need, Modifier.fillMaxSize())
        }
        CardSize.M -> Column(box.padding(10.dp)) {
            if (s != null) JourneyScene(s.journey, Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trf("Niv. %1\$s", lvl), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = hue.onContainer())
                Spacer(Modifier.width(8.dp))
                ProgressBar(inLvl.toFloat() / need, hue, Modifier.weight(1f), height = 6.dp)
            }
        }
        CardSize.W -> Row(box.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LevelRing(lvl, inLvl.toFloat() / need, Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(s?.journey?.leg?.label ?: Achievements.Leg.EARTH.label), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                ProgressBar(s?.journey?.fraction ?: 0f, hue, Modifier.fillMaxWidth(), height = 6.dp)
            }
            Spacer(Modifier.width(8.dp))
            Icon(if (s?.journey?.leg?.rocket == true) Icons.Rounded.RocketLaunch else Icons.Rounded.Public, null, tint = hue)
        }
        CardSize.L -> Tile(c, tr("Progression"), modifier, tinted = false, trailing = trf("Niv. %1\$s", lvl), onClick = open) {
            if (s == null) { Text(tr("Calcul de votre progression…"), color = Mf.colors.textSecondary); return@Tile }
            JourneyScene(s.journey, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(tr(s.journey.leg.label), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(journeyKm(s.journey.km) + " · " + "${(s.journey.fraction * 100).toInt()} %", style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
                if (s.today > 0) Text(trf("+%1\$s XP aujourd'hui", s.today), style = MaterialTheme.typography.labelLarge, color = hue.onContainer())
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(inLvl.toFloat() / need, hue, Modifier.fillMaxWidth(), height = 6.dp)
            Text(trf("%1\$s / %2\$s XP jusqu'au niveau %3\$s", inLvl, need, lvl + 1), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp))
            val won = s.trophies.filter { it.tier != null }.sortedByDescending { it.tier!!.ordinal }.take(5)
            if (won.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { won.forEach { Medal(it, 34.dp) } }
            }
        }
    }
}

/** Niveau au centre d'un anneau doré qui se remplit vers le niveau suivant. */
@Composable
private fun LevelRing(level: Int, fraction: Float, modifier: Modifier) {
    val track = Space.Gold.container()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension; val st = d * 0.1f
            val tl = Offset((size.width - d) / 2 + st / 2, (size.height - d) / 2 + st / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d - st, d - st), style = Stroke(st))
            drawArc(Space.Gold, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, Size(d - st, d - st), style = Stroke(st, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(14.dp), tint = Space.Gold)
            Text("$level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Space.Gold.onContainer())
        }
    }
}

/** Médaille : le symbole du trophée sur un disque de la couleur de son palier, ou en creux s'il n'est pas obtenu. */
@Composable
internal fun Medal(p: Achievements.Progress, size: androidx.compose.ui.unit.Dp) {
    val t = p.tier
    val col = tierColor(t)
    Box(Modifier.size(size).clip(CircleShape).background(if (t != null) col else Mf.colors.chip)
        .border(if (t == null) 1.5.dp else 0.dp, Mf.colors.divider, CircleShape), contentAlignment = Alignment.Center) {
        Icon(p.trophy.icon(), null, Modifier.size(size * 0.55f), tint = if (t != null) androidx.compose.ui.graphics.Color.White else Mf.colors.textTertiary)
    }
}

/** Écran des succès : le voyage, le niveau, l'XP des 30 derniers jours, les étapes et tous les trophées. */
@Composable
fun AchievementsScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val s = summaryState(vm)
    LaunchedEffect(s) { s?.let { AchievementData.markSeen(ctx, it.unlocked) } }
    DetailScaffold(tr("Progression"), onBack = { nav.popBackStack() }) {
        if (s == null) { Text(tr("Calcul de votre progression…"), color = Mf.colors.textSecondary); return@DetailScaffold }
        val (lvl, inLvl, need) = s.level
        JourneyScene(s.journey, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(CardRadius)))
        Spacer(Modifier.height(12.dp))
        app.superhealth.ui.adventure.AdventureTeaser(vm, nav)
        Spacer(Modifier.height(12.dp))
        MfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelRing(lvl, inLvl.toFloat() / need, Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(trf("Niveau %1\$s", lvl), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(trf("%1\$s XP au total · %2\$s jours suivis", formatNumber(s.xp.toDouble()), s.days), style = MaterialTheme.typography.bodySmall,
                        color = Mf.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            val last30 = s.xpByDay.takeLast(30)
            BarChart(last30.map { it.second.toDouble() }, emptyList(), Space.Gold, height = 70.dp)
            Text(tr("XP des 30 derniers jours"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        SectionLabel(tr("Voyage"))
        MfCard {
            Achievements.Leg.entries.forEach { l ->
                val done = l in s.journey.done
                val current = l == s.journey.leg
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) Space.Gold else if (current) Space.Gold.container() else Mf.colors.chip),
                        contentAlignment = Alignment.Center) {
                        Icon(if (done) Icons.Rounded.Check else if (l.rocket) Icons.Rounded.RocketLaunch else Icons.Rounded.Public, null, Modifier.size(16.dp),
                            tint = if (done) androidx.compose.ui.graphics.Color.White else if (current) Space.Gold else Mf.colors.textTertiary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr(l.label), style = MaterialTheme.typography.bodyLarge, color = if (done || current) Mf.colors.text else Mf.colors.textSecondary)
                        Text(trf("%1\$s XP · %2\$s", formatNumber(l.xp.toDouble()), journeyKm(l.km)), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
                    }
                    if (current) Text("${(s.journey.fraction * 100).toInt()} %", style = MaterialTheme.typography.labelLarge, color = Space.Gold.onContainer())
                }
            }
        }
        SectionLabel(tr("Trophées"))
        s.trophies.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                row.forEach { p ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Mf.colors.card).padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Medal(p, 46.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(tr(p.trophy.label), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(p.next?.let { trf("%1\$s / %2\$s", formatNumber(p.value.toDouble()), formatNumber(it.toDouble())) } ?: tr("Complet"),
                            style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        ProgressBar(p.fraction, tierColor(p.tier).takeIf { p.tier != null } ?: Space.Gold, Modifier.fillMaxWidth(), height = 4.dp)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        SectionLabel(tr("Gagner de l'XP"))
        MfCard {
            listOf("Objectif de pas atteint" to "+20 · " + tr("+10 à 150 %"), "Journée journalisée" to "+10", "Calories dans la cible (±10 %)" to "+10",
                "Protéines atteintes" to "+10", "Séance ou activité" to "+30", "Nuit complète" to "+10", "Record" to "+15", "Pesée" to "+5",
                "Hydratation atteinte" to "+5").forEach { (a, b) ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(tr(a), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(b, style = MaterialTheme.typography.bodyMedium, color = Space.Gold.onContainer(), fontWeight = FontWeight.SemiBold)
                }
            }
            Text(tr("100 XP par jour au plus."), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}
