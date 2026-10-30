package app.superhealth.ui.health

import app.superhealth.platform.*
import app.superhealth.platform.Context
import app.superhealth.platform.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.dashCardBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.bridge.Bridge
import app.superhealth.bridge.Watch
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.formatDuration
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import app.superhealth.ui.localDate
import app.superhealth.ui.relativeDay
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.layout.height
import app.superhealth.nutrition.ui.components.SettingRow
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.superhealth.ui.trf
import kotlinx.coroutines.launch
import app.superhealth.ui.tr

fun Bridge.Kind.icon(): ImageVector = when (this) {
    Bridge.Kind.STRENGTH -> Icons.Rounded.FitnessCenter
    Bridge.Kind.RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
    Bridge.Kind.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
    Bridge.Kind.HIKE -> Icons.Rounded.Hiking
    Bridge.Kind.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
    Bridge.Kind.SWIM -> Icons.Rounded.Pool
    Bridge.Kind.YOGA -> Icons.Rounded.SelfImprovement
    Bridge.Kind.OTHER -> Icons.Rounded.SportsScore
}

/** Ligne d'activité commune (musculation, montre, Health Connect), avec la FC moyenne de la montre quand elle existe. */
@Composable
fun ActivityRow(a: Bridge.Activity, horizontal: androidx.compose.ui.unit.Dp = 16.dp, chip: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val hr by produceState<Int?>(null, a.key) { value = runCatching { Bridge.averageHeartRate(a.start, a.end) }.getOrNull() }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = horizontal, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val tint = a.kind.hue()
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(chip ?: tint.container()), contentAlignment = Alignment.Center) {
            Icon(a.kind.icon(), null, Modifier.size(22.dp), tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(a.title), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(relativeDay(localDate(a.start)), a.distanceM?.takeIf { it >= 100 }?.let { km(it) + " km" },
                formatDuration(a.minutes.toDouble()), a.run?.app ?: tr(a.source.label)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        hr?.let {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Favorite, null, Modifier.size(14.dp), tint = HealthHues.Heart)
            Text(" $it", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

fun openActivity(ctx: Context, nav: NavHostController, a: Bridge.Activity) {
    when {
        a.gymId != null -> nav.navigate("workoutdetail/${a.gymId}")
        a.watch != null -> nav.navigate("h/workout/${a.watch.id}")
        a.run != null -> nav.navigate("h/workout/${a.run.id}")
        else -> Toast.makeText(ctx, app.superhealth.ui.trf("Activité enregistrée par %1\$s", a.hc?.app ?: "Apple Santé"), Toast.LENGTH_SHORT).show()
    }
}

/** Activités lues pour l'onglet : [strength] garde la musculation, [health] les séances de la montre et de Health Connect. */
private suspend fun tabActivities(fromMs: Long, strength: Boolean, health: Boolean): List<Bridge.Activity> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching { Bridge.activities(fromMs) }.getOrDefault(emptyList()).filter { a ->
        val gym = a.source == Bridge.Source.GYM || a.kind == Bridge.Kind.STRENGTH
        if (gym) strength && (a.source == Bridge.Source.GYM || health) else health
    }
}

/**
 * Semaine de l'onglet Activités, en une ligne : nombre d'activités et durée de la semaine à gauche, une barre par jour
 * (minutes d'activité, en dégradé) à droite ; points pour les jours sans activité. Le toucher ouvre les performances.
 */
@Composable
fun ColumnScope.ActivityWeek(vm: SuperViewModel, nav: NavHostController, strength: Boolean, health: Boolean) {
    val refresh by vm.refresh.collectAsState()
    val imported by app.superhealth.bridge.RunImport.runs.collectAsState()
    val sh by vm.settings.collectAsState()
    val devices by vm.devices.collectAsState()
    val today = LocalDate.now()
    val start = app.superhealth.training.domain.Analytics.weekStart(today)
    val history by vm.training.state.collectAsState()
    // dernière lecture réaffichée dès l'ouverture (lue à l'avance au démarrage) : le panneau ne repart pas de zéro
    val read by produceState(lastWeek, refresh, imported, history.history.size) {
        value = weekActivities(strength, health).also { lastWeek = it }
    }
    val list = read.filter { !localDate(it.start).isBefore(start) }
    val route = when {
        health && sh.watchModule && devices.isNotEmpty() -> "h/performance"
        strength -> "t/insights"
        else -> null
    }
    val minutes = list.sumOf { it.minutes }
    val byDay = list.groupBy { localDate(it.start) }.mapValues { (_, l) -> l.sumOf { it.minutes } }
    val max = (byDay.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val hue = HealthHues.Fitness
    val dot = Mf.colors.chip
    val shape = RoundedCornerShape(app.superhealth.ui.theme.CardRadius)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(shape).dashCardBackground(hue)
        .then(if (route != null) Modifier.clickable { nav.navigate(route) } else Modifier).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${list.size}", style = MaterialTheme.typography.headlineMedium, fontSize = 22.sp, modifier = Modifier.alignByBaseline())
                Text(" " + (if (list.size > 1) tr("activités") else tr("activité")), style = MaterialTheme.typography.bodyMedium,
                    color = Mf.colors.textSecondary, modifier = Modifier.alignByBaseline())
            }
            Text(trf("%1\$s cette semaine", formatDuration(minutes.toDouble())), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, maxLines = 1)
        }
        (0L..6L).forEach { i ->
            val d = start.plusDays(i)
            val m = byDay[d] ?: 0L
            Column(Modifier.width(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.width(7.dp).height(34.dp)) {
                    val w = size.width
                    if (m > 0) {
                        val h = maxOf(w, size.height * m / max)
                        drawRoundRect(app.superhealth.ui.theme.dataBrush(hue, Offset(0f, size.height), Offset(0f, size.height - h)),
                            Offset(0f, size.height - h), Size(w, h), CornerRadius(w / 2))
                    } else drawCircle(dot, w / 2, Offset(w / 2, size.height - w / 2), alpha = if (d.isAfter(today)) 0.5f else 1f)
                }
                Spacer(Modifier.height(4.dp))
                Text(app.superhealth.ui.dayLetter(d), style = MaterialTheme.typography.labelMedium, fontSize = 12.sp,
                    fontWeight = if (d == today) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (d == today) Mf.colors.text else Mf.colors.textSecondary)
            }
        }
    }
}

/**
 * Activités récentes : la dernière sortie avec une distance en grand (distance, temps et allure, tracé du parcours quand
 * il existe), puis les deux activités suivantes en petites tuiles (volume soulevé, distance ou durée).
 */
@Composable
fun ColumnScope.RecentActivities(vm: SuperViewModel, nav: NavHostController, strength: Boolean, health: Boolean) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val sh by vm.settings.collectAsState()
    val devices by vm.devices.collectAsState()
    val imported by app.superhealth.bridge.RunImport.runs.collectAsState()
    val history by vm.training.state.collectAsState()
    // dernière lecture réaffichée dès l'ouverture ; lue à l'avance au démarrage (voir preloadRecent)
    val recent by produceState(lastRecent, refresh, sh.watchModule, imported, history.history.size) {
        // les dernières, quel que soit leur âge (historique rapatrié de la montre, pause hivernale…) : la liste tout de suite, les tracés ensuite
        val l = tabActivities(0L, strength, health)
        value = Recent(l, lastRecent.routes).also { lastRecent = it }
        value = Recent(l, routesOf(ctx, l)).also { lastRecent = it }
    }
    val list = recent.list
    val routes = recent.routes
    SectionLabel(tr("Récentes")) {
        if (list.isNotEmpty()) Text(tr("Tout voir"), Modifier.clickable { nav.navigate(if (health) "h/activities" else "history") }, color = Mf.colors.accent,
            style = MaterialTheme.typography.labelLarge)
    }
    if (list.isEmpty()) {
        MfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Watch, null, Modifier.size(28.dp), tint = Mf.colors.accent)
            Spacer(Modifier.width(12.dp))
            Text(when {
                !health -> tr("Vos séances terminées apparaîtront ici.")
                sh.watchModule && devices.isNotEmpty() -> tr("Lancez une activité sur la montre : elle apparaîtra ici après synchronisation.")
                sh.healthConnect -> tr("Les activités de vos autres apps (Apple Santé) apparaîtront ici.")
                else -> tr("Associez une montre ou Apple Santé pour retrouver vos activités ici.")
            }, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        }
        }
        return
    }
    // en grand : la dernière course, sinon la dernière sortie avec une distance (choix stable : il ne dépend pas des tracés encore à lire)
    val lead = list.take(5).let { l -> l.firstOrNull { it.kind == Bridge.Kind.RUN && (it.distanceM ?: 0.0) >= 100 } ?: l.firstOrNull { (it.distanceM ?: 0.0) >= 100 } }
    val rest = list.filter { it != lead }.take(if (lead == null) 4 else 2)
    lead?.let { a -> app.superhealth.ui.theme.CardTone(a.kind.hue()) { RouteTile(a, routes[a.key]) { openActivity(ctx, nav, a) } } }
    rest.chunked(2).forEach { pair ->
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            pair.forEach { a -> app.superhealth.ui.theme.CardTone(a.kind.hue()) { SmallActivityTile(a, history.history, routes[a.key], Modifier.weight(1f).fillMaxHeight()) { openActivity(ctx, nav, a) } } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Activités récentes et tracés des premières (clé de l'activité → tracé). */
private class Recent(val list: List<Bridge.Activity>, val routes: Map<String, List<Pair<Double, Double>>>)
private var lastRecent = Recent(emptyList(), emptyMap())
private var lastWeek = emptyList<Bridge.Activity>()

private suspend fun weekActivities(strength: Boolean, health: Boolean) = tabActivities(
    app.superhealth.training.domain.Analytics.weekStart(LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), strength, health)

private suspend fun routesOf(ctx: Context, l: List<Bridge.Activity>) =
    l.take(5).filter { (it.distanceM ?: 0.0) >= 100 }.mapNotNull { a -> routeOf(ctx, a)?.let { a.key to it } }.toMap()

/** Lit à l'avance les activités récentes et leurs tracés : l'onglet Activités les montre dès son ouverture. */
suspend fun preloadRecent(ctx: Context, strength: Boolean, health: Boolean) {
    lastWeek = weekActivities(strength, health)
    val l = tabActivities(0L, strength, health)
    lastRecent = Recent(l, lastRecent.routes)
    lastRecent = Recent(l, routesOf(ctx, l))
}

/** Temps d'une activité : « 28:41 », ou « 1:05:12 » au-delà d'une heure. */
internal fun clock(sec: Long): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

/** Allure (course, marche, randonnée) ou vitesse (vélo…) moyenne. */
internal fun pace(a: Bridge.Activity): String? {
    val m = a.distanceM?.takeIf { it >= 100 } ?: return null
    val sec = (a.end - a.start) / 1000
    if (sec <= 0) return null
    return if (a.kind == Bridge.Kind.BIKE) formatNumber(m / 1000 / (sec / 3600.0), 1) + " km/h"
    else (sec / (m / 1000)).roundToLong().let { "%d:%02d /km".format(it / 60, it % 60) }
}

/** Tuile de la dernière sortie : distance en grand, temps et allure, tracé à droite. */
@Composable
private fun RouteTile(a: Bridge.Activity, route: List<Pair<Double, Double>>?, onClick: () -> Unit) {
    val hue = a.kind.hue()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).dashCardBackground(hue).clickable(onClick = onClick)
        .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            app.superhealth.ui.dashboard.TileLabel(a.kind.icon(), "${tr(a.title)} · ${relativeDay(localDate(a.start)).lowercase()}", hue)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatNumber((a.distanceM ?: 0.0) / 1000, 1), style = MaterialTheme.typography.headlineMedium, fontSize = 30.sp,
                    modifier = Modifier.alignByBaseline())
                Text(" km", style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.alignByBaseline())
            }
            Text(listOfNotNull(clock((a.end - a.start) / 1000), pace(a)).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, maxLines = 1)
        }
        route?.let { RouteTrace(it, hue, Modifier.padding(start = 8.dp).size(118.dp, 74.dp)) }
    }
}

/** Petite tuile d'activité : nom et jour, puis le volume soulevé (musculation), la distance ou la durée, et le tracé à droite s'il existe. */
@Composable
private fun SmallActivityTile(a: Bridge.Activity, history: List<app.superhealth.training.domain.Workout>, route: List<Pair<Double, Double>>?, modifier: Modifier, onClick: () -> Unit) {
    val volume = a.gymId?.let { id -> history.firstOrNull { it.id == id } }
        ?.exercises?.sumOf { app.superhealth.training.domain.Analytics.volume(it.sets) }?.takeIf { it > 0 }
    val value = when {
        volume != null -> if (volume >= 1000) formatNumber(volume / 1000, 1) + " t" else formatNumber(volume) + " kg"
        (a.distanceM ?: 0.0) >= 100 -> formatNumber(a.distanceM!! / 1000, 1) + " km"
        else -> formatDuration(a.minutes.toDouble())
    }
    Column(modifier.clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).dashCardBackground(a.kind.hue()).clickable(onClick = onClick).padding(14.dp)) {
        app.superhealth.ui.dashboard.TileLabel(a.kind.icon(), "${tr(a.title)} · ${relativeDay(localDate(a.start)).lowercase()}", a.kind.hue())
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontSize = 22.sp, maxLines = 1, modifier = Modifier.weight(1f))
            route?.let { RouteTrace(it, a.kind.hue(), Modifier.size(52.dp, 32.dp), stroke = 2.dp) }
        }
    }
}

private val routeCache = ConcurrentHashMap<String, List<Pair<Double, Double>>>()

/** Tracé GPS d'une activité (latitude, longitude), allégé à 150 points et gardé en mémoire et sur le disque (lire une séance de montre est long) ; null sans tracé. */
suspend fun routeOf(ctx: Context, a: Bridge.Activity): List<Pair<Double, Double>>? {
    routeCache[a.key]?.let { return it.takeIf { r -> r.isNotEmpty() } }
    val file = File(File(ctx.filesDir, "routes").apply { mkdirs() }, a.key.replace('/', '_') + ".txt")
    val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (file.exists()) runCatching { file.readText().lines().map { it.substringBefore(' ').toDouble() to it.substringAfter(' ').toDouble() } }.getOrNull()
        else readRoute(ctx, a)?.also { pts -> runCatching { file.writeText(pts.joinToString("\n") { "${it.first} ${it.second}" }) } }
    }
    routeCache[a.key] = r.orEmpty()
    return r
}

private suspend fun readRoute(ctx: Context, a: Bridge.Activity): List<Pair<Double, Double>>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val id = a.watch?.id ?: a.run?.id
    val pts = runCatching {
        when {
            id != null -> app.superhealth.health.data.WorkoutDetails.load(ctx, id)?.points?.mapNotNull { p -> p.lat?.let { la -> p.lon?.let { la to it } } }
            else -> null
        }
    }.getOrNull()?.takeIf { it.size >= 2 } ?: return@withContext null
    val step = (pts.size + 149) / 150
    pts.filterIndexed { i, _ -> i % step == 0 || i == pts.lastIndex }
}

/** Parcours : tracé en dégradé de la couleur de l'activité, départ en point plein, arrivée cerclée. */
@Composable
fun RouteTrace(pts: List<Pair<Double, Double>>, hue: Color, modifier: Modifier, stroke: androidx.compose.ui.unit.Dp = 3.dp) {
    val ink = Mf.colors.text
    Canvas(modifier) {
        val k = kotlin.math.cos(Math.toRadians(pts.map { it.first }.average()))
        val xs = pts.map { it.second * k }; val ys = pts.map { -it.first }
        val w = stroke.toPx(); val pad = w * 2
        val sx = (xs.max() - xs.min()).coerceAtLeast(1e-9); val sy = (ys.max() - ys.min()).coerceAtLeast(1e-9)
        val s = minOf((size.width - 2 * pad) / sx, (size.height - 2 * pad) / sy)
        val ox = (size.width - sx * s) / 2 - xs.min() * s; val oy = (size.height - sy * s) / 2 - ys.min() * s
        val o = pts.indices.map { Offset((xs[it] * s + ox).toFloat(), (ys[it] * s + oy).toFloat()) }
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(o[0].x, o[0].y); o.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, app.superhealth.ui.theme.dataBrush(hue, Offset.Zero, Offset(size.width, size.height)),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(ink, w * 1.3f, o.first())
        drawCircle(hue, w * 1.3f, o.last(), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.7f))
    }
}

/** Réglages > Données : récupérer l'historique de la montre et importer des fichiers d'activités. */
@Composable
fun ActivityImportScreen(vm: SuperViewModel, nav: NavHostController) {
    DetailScaffold(tr("Import d'activités"), onBack = { nav.popBackStack() }) {
        Text(tr("Retrouvez vos anciennes séances : elles rejoignent l'onglet Activités, les records et la charge d'entraînement."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        WatchHistoryCard(vm)
        ImportActivitiesCard(vm, nav)
    }
}

/** Rapatrie les activités gardées en mémoire par une montre Garmin (après une réinitialisation ou un nouvel appairage). */
@Composable
fun ColumnScope.WatchHistoryCard(vm: SuperViewModel) {
    val ctx = LocalContext.current
    val devices by vm.devices.collectAsState()
    val garmin = devices.filter { app.superhealth.bridge.Watch.supportsHistory(it.device) }
    if (garmin.isEmpty()) return
    val busy = garmin.any { it.busy }
    // Progression de la récupération (compteurs du moteur Garmin), relue chaque seconde pendant la synchro.
    val progress: Pair<Int, Int>? = null
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).background(Mf.colors.card)
        .clickable(enabled = !busy) {
            val ready = garmin.filter { it.connected }
            if (ready.isEmpty()) {
                garmin.forEach { vm.fetchHistory(it) } // reconnexion d'abord
                Toast.makeText(ctx, tr("Montre déconnectée : reconnexion en cours, réessayez dans un instant."), Toast.LENGTH_LONG).show()
            } else {
                ready.forEach { vm.fetchHistory(it) }
                Toast.makeText(ctx, tr("Récupération lancée : les activités apparaissent au fil du téléchargement."), Toast.LENGTH_LONG).show()
            }
        }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(HealthHues.Fitness.container()), contentAlignment = Alignment.Center) {
            if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = HealthHues.Fitness)
            else Icon(Icons.Rounded.History, null, Modifier.size(22.dp), tint = HealthHues.Fitness)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Récupérer l'historique de la montre"), style = MaterialTheme.typography.bodyLarge)
            val p = progress
            Text(when {
                p != null && p.second > 0 -> trf("Récupération : %1\$s / %2\$s activités", p.first, p.second)
                p != null -> tr("Recherche des activités sur la montre…")
                busy -> tr("Synchronisation en cours…")
                else -> tr("Activités encore stockées sur la montre, même déjà synchronisées")
            }, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            if (p != null && p.second > 0) androidx.compose.material3.LinearProgressIndicator(progress = { p.first.toFloat() / p.second },
                Modifier.fillMaxWidth().padding(top = 6.dp), color = HealthHues.Fitness, trackColor = HealthHues.Fitness.container())
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
    }
}

/** Import d'activités passées : fichiers GPX, TCX, FIT ou export ZIP des apps de running. */
@Composable
fun ColumnScope.ImportActivitiesCard(vm: SuperViewModel, nav: NavHostController) {
    val importer = rememberFitImport(vm, nav)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).background(Mf.colors.card)
        .clickable { importer() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(HealthHues.Fitness.container()), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FileOpen, null, Modifier.size(22.dp), tint = HealthHues.Fitness)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Importer des activités"), style = MaterialTheme.typography.bodyLarge)
            Text(tr("Strava, Garmin, Polar, Suunto, Coros, Nike… (GPX, TCX, FIT ou ZIP)"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
    }
}

/**
 * Sélecteur de fichiers + fenêtre de progression puis de résultat. Renvoie l'action qui ouvre le sélecteur.
 * Après l'import, « Voir » ouvre l'historique des activités.
 */
@Composable
fun rememberFitImport(vm: SuperViewModel, nav: NavHostController): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var result by remember { mutableStateOf<Result<app.superhealth.bridge.RunImport.ImportResult>?>(null) }
    val pick = rememberFilePicker(multiple = true) { uris ->
        if (uris.isEmpty()) return@rememberFilePicker
        progress = 0 to 0
        scope.launch {
            result = runCatching { app.superhealth.bridge.RunImport.import(ctx, uris) { done, total -> progress = done to total } }
            progress = null
            app.superhealth.health.data.HealthRepository.invalidate(); vm.bump()
        }
    }
    progress?.let { (done, total) ->
        AlertDialog(onDismissRequest = {}, containerColor = Mf.colors.card, confirmButton = {},
            title = { Text(tr("Import en cours…")) },
            text = {
                Column {
                    if (total > 0) {
                        LinearProgressIndicator(progress = { done.toFloat() / total }, Modifier.fillMaxWidth(), color = HealthHues.Fitness,
                            trackColor = HealthHues.Fitness.container())
                        Spacer(Modifier.height(8.dp))
                        Text(trf("%1\$s sur %2\$s fichiers", done, total), color = Mf.colors.textSecondary)
                    } else LinearProgressIndicator(Modifier.fillMaxWidth(), color = HealthHues.Fitness, trackColor = HealthHues.Fitness.container())
                }
            })
    }
    result?.let { r ->
        val res = r.getOrNull()
        AlertDialog(onDismissRequest = { result = null }, containerColor = Mf.colors.card,
            title = { Text(tr(if (res != null && res.imported > 0) "Activités importées" else "Import terminé")) },
            text = {
                Text(r.fold({
                    listOfNotNull(
                        trf("%1\$s activité(s) importée(s)", it.imported),
                        it.skipped.takeIf { n -> n > 0 }?.let { n -> trf("%1\$s déjà présente(s)", n) },
                        it.failed.takeIf { n -> n > 0 }?.let { n -> trf("%1\$s fichier(s) illisible(s)", n) },
                    ).joinToString("\n")
                }, { e -> trf("Erreur : %1\$s", e.message) }))
            },
            confirmButton = {
                if (res != null && res.imported > 0) TextButton(onClick = { result = null; nav.navigate("h/activities") }) { Text(tr("Voir"), color = Mf.colors.accent) }
                else TextButton(onClick = { result = null }) { Text(tr("OK"), color = Mf.colors.accent) }
            },
            dismissButton = { if (res != null && res.imported > 0) TextButton(onClick = { result = null }) { Text(tr("Fermer"), color = Mf.colors.text) } })
    }
    return { pick() }
}

/** Historique complet de toutes les activités, par mois. */
@Composable
fun ActivitiesHistoryScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val imported by app.superhealth.bridge.RunImport.runs.collectAsState()
    val list by produceState(emptyList<Bridge.Activity>(), refresh, imported) {
        value = runCatching { Bridge.activities(0L) }.getOrDefault(emptyList())
    }
    val import = rememberFitImport(vm, nav)
    DetailScaffold(tr("Toutes les activités"), onBack = { nav.popBackStack() }, action = {
        IconButton(onClick = import) { Icon(Icons.Rounded.FileOpen, tr("Importer des activités")) }
    }) {
        if (list.isEmpty()) MfCard { Text(tr("Aucune activité pour l'instant."), color = Mf.colors.textSecondary) }
        list.groupBy { localDate(it.start).withDayOfMonth(1) }.forEach { (month, items) ->
            SectionLabel(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", app.superhealth.ui.Lang.locale))
                .replaceFirstChar { it.uppercase(app.superhealth.ui.Lang.locale) })
            GroupCard {
                items.forEachIndexed { i, a ->
                    if (i > 0) RowDivider()
                    ActivityRow(a) { openActivity(ctx, nav, a) }
                }
            }
        }
    }
}
