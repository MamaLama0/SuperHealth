package app.superhealth.ui.health

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.PhoneSession
import app.superhealth.SuperPrefs
import app.superhealth.bridge.Bridge
import app.superhealth.bridge.LiveHeart
import app.superhealth.bridge.LiveSession
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.formatDuration
import app.superhealth.ui.formatNumber
import app.superhealth.ui.localDate
import app.superhealth.ui.relativeDay
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.delay

/** Activités qu'on peut suivre depuis le téléphone (la musculation a sa propre séance). */
val LIVE_KINDS = listOf(Bridge.Kind.RUN, Bridge.Kind.WALK, Bridge.Kind.HIKE, Bridge.Kind.BIKE, Bridge.Kind.SWIM, Bridge.Kind.YOGA, Bridge.Kind.OTHER)

/** Couleur d'une activité, partagée par les listes et la séance en direct. */
fun Bridge.Kind.hue(): Color = when (this) {
    Bridge.Kind.STRENGTH -> app.superhealth.ui.theme.WorkoutBlue
    Bridge.Kind.RUN -> HealthHues.Fitness
    Bridge.Kind.WALK, Bridge.Kind.HIKE -> HealthHues.Activity
    Bridge.Kind.BIKE -> HealthHues.Body
    Bridge.Kind.SWIM -> HealthHues.Respiration
    Bridge.Kind.YOGA -> HealthHues.Sleep
    Bridge.Kind.OTHER -> HealthHues.Mind
}

// ---- Zones cardiaques -----------------------------------------------------------------------------

data class Zone(val n: Int, val label: String, val color: Color, val minPct: Int)

val ZONES = listOf(
    Zone(1, "Échauffement", Color(0xFF8FA3B8), 0),
    Zone(2, "Endurance", HealthHues.Body, 60),
    Zone(3, "Aérobie", HealthHues.Activity, 70),
    Zone(4, "Seuil", HealthHues.Mind, 80),
    Zone(5, "Maximum", HealthHues.Heart, 90),
)

fun zoneOf(bpm: Int, max: Int): Zone = ZONES.last { bpm * 100 >= it.minPct * max }

// ---- Démarrer ------------------------------------------------------------------------------------

/** Onglet Activités : démarrer une course, une marche… suivie avec la fréquence cardiaque de la montre. */
@Composable
fun ColumnScope.StartActivity(nav: NavHostController) {
    val live by LiveSession.state.collectAsState()
    SectionLabel(tr("Cardio"))
    MfCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        if (live != null) {
            LiveBanner(live!!) { nav.navigate("act/live") }
            return@MfCard
        }
        Text(tr("Démarrer une activité"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 2.dp))
        Text(tr("Chrono et fréquence cardiaque de la montre en direct."), style = MaterialTheme.typography.bodySmall,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(Modifier.height(12.dp))
        LIVE_KINDS.chunked(4).forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(k.hue().container())
                            .clickable { LiveSession.start(k); nav.navigate("act/live") }.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(k.icon(), null, Modifier.size(24.dp), tint = k.hue().onContainer())
                        Spacer(Modifier.height(4.dp))
                        Text(tr(Bridge.kindLabel(k.name)), style = MaterialTheme.typography.labelMedium, color = k.hue().onContainer(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Bandeau « activité en cours » (onglets et carte Cardio). */
@Composable
fun LiveBanner(st: LiveSession.State, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val hue = st.kind.hue()
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(hue).clickable(onClick = onOpen)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(st.kind.icon(), null, tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(if (st.paused) "En pause" else "Activité en cours"), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            Text(tr(Bridge.kindLabel(st.kind.name)), style = MaterialTheme.typography.titleSmall, color = Color.White)
        }
        st.lastBpm?.let {
            Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp), tint = Color.White)
            Text(" $it", style = MaterialTheme.typography.titleSmall, color = Color.White)
            Spacer(Modifier.width(12.dp))
        }
        Text(chrono(st.activeMs(now)), style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

fun chrono(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

// ---- Séance en direct ----------------------------------------------------------------------------

@Composable
fun LiveSessionScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val st by LiveSession.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(500); now = System.currentTimeMillis() } }
    val s = st ?: run { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    val hue = s.kind.hue()
    val max = remember { LiveSession.maxHr() }
    val hasWatch = remember(now / 10_000) { LiveHeart.available() }

    Column(Modifier.fillMaxSize().background(hue.container()).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour"), tint = hue.onContainer()) }
            Icon(s.kind.icon(), null, tint = hue.onContainer())
            Spacer(Modifier.width(8.dp))
            Text(tr(Bridge.kindLabel(s.kind.name)), style = MaterialTheme.typography.titleLarge, color = hue.onContainer(), modifier = Modifier.weight(1f))
            if (s.paused) Text(tr("En pause"), style = MaterialTheme.typography.labelLarge, color = hue.onContainer(),
                modifier = Modifier.padding(end = 16.dp).clip(CircleShape).background(Mf.colors.card).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(chrono(s.activeMs(now)), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Mf.colors.text,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, maxLines = 1)
            Text(tr("Durée active"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))

            MfCard {
                val bpm = s.lastBpm
                val zone = bpm?.let { zoneOf(it, max) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, null, Modifier.size(28.dp), tint = HealthHues.Heart)
                    Spacer(Modifier.width(8.dp))
                    Text(bpm?.toString() ?: "—", style = MaterialTheme.typography.displayMedium)
                    Text(" " + tr("bpm"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 14.dp))
                    Spacer(Modifier.weight(1f))
                    zone?.let {
                        Text(trf("Zone %1\$s · %2\$s", it.n, tr(it.label)), style = MaterialTheme.typography.labelLarge, color = Color.White,
                            modifier = Modifier.clip(CircleShape).background(it.color).padding(horizontal = 10.dp, vertical = 5.dp), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(10.dp))
                ZoneBar(zone?.n)
                Spacer(Modifier.height(14.dp))
                val window = 10 * 60_000L
                val pts = s.hr.filter { it.first >= now - window }.map { it.first to it.second.toDouble() }
                HeartChart(pts, HealthHues.Heart, height = 130.dp, from = maxOf(s.start, now - window), to = now, gapMs = 60_000L,
                    live = !s.paused && hasWatch)
            }
            Spacer(Modifier.height(12.dp))
            MfCard {
                Row {
                    LiveStat(tr("FC moy."), s.avgBpm?.toString() ?: "—", Modifier.weight(1f))
                    LiveStat(tr("FC max."), s.maxBpm?.toString() ?: "—", Modifier.weight(1f))
                    LiveStat(tr("Calories"), LiveSession.kcal(s.hr, s.activeMs(now) / 1000)?.toString() ?: "—", Modifier.weight(1f))
                    LiveStat(tr("Pas"), if (s.steps > 0) formatNumber(s.steps.toDouble()) else "—", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Mf.colors.card.copy(alpha = 0.6f)).padding(12.dp)) {
                Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = Mf.colors.textSecondary)
                Spacer(Modifier.width(10.dp))
                Text(tr(if (hasWatch) "Pour le GPS et la distance, lancez aussi l'activité sur la montre : sa séance remplacera celle-ci après la synchronisation."
                    else "Aucune montre connectée : seul le chrono est suivi. Connectez-la pour voir votre cœur en direct."),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            RoundAction(Icons.Rounded.Stop, tr("Terminer"), Mf.colors.card, Mf.colors.text, 64) { if (s.paused) confirm = true else LiveSession.pause().also { confirm = true } }
            RoundAction(if (s.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, tr(if (s.paused) "Reprendre" else "Pause"), hue, Color.White, 84) {
                if (s.paused) LiveSession.resume() else LiveSession.pause()
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false; LiveSession.resume() }, containerColor = Mf.colors.card,
        title = { Text(tr("Terminer l'activité ?")) },
        text = { Text(trf("%1\$s d'activité.", formatDuration(s.activeMs(now) / 60_000.0))) },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                val saved = LiveSession.stop(save = true)
                vm.bump()
                if (saved == null) Toast.makeText(ctx, tr("Activité trop courte : non enregistrée"), Toast.LENGTH_SHORT).show()
                nav.popBackStack()
                saved?.let { nav.navigate("act/phone/${it.id}") }
            }) { Text(tr("Enregistrer"), color = Mf.colors.accent) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirm = false; LiveSession.stop(save = false); nav.popBackStack() }) { Text(tr("Supprimer"), color = HealthHues.Heart) }
                TextButton(onClick = { confirm = false; LiveSession.resume() }) { Text(tr("Reprendre"), color = Mf.colors.text) }
            }
        },
    )
}

@Composable
private fun ZoneBar(current: Int?) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ZONES.forEach { z ->
            Box(Modifier.weight(1f).height(if (z.n == current) 10.dp else 6.dp).clip(CircleShape)
                .background(if (current == null || z.n == current) z.color else z.color.copy(alpha = 0.3f)))
        }
    }
}

@Composable
private fun LiveStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, fg: Color, sizeDp: Int, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size((sizeDp * 0.45f).dp), tint = fg)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
    }
}

// ---- Détail d'une activité suivie sur le téléphone -----------------------------------------------

@Composable
fun PhoneSessionScreen(vm: SuperViewModel, nav: NavHostController, id: String) {
    val sessions by SuperPrefs.sessions.collectAsState()
    val s = sessions.firstOrNull { it.id == id } ?: run { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    val kind = runCatching { Bridge.Kind.valueOf(s.kind) }.getOrDefault(Bridge.Kind.OTHER)
    var delete by remember { mutableStateOf(false) }
    DetailScaffold(tr(Bridge.kindLabel(s.kind)), onBack = { nav.popBackStack() }, action = {
        IconButton(onClick = { delete = true }) { Icon(Icons.Rounded.DeleteOutline, tr("Supprimer")) }
    }) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).background(kind.hue().container()).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Mf.colors.card), contentAlignment = Alignment.Center) {
                Icon(kind.icon(), null, tint = kind.hue())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(formatDuration(s.activeSec / 60.0), style = MaterialTheme.typography.headlineMedium, color = kind.hue().onContainer())
                Text(relativeDay(localDate(s.start)) + " · " + timeOf(s.start) + " – " + timeOf(s.end) + " · " + tr("Téléphone"),
                    style = MaterialTheme.typography.bodySmall, color = kind.hue().onContainer())
            }
        }
        Spacer(Modifier.height(12.dp))
        MfCard {
            Row {
                LiveStat(tr("FC moy."), s.avgHr?.toString() ?: "—", Modifier.weight(1f))
                LiveStat(tr("FC max."), s.maxHr?.toString() ?: "—", Modifier.weight(1f))
                LiveStat(tr("Calories"), s.kcal?.toString() ?: "—", Modifier.weight(1f))
                LiveStat(tr("Pas"), if (s.steps > 0) formatNumber(s.steps.toDouble()) else "—", Modifier.weight(1f))
            }
        }
        val pts = s.hr.mapIndexedNotNull { i, v -> v?.let { s.start + i * PhoneSession.HR_STEP_S * 1000L to it.toDouble() } }
        if (pts.size >= 2) {
            SectionLabel(tr("Fréquence cardiaque"))
            MfCard {
                HeartChart(pts, HealthHues.Heart, height = 160.dp, from = s.start, to = s.end, gapMs = 120_000L,
                    xLabels = listOf(s.start to timeOf(s.start), s.end to timeOf(s.end)))
            }
            SectionLabel(tr("Zones cardiaques"))
            val max = LiveSession.maxHr()
            val byZone = pts.groupingBy { zoneOf(it.second.toInt(), max).n }.eachCount()
            MfCard {
                ZONES.reversed().forEach { z ->
                    val n = byZone[z.n] ?: 0
                    val f = n.toFloat() / pts.size
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Z${z.n}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                        Text(tr(z.label), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.width(96.dp), maxLines = 1)
                        Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(Mf.colors.chip)) {
                            if (f > 0) Box(Modifier.fillMaxWidth(f).height(10.dp).clip(CircleShape).background(z.color))
                        }
                        Text(formatDuration(n * PhoneSession.HR_STEP_S / 60.0), style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(64.dp), textAlign = TextAlign.End, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (delete) AlertDialog(
        onDismissRequest = { delete = false }, containerColor = Mf.colors.card,
        title = { Text(tr("Supprimer cette activité ?")) },
        confirmButton = { TextButton(onClick = { delete = false; LiveSession.delete(s.id); vm.bump(); nav.popBackStack() }) { Text(tr("Supprimer"), color = HealthHues.Heart) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(tr("Annuler"), color = Mf.colors.text) } },
    )
}
