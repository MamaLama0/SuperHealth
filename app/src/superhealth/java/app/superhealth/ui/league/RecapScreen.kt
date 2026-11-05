package app.superhealth.ui.league

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.bridge.Bridge
import app.superhealth.league.League
import app.superhealth.league.WeeklyRecap
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.go
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Hues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Bilan de la semaine : une page à feuilleter, chiffres animés, graphiques, partage au Groupe sous forme d'image. */
@Composable
fun RecapScreen(nav: NavHostController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val data by produceState<WeeklyRecap.Data?>(null) { value = runCatching { WeeklyRecap.compute(ctx) }.getOrNull() }
    LaunchedEffect(data?.weekStart) { data?.let { d -> app.superhealth.SuperPrefs.update { it.copy(recapSeenWeek = maxOf(it.recapSeenWeek, d.weekStart)) } } }
    var busy by remember { mutableStateOf(false) }
    val accent = Mf.colors.accent
    DetailScaffold(tr("Ma semaine"), onBack = { nav.popBackStack() }, contentSpacing = 14.dp) {
        val d = data ?: run { LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent); return@DetailScaffold }
        if (d.empty) { EmptyRecap(); return@DetailScaffold }
        Hero(d, accent)
        if (d.sports.isNotEmpty() || d.mindMinutes > 0) Activities(d)
        if (d.volumes.any { it > 0 }) Volume(d, accent)
        if (d.lifts.isNotEmpty()) Lifts(d, accent)
        Wellbeing(d)
        if (League.available()) ShareButton(busy, accent) {
            busy = true
            scope.launch {
                runCatching {
                    val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { WeeklyRecap.render(ctx, d, accent.toArgb()) }
                    app.superhealth.league.SocialPosts.draft.value = null
                    app.superhealth.league.GroupDraft.save(ctx, app.superhealth.league.GroupDraft.Draft(WeeklyRecap.summary(d),
                        uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file).toString()))
                    nav.go("social/compose")
                }
                busy = false
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Phrase d'accroche : le fait le plus marquant de la semaine. */
private fun punchline(d: WeeklyRecap.Data): String {
    val best = d.lifts.firstOrNull { (it.change ?: 0.0) > 0.02 }
    val minutes = d.sports.sumOf { it.minutes } + d.mindMinutes
    return when {
        d.volumeKg > 0 && d.volumes.count { it > 0 } >= 3 && d.volumeKg >= d.volumes.max() -> tr("Plus gros volume des six dernières semaines.")
        best != null -> trf("Nouveau cap sur %1\$s.", tr(best.name))
        d.mirror -> tr("Défi miroir relevé. Respect.")
        minutes >= 150 -> trf("%1\$s d'activité au compteur.", WeeklyRecap.minutes(minutes))
        d.questsDone > 0 && d.questsDone == d.quests -> tr("Toutes les quêtes bouclées.")
        else -> tr("Une semaine de plus dans les jambes.")
    }
}

@Composable
private fun Hero(d: WeeklyRecap.Data, accent: Color) {
    val fmt = DateTimeFormatter.ofPattern("d MMM", app.superhealth.ui.Lang.locale)
    val start = LocalDate.ofEpochDay(d.weekStart)
    val deep = lerp(accent, Color(0xFF14101F), 0.55f)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(accent, lerp(accent, Hues.pack.purple, 0.5f), deep)))
        .padding(22.dp)) {
        Text((start.format(fmt) + " – " + start.plusDays(6).format(fmt)).uppercase(app.superhealth.ui.Lang.locale),
            style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .8f))
        Text(tr("Votre semaine"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 4.dp))
        Text(punchline(d), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .92f), modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            d.headline().forEachIndexed { i, stat ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 12.dp)) {
                    CountUp(stat.value, i)
                    Text(stat.label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        d.heaviest?.let { (name, kg) ->
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FitnessCenter, null, Modifier.size(16.dp), tint = Color.White)
                Text(trf("Charge la plus lourde : %1\$s kg au %2\$s", "%.1f".format(app.superhealth.ui.Lang.locale, kg), tr(name)),
                    style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Nombre qui monte de 0 à sa valeur à l'ouverture ; les valeurs non numériques s'affichent directement. */
@Composable
private fun CountUp(value: String, index: Int) {
    val locale = app.superhealth.ui.Lang.locale
    val digits = value.filter { it.isDigit() || it == ',' || it == '.' }
    val target = digits.replace(" ", "").replace(" ", "").let { raw ->
        if (value.contains("h") || value.contains("min")) null else raw.replace(',', '.').toDoubleOrNull()?.takeIf { raw.count { it == '.' || it == ',' } <= 1 && !value.contains(' ') }
    }
    val anim = remember(value) { Animatable(0f) }
    LaunchedEffect(value) { if (target != null) anim.animateTo(1f, tween(900, delayMillis = 120 * index, easing = FastOutSlowInEasing)) }
    val shown = if (target == null) value else {
        val v = target * anim.value
        if (value.contains(',') || value.contains('.')) "%.1f".format(locale, v) else "%,d".format(locale, v.roundToInt())
    }
    Text(shown, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
}

@Composable
private fun Section(title: String, icon: ImageVector, hue: Color) {
    Row(Modifier.padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(hue.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(15.dp), tint = hue)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
    }
}

private fun sportLook(kind: Bridge.Kind): Pair<ImageVector, Color> = when (kind) {
    Bridge.Kind.RUN -> Icons.AutoMirrored.Rounded.DirectionsRun to HealthHues.Activity
    Bridge.Kind.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk to Hues.pack.green
    Bridge.Kind.HIKE -> Icons.Rounded.Hiking to Hues.pack.teal
    Bridge.Kind.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike to Hues.pack.yellow
    Bridge.Kind.SWIM -> Icons.Rounded.Pool to Hues.pack.cyan
    Bridge.Kind.YOGA -> Icons.Rounded.SelfImprovement to HealthHues.Mind
    else -> Icons.Rounded.Bolt to Hues.pack.orange
}

@Composable
private fun Activities(d: WeeklyRecap.Data) {
    val locale = app.superhealth.ui.Lang.locale
    Section(tr("Activités"), Icons.Rounded.Bolt, HealthHues.Activity)
    val rows = d.sports.map { s ->
        val (icon, hue) = sportLook(s.kind)
        val detail = listOfNotNull(trf(if (s.count > 1) "%1\$s sorties" else "%1\$s sortie", s.count),
            s.km.takeIf { it >= 0.1 }?.let { "%.1f km".format(locale, it) }, WeeklyRecap.minutes(s.minutes)).joinToString(" · ")
        Triple(Triple(icon, hue, tr(s.label)), detail, s.minutes)
    } + if (d.mindMinutes > 0) listOf(Triple(Triple(Icons.Rounded.SelfImprovement, HealthHues.Mind, tr("Esprit et mobilité")), WeeklyRecap.minutes(d.mindMinutes.toLong()), d.mindMinutes.toLong())) else emptyList()
    val max = rows.maxOf { it.third }.coerceAtLeast(1)
    MfCard {
        rows.forEachIndexed { i, (look, detail, minutes) ->
            val (icon, hue, label) = look
            if (i > 0) Spacer(Modifier.height(14.dp))
            val grow = remember { Animatable(0f) }
            LaunchedEffect(Unit) { grow.animateTo(1f, tween(800, delayMillis = 100 * i, easing = FastOutSlowInEasing)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(hue.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp), tint = hue)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Mf.colors.divider.copy(alpha = .5f))) {
                        Box(Modifier.fillMaxWidth(grow.value * minutes / max.toFloat()).fillMaxHeight().clip(CircleShape).background(hue))
                    }
                }
            }
        }
    }
}

@Composable
private fun Volume(d: WeeklyRecap.Data, accent: Color) {
    val locale = app.superhealth.ui.Lang.locale
    Section(tr("Volume soulevé"), Icons.Rounded.FitnessCenter, HealthHues.Fitness)
    MfCard {
        val previous = d.volumes.dropLast(1).filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%,d".format(locale, d.volumeKg.roundToInt()), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(" kg", style = MaterialTheme.typography.titleMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            previous?.takeIf { it > 0 && d.volumeKg > 0 }?.let { p -> DeltaPill(d.volumeKg / p - 1, accent, tr("vs moyenne")) }
        }
        val max = d.volumes.max().coerceAtLeast(1.0)
        val muted = Mf.colors.divider
        val grow = remember { Animatable(0f) }
        LaunchedEffect(Unit) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        Canvas(Modifier.fillMaxWidth().height(130.dp).padding(top = 14.dp)) {
            val slot = size.width / d.volumes.size
            d.volumes.forEachIndexed { i, v ->
                val h = (size.height * (v / max) * grow.value).toFloat().coerceAtLeast(6f)
                val current = i == d.volumes.lastIndex
                drawRoundRect(if (current) Brush.verticalGradient(listOf(accent, lerp(accent, Hues.pack.purple, .6f))) else Brush.verticalGradient(listOf(muted, muted)),
                    Offset(i * slot + slot * 0.14f, size.height - h), Size(slot * 0.72f, h), CornerRadius(14f, 14f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            d.volumes.indices.forEach { i ->
                Text(if (i == d.volumes.lastIndex) tr("Cette sem.") else "S-${d.volumes.lastIndex - i}", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = if (i == d.volumes.lastIndex) accent else Mf.colors.textTertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DeltaPill(change: Double, accent: Color, suffix: String? = null) {
    val up = change >= 0
    val hue = if (up) Hues.pack.green else Mf.colors.textSecondary
    Row(Modifier.clip(CircleShape).background(hue.copy(alpha = .15f)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (up) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown, null, Modifier.size(14.dp), tint = hue)
        Text((if (up) "+" else "") + "%.1f %%".format(app.superhealth.ui.Lang.locale, change * 100) + (suffix?.let { " $it" } ?: ""),
            style = MaterialTheme.typography.labelMedium, color = hue, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun Lifts(d: WeeklyRecap.Data, accent: Color) {
    val locale = app.superhealth.ui.Lang.locale
    Section(tr("Progrès de force"), Icons.Rounded.TrendingUp, Hues.pack.green)
    d.lifts.forEach { lift ->
        MfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(lift.name), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(trf("1RM estimé %1\$s kg", "%.1f".format(locale, lift.best)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
                lift.change?.let { DeltaPill(it, accent) } ?: Text(tr("nouveau"), style = MaterialTheme.typography.labelMedium, color = accent)
            }
            if (lift.sessions.size >= 2) Sparkline(lift.sessions, accent, Modifier.fillMaxWidth().height(56.dp).padding(top = 10.dp))
        }
    }
}

@Composable
private fun Wellbeing(d: WeeklyRecap.Data) {
    val locale = app.superhealth.ui.Lang.locale
    val tiles = listOfNotNull(
        d.steps?.let { Triple(Icons.AutoMirrored.Rounded.DirectionsWalk to Hues.pack.green, "%,d".format(locale, it.roundToInt()), tr("pas par jour")) },
        d.sleepMin?.let { Triple(Icons.Rounded.Bedtime to HealthHues.Sleep, "%d h %02d".format((it / 60).toInt(), (it % 60).toInt()), tr("de sommeil par nuit")) },
        d.weightChange?.let { Triple(Icons.Rounded.MonitorWeight to HealthHues.Body, (if (it >= 0) "+" else "") + "%.1f kg".format(locale, it), tr("sur la semaine")) },
        d.index?.let { Triple(Icons.Rounded.Favorite to HealthHues.Heart, "$it", tr("indice de santé")) },
    )
    if (tiles.isEmpty()) return
    Section(tr("Au quotidien"), Icons.Rounded.WbSunny, Hues.pack.yellow)
    tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (look, value, label) ->
                val (icon, hue) = look
                Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(hue.copy(alpha = .12f)).padding(14.dp)) {
                    Icon(icon, null, Modifier.size(20.dp), tint = hue)
                    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}


@Composable
private fun ShareButton(busy: Boolean, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).clip(CircleShape)
        .background(Brush.horizontalGradient(listOf(accent, lerp(accent, Hues.pack.purple, .55f))))
        .clickable(enabled = !busy, onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.IosShare, null, Modifier.size(20.dp), tint = Color.White)
        Text(if (busy) tr("Préparation…") else tr("Partager au Groupe"), style = MaterialTheme.typography.titleSmall,
            color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun EmptyRecap() {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Mf.colors.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Insights, null, Modifier.size(34.dp), tint = Mf.colors.accent)
        }
        Text(tr("Pas de bilan cette semaine"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(tr("Aucune activité suivie. Le bilan revient dès votre prochaine séance, sortie ou méditation."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp))
    }
}

/** Courbe des meilleures séances, avec un dégradé sous la ligne et le dernier point marqué. */
@Composable
private fun Sparkline(values: List<Double>, color: Color, modifier: Modifier) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val lo = values.min(); val hi = values.max().coerceAtLeast(lo + 1e-6)
        val pad = 6f
        fun pt(i: Int) = Offset(i * size.width / (values.size - 1), pad + (size.height - 2 * pad) * (1 - ((values[i] - lo) / (hi - lo)).toFloat() * grow.value))
        val line = Path(); val fill = Path()
        values.indices.forEach { i -> val p = pt(i); if (i == 0) { line.moveTo(p.x, p.y); fill.moveTo(p.x, size.height); fill.lineTo(p.x, p.y) } else { line.lineTo(p.x, p.y); fill.lineTo(p.x, p.y) } }
        fill.lineTo(size.width, size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .30f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 6f, cap = StrokeCap.Round))
        drawCircle(color, 9f, pt(values.lastIndex))
        drawCircle(Color.White, 4f, pt(values.lastIndex))
    }
}
