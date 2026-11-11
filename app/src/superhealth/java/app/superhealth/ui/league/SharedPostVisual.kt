package app.superhealth.ui.league

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.superhealth.bridge.Bridge
import app.superhealth.league.*
import app.superhealth.nutrition.ui.num
import app.superhealth.training.domain.MuscleGroup
import app.superhealth.training.WorkoutApp
import app.superhealth.ui.health.TrackMap
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.ui.tr
import app.superhealth.ui.trf

private fun time(seconds: Double): String {
    val n = seconds.toInt().coerceAtLeast(0)
    return if (n >= 3600) "%d:%02d:%02d".format(n / 3600, n / 60 % 60, n % 60) else "%d:%02d".format(n / 60, n % 60)
}

private fun date(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", app.superhealth.ui.Lang.locale))

/** One visual language in the composer and feed: route, key figures, then optional details. */
@Composable
internal fun SharedPostVisual(record: GroupRecords.Record? = null, workout: SharedActivity.Strength? = null,
    activity: SharedActivity.Outdoor? = null) {
    when {
        record != null -> RecordVisual(record)
        workout != null -> StrengthVisual(workout)
        activity != null -> ActivityVisual(activity)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 7.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(tr(label), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecordVisual(r: GroupRecords.Record) {
    val ink = Mf.colors.textSecondary
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(trf("Record du %1\$s", date(r.achieved)), style = MaterialTheme.typography.labelSmall, color = ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GameArt(GameArtKind.TROPHY, Modifier.size(64.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("NOUVEAU RECORD"), style = MaterialTheme.typography.labelMedium, color = RankGold)
                Text(num(r.value, if (r.metric == "REPS") 0 else 1) + " " + r.unit, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(tr(GroupRecords.label(r)), style = MaterialTheme.typography.labelMedium, color = ink)
            }
        }
        r.previous?.let { old ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mf.colors.elevated).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Précédent record") + "  " + num(old, 1) + " " + r.unit, style = MaterialTheme.typography.bodySmall, color = ink)
                Text("+" + num(r.value - old, 1) + " " + r.unit + " · +" + num((r.value / old - 1) * 100, 1) + "%",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Mf.colors.accent)
            }
        }
        if (r.trail.size > 1) Trace(tr("Progression des records"), r.trail, r.unit, Mf.colors.accent)
        if (r.muscles.isNotEmpty()) Text(r.muscles.mapNotNull(MuscleGroup::of).take(4).joinToString(" · ") { tr(it.label) },
            style = MaterialTheme.typography.bodySmall, color = ink)
    }
}

@Composable
private fun StrengthVisual(w: SharedActivity.Strength) {
    val training by WorkoutApp.store.state.collectAsState()
    val muscles = remember(w) { w.exercises.flatMap { it.muscles }.distinct() }
    var expanded by rememberSaveable(w.started) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(trf("Séance du %1\$s", date(w.started)), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("MUSCULATION"), style = MaterialTheme.typography.labelMedium, color = WorkoutBlue)
                Stat("Durée", time(w.seconds.toDouble()))
                Stat("Volume soulevé", num(w.volumeKg, 0) + " kg")
            }
            if (muscles.isNotEmpty()) app.superhealth.training.ui.components.BodyMap(muscles.associateWith { 1f }, training.settings.sex,
                Modifier.weight(1f).height(170.dp), color = WorkoutBlue)
            else Icon(Icons.Rounded.FitnessCenter, null, Modifier.size(72.dp).padding(8.dp), tint = WorkoutBlue)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("Exercices", w.exerciseCount.toString(), Modifier.weight(1f))
            Stat("Séries", w.setCount.toString(), Modifier.weight(1f))
            Stat("Répétitions", w.repCount.toString(), Modifier.weight(1f))
        }
        w.exercises.take(if (expanded) 12 else 3).forEach { e ->
            HorizontalDivider(color = Mf.colors.divider, modifier = Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = Mf.colors.elevated) {
                    Text(e.sets.size.toString(), Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(e.sets.joinToString("  ·  ") { s -> listOfNotNull(s.kg?.let { num(it, 1) + " kg" }, s.reps?.let { "$it " + tr("reps") }).joinToString(" × ") },
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = if (expanded) 8 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (w.exercises.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(tr(if (expanded) "Réduire" else "Voir tous les exercices")) }
        if (w.exercises.isNotEmpty() && w.exercises.size < w.exerciseCount) Text(trf("Détail de %1\$s exercices sur %2\$s", w.exercises.size, w.exerciseCount), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
    }
}

@Composable
private fun ActivityVisual(a: SharedActivity.Outdoor) {
    val route = remember(a.route) { a.route?.let(SharedActivity.Route::decode).orEmpty() }
    var map by remember { mutableStateOf(false) }
    var details by rememberSaveable(a.started) { mutableStateOf(false) }
    val hue = Mf.colors.accent
    val paceSport = a.sport in setOf("RUN", "WALK", "HIKE")
    val sport = Bridge.Kind.entries.firstOrNull { it.name == a.sport } ?: Bridge.Kind.OTHER
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(trf("Activité du %1\$s", date(a.started)), Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
        if (route.size > 1) TrackMap(route, hue, Modifier.fillMaxWidth().height(210.dp), onClick = { map = true })
        else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Mf.colors.elevated).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(when (sport) { Bridge.Kind.BIKE -> Icons.Rounded.DirectionsBike; Bridge.Kind.SWIM -> Icons.Rounded.Pool; Bridge.Kind.WALK -> Icons.Rounded.DirectionsWalk; Bridge.Kind.HIKE -> Icons.Rounded.Hiking; Bridge.Kind.RUN -> Icons.Rounded.DirectionsRun; else -> Icons.Rounded.Route },
                null, Modifier.size(42.dp), tint = hue)
            Spacer(Modifier.width(12.dp)); Text(Bridge.kindLabel(a.sport), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            a.metres?.let { Stat("Distance", num(it / 1000, 2) + " km", Modifier.weight(1f)) }
            Stat("Durée", time(a.seconds), Modifier.weight(1f))
            a.metres?.takeIf { it > 0 }?.let { metres ->
                Stat(if (paceSport) "Allure moyenne" else "Vitesse moyenne", if (paceSport) time(a.seconds * 1000 / metres) + "/km" else num(metres / a.seconds * 3.6, 1) + " km/h", Modifier.weight(1f))
            }
        }
        if (a.ascent != null || a.kcal != null || a.avgHr != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            a.ascent?.let { Stat("Dénivelé positif", num(it, 0) + " m", Modifier.weight(1f)) }
            a.avgHr?.let { Stat("FC moyenne", "$it bpm", Modifier.weight(1f)) }
            a.kcal?.let { Stat("Énergie", num(it, 0) + " kcal", Modifier.weight(1f)) }
        }
        val hasDetails = a.heart.isNotEmpty() || a.speed.isNotEmpty() || a.elevation.isNotEmpty() || a.splits.isNotEmpty() || a.maxHr != null || a.cadence != null
        if (hasDetails) TextButton(onClick = { details = !details }) { Text(tr(if (details) "Réduire" else "Détails de l’activité")) }
        if (details) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                a.maxHr?.let { Stat("FC maximale", "$it bpm", Modifier.weight(1f)) }
                a.cadence?.let { Stat("Cadence", num(it, 0) + "/min", Modifier.weight(1f)) }
            }
            if (a.speed.size > 1) Trace(tr("Vitesse"), a.speed.map { it * 3.6 }, "km/h", hue)
            if (a.elevation.size > 1) Trace(tr("Altitude"), a.elevation, "m", hue)
            if (a.heart.size > 1) Trace(tr("Fréquence cardiaque"), a.heart.map { it.toDouble() }, "bpm", Color(0xFFE2606E))
            if (a.splits.isNotEmpty()) {
                Text(tr("Tours et intervalles"), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall)
                a.splits.forEachIndexed { i, split ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text((i + 1).toString(), style = MaterialTheme.typography.labelMedium)
                        Text(num(split.metres / 1000, 2) + " km", style = MaterialTheme.typography.bodySmall)
                        Text(time(split.seconds), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (map) Dialog(onDismissRequest = { map = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Mf.colors.background)) {
            TrackMap(route, hue, Modifier.fillMaxSize(), interactive = true)
            IconButton(onClick = { map = false }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).background(Mf.colors.card, RoundedCornerShape(50))) {
                Icon(Icons.Rounded.Close, tr("Fermer"))
            }
        }
    }
}

@Composable
private fun Trace(label: String, values: List<Double>, unit: String, hue: Color) {
    val clean = values.filter { it.isFinite() }
    if (clean.size < 2) return
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(num(clean.min(), 0) + " – " + num(clean.max(), 0) + " " + unit, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
        }
        val base = Mf.colors.elevated
        Canvas(Modifier.fillMaxWidth().padding(top = 8.dp).height(78.dp)) {
            val lo = clean.min(); val span = (clean.max() - lo).coerceAtLeast(1.0)
            val path = Path()
            clean.forEachIndexed { i, value ->
                val x = i.toFloat() / (clean.size - 1) * size.width
                val y = size.height * (.88f - .76f * ((value - lo) / span).toFloat())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawRect(base, alpha = .3f)
            drawPath(fill, Brush.verticalGradient(listOf(hue.copy(alpha = .18f), Color.Transparent)))
            drawPath(path, hue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
