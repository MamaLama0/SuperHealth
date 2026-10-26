package app.superhealth.ui.health

import app.superhealth.platform.*

import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import app.superhealth.ui.coach.askCoach
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.superhealth.ui.charts.LineChart
import app.superhealth.ui.charts.Series
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.WorkoutDetail
import app.superhealth.health.data.WorkoutDetails
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.Lang
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.platform.Instant
import app.superhealth.platform.ZoneId
import app.superhealth.platform.DateTimeFormatter
import kotlin.math.roundToInt

// ---- Formats ----------------------------------------------------------------------------------------

/** « 5:32 » (min:s par km). */
fun pace(secPerKm: Double): String {
    val s = secPerKm.roundToInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** « 1:02:15 » ou « 42:07 ». */
fun clock(sec: Double): String {
    val s = sec.roundToInt()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun km(m: Double): String = formatNumber(m / 1000, if (m >= 100_000) 1 else 2)

// ---- Calculs sur le tracé -------------------------------------------------------------------------

/** Vitesse lissée (m/s) à chaque point : celle de la montre, sinon distance parcourue sur ~20 s. */
private fun speeds(w: WorkoutDetail): List<Double?> {
    val p = w.points
    return p.indices.map { i ->
        p[i].speed ?: run {
            var j = i
            while (j > 0 && p[i].t - p[j].t < 20) j--
            val dt = p[i].t - p[j].t
            if (dt > 3) (p[i].d - p[j].d) / dt else null
        }
    }
}

/** Splits au kilomètre : (n° km, distance du split, secondes, FC moyenne, dénivelé +). */
private data class Split(val n: Int, val m: Double, val sec: Double, val hr: Int?, val gain: Double)

private fun kmSplits(w: WorkoutDetail): List<Split> {
    val p = w.points.filter { it.d > 0 }
    if (p.size < 2 || (w.distanceM ?: 0.0) < 500) return emptyList()
    val out = mutableListOf<Split>()
    var startI = 0
    var next = 1000.0
    for (i in 1 until p.size) {
        if (p[i].d >= next || i == p.lastIndex) {
            val seg = p.subList(startI, i + 1)
            val m = p[i].d - p[startI].d
            if (m > 50) out += Split(out.size + 1, m, p[i].t - p[startI].t,
                seg.mapNotNull { it.hr }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                seg.zipWithNext().sumOf { (a, b) -> if (a.alt != null && b.alt != null && b.alt > a.alt) b.alt - a.alt else 0.0 })
            startI = i
            next += 1000.0
        }
    }
    return out
}

/** Libellé de l'effet d'entraînement (échelle 0–5 de Garmin). */
private fun teLabel(v: Double) = when {
    v < 1 -> "Aucun"; v < 2 -> "Mineur"; v < 3 -> "Entretien"; v < 4 -> "Amélioration"; v < 5 -> "Forte amélioration"; else -> "Excessif"
}

// ---- Écran --------------------------------------------------------------------------------------------

@Composable
fun WorkoutScreen(nav: NavHostController, id: Long) {
    val ctx = LocalContext.current
    val w by produceState<WorkoutDetail?>(null, id) { value = runCatching { WorkoutDetails.load(ctx, id) }.getOrNull() }
    val detail = w
    val kind = detail?.kind?.name?.let { Bridge.kindOf(it) } ?: Bridge.Kind.OTHER
    val hue = kind.hue()
    var confirmDelete by remember { mutableStateOf(false) }
    DetailScaffold(tr(detail?.name ?: "Activité"), onBack = { nav.popBackStack() }, action = {
        // module Coach : analyse de la séance comparée aux précédentes (comme l'Athlete Intelligence de Strava)
        if (detail != null && app.superhealth.ui.coach.coachAvailable()) IconButton(onClick = {
            val facts = listOfNotNull(detail.name, Instant.ofEpochMilli(detail.start).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                detail.distanceM?.let { "${km(it)} km" }, "${(detail.activeSec / 60).toInt()} min", detail.avgHr?.let { "FC moy. $it" },
                detail.maxHr?.let { "FC max $it" }, detail.ascentM?.let { "D+ ${it.toInt()} m" }, detail.aerobicTe?.let { "TE aérobie $it" }).joinToString(", ")
            val p = app.superhealth.coach.Coach.prompts()?.workout?.takeIf { it.isNotBlank() }
            nav.askCoach(p?.replace("{details}", facts) ?: app.superhealth.ui.trf("Analyse cette séance : %1\$s", facts))
        }) { Icon(Icons.Rounded.AutoAwesome, tr("Analyse du coach"), tint = app.superhealth.ui.coach.CoachHue) }
        // séance importée (fichier GPX, TCX, FIT) : peut être retirée
        if (detail != null && id < 0) IconButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Rounded.Delete, tr("Supprimer"), tint = Mf.colors.textSecondary)
        }
    }) {
        if (confirmDelete) androidx.compose.material3.AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = Mf.colors.card,
            title = { Text(tr("Supprimer cette activité importée ?")) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                confirmDelete = false; app.superhealth.bridge.RunImport.delete(id); nav.popBackStack()
            }) { Text(tr("Supprimer"), color = app.superhealth.ui.theme.Base.Red) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text(tr("Annuler"), color = Mf.colors.text) } })
        if (detail == null) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = hue) }
            return@DetailScaffold
        }
        // En-tête : type, date, montre.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(hue.container()), contentAlignment = Alignment.Center) {
                Icon(kind.icon(), null, tint = hue)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                val date = Instant.ofEpochMilli(detail.start).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Lang.locale)).replaceFirstChar { it.uppercase(Lang.locale) }
                Text(date + " · " + timeOf(detail.start), style = MaterialTheme.typography.titleSmall)
                Text(detail.deviceName, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))

        val track = remember(detail) { detail.points.mapNotNull { p -> if (p.lat != null && p.lon != null) p.lat to p.lon else null } }
        if (track.size >= 2) {
            TrackMap(track, hue, Modifier.fillMaxWidth().height(230.dp)) { nav.navigate("h/workout/${detail.id}/map") }
            Spacer(Modifier.height(12.dp))
        }

        // Chiffres clés (comme l'en-tête d'une activité Garmin Connect).
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius))
            .background(Brush.verticalGradient(listOf(hue.container(1.35f), hue.container(0.8f)))).padding(16.dp)) {
            Row {
                detail.distanceM?.let { Big(km(it), "km", tr("Distance"), hue, Modifier.weight(1f)) }
                Big(clock(detail.activeSec), null, tr("Durée"), hue, Modifier.weight(1f))
                detail.avgSpeedMps?.let { v ->
                    if (detail.usesPace) Big(pace(1000 / v), "/km", tr("Allure moy."), hue, Modifier.weight(1f))
                    else Big(formatNumber(v * 3.6, 1), "km/h", tr("Vitesse moy."), hue, Modifier.weight(1f))
                }
            }
            val small = listOfNotNull(
                detail.avgHr?.let { "$it" to tr("FC moy.") },
                detail.maxHr?.let { "$it" to tr("FC max.") },
                detail.kcal?.let { formatNumber(it) to tr("Calories") },
                detail.ascentM?.let { "${it.roundToInt()} m" to tr("Dénivelé +") },
                detail.descentM?.let { "${it.roundToInt()} m" to tr("Dénivelé −") },
                detail.avgCadence?.let { "${it.roundToInt()}" to tr(if (detail.usesPace) "Cadence (pas/min)" else "Cadence") },
                detail.maxSpeedMps?.let { v -> if (detail.usesPace) pace(1000 / v) to tr("Allure max.") else formatNumber(v * 3.6, 1) to tr("Vitesse max.") },
                detail.steps?.let { formatNumber(it.toDouble()) to tr("Pas") },
                clock((detail.end - detail.start) / 1000.0) to tr("Temps total"),
            )
            if (small.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                small.chunked(3).forEach { row ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        row.forEach { (v, l) -> Small(v, l, Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (detail.aerobicTe != null || detail.anaerobicTe != null || detail.recoverySec != null) {
            SectionLabel(tr("Effet d'entraînement"))
            MfCard {
                detail.aerobicTe?.let { TeBar(tr("Aérobie"), it, HealthHues.Body) }
                detail.anaerobicTe?.let { Spacer(Modifier.height(12.dp)); TeBar(tr("Anaérobie"), it, HealthHues.Fitness) }
                detail.recoverySec?.let {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Temps de récupération"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${(it / 3600).roundToInt()} h", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        detail.effort?.let { e ->
            SectionLabel(tr("Effort"))
            MfCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatNumber(e, if (e % 1.0 == 0.0) 0 else 1), style = MaterialTheme.typography.headlineMedium, color = hue.onContainer())
                    Text(" / 10", style = MaterialTheme.typography.titleSmall, color = Mf.colors.textSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(tr(when { e < 4 -> "Facile"; e < 7 -> "Modéré"; e < 9 -> "Difficile"; else -> "Maximal" }), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                app.superhealth.nutrition.ui.components.ProgressBar((e / 10).toFloat(), hue, height = 8.dp)
                if (detail.effortEstimated) Text(tr("Estimé par la montre"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }

        if (detail.dynamics.isNotEmpty()) {
            SectionLabel(tr(if (detail.usesPace) "Dynamique de course" else "Mesures de la montre"))
            MfCard {
                detail.dynamics.chunked(3).forEachIndexed { i, row ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Row { row.forEach { (l, v) -> Small(v, tr(l), Modifier.weight(1f)) }; repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } }
                }
            }
        }

        if (detail.points.size >= 10) Charts(detail)
        Zones(detail)

        val splits = remember(detail) { kmSplits(detail) }
        if (splits.size >= 2 && !detail.isSwim) {
            SectionLabel(tr("Splits"))
            MfCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp, horizontal = 16.dp)) {
                Row(Modifier.padding(bottom = 6.dp)) {
                    Head(tr("Km"), Modifier.width(36.dp)); Head(if (detail.usesPace) tr("Allure") else tr("Vitesse"), Modifier.weight(1f))
                    Head(tr("FC"), Modifier.width(44.dp)); Head(tr("D+"), Modifier.width(44.dp))
                }
                val fastest = splits.minOf { it.sec / it.m }; val slowest = splits.maxOf { it.sec / it.m }
                splits.forEach { s ->
                    val perKm = s.sec / s.m * 1000
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (s.m < 950) formatNumber(s.m / 1000, 2) else "${s.n}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(36.dp))
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            // Barre : plus longue = plus rapide (comme Garmin Connect).
                            val f = if (slowest > fastest) (1 - ((s.sec / s.m) - fastest) / (slowest - fastest)) * 0.6 + 0.4 else 1.0
                            Box(Modifier.weight(f.toFloat()).height(18.dp).clip(RoundedCornerShape(6.dp)).background(hue.container(1.6f)),
                                contentAlignment = Alignment.CenterStart) {
                                Text(if (detail.usesPace) pace(perKm) else formatNumber(3600 / perKm, 1), style = MaterialTheme.typography.labelMedium,
                                    color = hue.onContainer(), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                            }
                            if (f < 1) Spacer(Modifier.weight((1 - f).toFloat()))
                        }
                        Text(s.hr?.toString() ?: "—", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                        Text("${s.gain.roundToInt()}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }

        // Tours : les micro-segments (pauses automatiques) sont écartés.
        val laps = detail.laps.filter { it.distanceM >= 100 || it.sec >= 60 }
        if (laps.size >= 2) {
            SectionLabel(tr("Tours"))
            MfCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp, horizontal = 16.dp)) {
                Row(Modifier.padding(bottom = 6.dp)) {
                    Head("#", Modifier.width(28.dp)); Head(tr("Distance"), Modifier.weight(1f)); Head(tr("Temps"), Modifier.weight(1f))
                    Head(if (detail.usesPace) tr("Allure") else tr("Vitesse"), Modifier.weight(1f)); Head(tr("FC"), Modifier.width(40.dp))
                }
                laps.forEach { l ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Cell("${l.index}", Modifier.width(28.dp)); Cell(km(l.distanceM) + " km", Modifier.weight(1f)); Cell(clock(l.sec), Modifier.weight(1f))
                        Cell(if (l.sec > 0 && l.distanceM > 0) (if (detail.usesPace) pace(l.sec / l.distanceM * 1000) else formatNumber(l.distanceM / l.sec * 3.6, 1)) else "—", Modifier.weight(1f))
                        Cell(l.avgHr?.toString() ?: "—", Modifier.width(40.dp))
                    }
                }
            }
        }

        if (detail.isSwim && (detail.poolLengthM != null || detail.swolf != null || detail.strokes != null)) {
            SectionLabel(tr("Natation"))
            MfCard {
                Row {
                    detail.poolLengthM?.let { Small("${it.roundToInt()} m", tr("Bassin"), Modifier.weight(1f)) }
                    detail.poolLengthM?.let { pl -> detail.distanceM?.let { Small("${(it / pl).roundToInt()}", tr("Longueurs"), Modifier.weight(1f)) } }
                    detail.swolf?.let { Small("${it.roundToInt()}", "SWOLF", Modifier.weight(1f)) }
                    detail.strokes?.let { Small("$it", tr("Mouvements"), Modifier.weight(1f)) }
                }
            }
        }

        if (detail.predictions.isNotEmpty()) {
            SectionLabel(tr("Prédictions de course"))
            MfCard {
                Row { detail.predictions.forEach { (l, s) -> Small(clock(s), tr(l), Modifier.weight(1f)) } }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Carte du tracé en plein écran, déplaçable et zoomable. */
@Composable
fun WorkoutMapScreen(nav: NavHostController, id: Long) {
    val ctx = LocalContext.current
    val w by produceState<WorkoutDetail?>(null, id) { value = runCatching { WorkoutDetails.load(ctx, id) }.getOrNull() }
    Box(Modifier.fillMaxSize().background(Mf.colors.background)) {
        w?.let { d ->
            val track = remember(d) { d.points.mapNotNull { p -> if (p.lat != null && p.lon != null) p.lat to p.lon else null } }
            TrackMap(track, Bridge.kindOf(d.kind.name).hue(), Modifier.fillMaxSize(), interactive = true)
        }
        Box(Modifier.statusBarsPadding().padding(12.dp).size(44.dp).clip(CircleShape).background(Mf.colors.card), contentAlignment = Alignment.Center) {
            androidx.compose.material3.IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour")) }
        }
    }
}

@Composable
private fun Big(value: String, unit: String?, label: String, hue: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = hue.onContainer(), maxLines = 1)
            unit?.let { Text(" $it", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Small(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Head(text: String, modifier: Modifier) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = modifier, maxLines = 1)

@Composable
private fun Cell(text: String, modifier: Modifier) =
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier, maxLines = 1)

@Composable
private fun TeBar(label: String, v: Double, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(formatNumber(v, 1), style = MaterialTheme.typography.titleLarge, color = color)
        Text("  " + tr(teLabel(v)), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until 5).forEach { i ->
            val f = (v - i).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(color.container(1.5f))) {
                if (f > 0) Box(Modifier.fillMaxWidth(f).height(8.dp).clip(CircleShape).background(color))
            }
        }
    }
}

private enum class ChartKind(val label: String) { PACE("Allure"), SPEED("Vitesse"), HR("Fréq. cardiaque"), ALT("Altitude"), CADENCE("Cadence") }

@Composable
private fun Charts(w: WorkoutDetail) {
    val sp = remember(w) { speeds(w) }
    val kinds = remember(w) {
        listOfNotNull(
            if (sp.count { it != null } > 10) (if (w.usesPace) ChartKind.PACE else ChartKind.SPEED) else null,
            ChartKind.HR.takeIf { w.points.count { it.hr != null } > 10 },
            ChartKind.ALT.takeIf { w.points.count { it.alt != null } > 10 },
            ChartKind.CADENCE.takeIf { w.points.count { it.cadence != null } > 10 },
        )
    }
    if (kinds.isEmpty()) return
    var sel by remember(kinds) { mutableStateOf(kinds.first()) }
    SectionLabel(tr("Graphiques"))
    MfCard {
        ChipChoice(kinds, sel, { tr(it.label) }) { sel = it }
        Spacer(Modifier.height(12.dp))
        // Abscisse : distance quand elle est connue (comme Garmin Connect), sinon temps.
        val byDistance = (w.distanceM ?: 0.0) > 500
        val pts = w.points.indices.mapNotNull { i ->
            val p = w.points[i]
            val y = when (sel) {
                ChartKind.PACE -> sp[i]?.takeIf { it > 0.5 }?.let { 1000 / it }?.takeIf { it < 20 * 60 }
                ChartKind.SPEED -> sp[i]?.let { it * 3.6 }
                ChartKind.HR -> p.hr?.toDouble()
                ChartKind.ALT -> p.alt
                ChartKind.CADENCE -> p.cadence?.toDouble()
            }
            y?.let { (if (byDistance) p.d / 1000 else p.t / 60) to it }
        }.let { l -> if (l.size > 400) l.filterIndexed { i, _ -> i % (l.size / 400 + 1) == 0 } else l }
        val color = when (sel) {
            ChartKind.PACE, ChartKind.SPEED -> HealthHues.Body; ChartKind.HR -> HealthHues.Heart
            ChartKind.ALT -> HealthHues.Activity; ChartKind.CADENCE -> HealthHues.Fitness
        }
        val fmt: (Double) -> String = if (sel == ChartKind.PACE) { v -> pace(v) } else { v -> "${v.roundToInt()}" }
        AreaChart(pts, color, invert = sel == ChartKind.PACE, format = fmt)
        Text(if (byDistance) tr("Distance (km)") else tr("Temps (min)"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = TextAlign.Center)
        val ys = pts.map { it.second }
        if (ys.isNotEmpty()) Row(Modifier.padding(top = 10.dp)) {
            val avg = ys.average()
            val best = if (sel == ChartKind.PACE) ys.min() else ys.max()
            Small(fmt(avg), tr("Moyenne"), Modifier.weight(1f))
            Small(fmt(best), tr(if (sel == ChartKind.PACE) "Meilleure" else "Maximum"), Modifier.weight(1f))
            if (sel == ChartKind.ALT) Small("${ys.min().roundToInt()}", tr("Minimum"), Modifier.weight(1f))
        }
    }
}

/**
 * Courbe remplie de la charte commune (allure inversée : plus rapide en haut, graduations en temps rond), moyenne en
 * pointillés, distance ou temps de départ et d'arrivée sous l'axe.
 */
@Composable
private fun AreaChart(pts: List<Pair<Double, Double>>, color: Color, invert: Boolean, format: (Double) -> String) {
    if (pts.size < 2) return
    val x0 = pts.first().first; val x1 = pts.last().first
    LineChart(listOf(Series(pts, color, area = true)), height = 170.dp, format = format, invertY = invert, timeAxis = invert,
        reference = pts.map { it.second }.average(), highlightEnd = false,
        xLabels = listOf(x0 to formatNumber(x0, 0), (x0 + x1) / 2 to formatNumber((x0 + x1) / 2, if (x1 < 10) 1 else 0),
            x1 to formatNumber(x1, if (x1 < 10) 1 else 0)))
}

/** Temps passé dans chaque zone cardiaque (bornes de la montre, sinon % de la FC max théorique). */
@Composable
private fun Zones(w: WorkoutDetail) {
    val hrPts = w.points.filter { it.hr != null }
    if (hrPts.size < 20) return
    val bounds = w.zoneBounds ?: run {
        val max = theoreticalMaxHr()
        listOf(50, 60, 70, 80, 90).map { it * max / 100 }
    }
    val time = DoubleArray(5)
    hrPts.zipWithNext().forEach { (a, b) ->
        val dt = (b.t - a.t).coerceAtMost(30.0)
        val z = bounds.indexOfLast { a.hr!! >= it }
        if (z >= 0) time[z] += dt
    }
    val total = time.sum().takeIf { it > 0 } ?: return
    SectionLabel(tr("Zones cardiaques"))
    MfCard {
        (4 downTo 0).forEach { i ->
            val z = ZONES[i]
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Z${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                Text("${bounds[i]}+", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, modifier = Modifier.width(40.dp))
                Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(Mf.colors.chip)) {
                    val f = (time[i] / total).toFloat()
                    if (f > 0) Box(Modifier.fillMaxWidth(f).height(12.dp).clip(CircleShape).background(z.color))
                }
                Text(clock(time[i]), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp), textAlign = TextAlign.End, maxLines = 1)
                Text("${(time[i] / total * 100).roundToInt()} %", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary,
                    modifier = Modifier.width(40.dp), textAlign = TextAlign.End, maxLines = 1)
            }
        }
    }
}
