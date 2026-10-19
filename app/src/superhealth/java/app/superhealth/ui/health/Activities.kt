package app.superhealth.ui.health

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import app.superhealth.ui.localDate
import app.superhealth.ui.relativeDay
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.bridge.WatchHistory
import app.superhealth.nutrition.ui.components.SettingRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.superhealth.ui.trf
import kotlinx.coroutines.launch
import app.superhealth.ui.tr
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutDetailsActivity

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
            Text(listOf(relativeDay(localDate(a.start)), formatDuration(a.minutes.toDouble()), tr(a.source.label)).joinToString(" · "),
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
        a.phone != null -> nav.navigate("act/phone/${a.phone.id}")
        a.watch != null -> ctx.startActivity(WorkoutDetailsActivity.createSingleWorkoutIntent(ctx, a.watch.id, a.watch.device))
        else -> Toast.makeText(ctx, app.superhealth.ui.trf("Activité enregistrée par %1\$s", a.hc?.app ?: "Health Connect"), Toast.LENGTH_SHORT).show()
    }
}

/** Onglet Activités, sous la musculation : course, marche, randonnée… de la montre et de Health Connect. */
@Composable
fun ColumnScope.OtherActivities(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val sh by vm.settings.collectAsState()
    val devices by vm.devices.collectAsState()
    val list by produceState(emptyList<Bridge.Activity>(), refresh, sh.watchModule) {
        value = runCatching { Bridge.activities(System.currentTimeMillis() - 60 * 86_400_000L) }.getOrDefault(emptyList())
            .filter { it.source != Bridge.Source.GYM }
    }
    SectionLabel(tr("Cardio et plein air")) {
        if (list.isNotEmpty()) Text(tr("Tout voir"), Modifier.clickable { nav.navigate("h/activities") }, color = Mf.colors.accent,
            style = MaterialTheme.typography.labelLarge)
    }
    if (list.isEmpty()) MfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Watch, null, Modifier.size(28.dp), tint = Mf.colors.accent)
            Spacer(Modifier.width(12.dp))
            Text(when {
                sh.watchModule && devices.isNotEmpty() -> tr("Lancez une course, une marche ou une randonnée sur votre montre : elle apparaîtra ici après la synchronisation.")
                sh.healthConnect -> tr("Les activités enregistrées par vos autres apps (Health Connect) apparaîtront ici.")
                else -> tr("Associez une montre ou connectez Health Connect pour retrouver ici vos courses, marches et randonnées.")
            }, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        }
    } else GroupCard {
        list.take(5).forEachIndexed { i, a ->
            if (i > 0) RowDivider()
            ActivityRow(a) { openActivity(ctx, nav, a) }
        }
    }
    if (sh.watchModule) WatchHistoryCard(vm)
}

/**
 * Activités passées de la montre : relecture de tout ce qu'elle garde en mémoire (Garmin, y compris ce qui a déjà été
 * envoyé à Garmin Connect), ou import de fichiers .fit / de l'export ZIP de Garmin Connect.
 */
@Composable
fun ColumnScope.WatchHistoryCard(vm: SuperViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices by vm.devices.collectAsState()
    if (!WatchHistory.supported()) return
    var importing by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val msg = runCatching { WatchHistory.import(ctx, uris) }.fold(
                { r -> if (r.failed == 0) trf("%1\$s activité(s) importée(s)", r.imported) else trf("%1\$s importée(s), %2\$s illisible(s)", r.imported, r.failed) },
                { e -> trf("Erreur : %1\$s", e.message) })
            importing = false
            app.superhealth.health.data.HealthRepository.invalidate(); vm.bump()
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
    SectionLabel(tr("Historique de la montre"))
    GroupCard {
        val busy = devices.any { it.busy }
        SettingRow(tr("Récupérer les activités passées"), subtitle = tr(if (busy) "Récupération en cours…" else "Tout ce que la montre garde en mémoire, même déjà envoyé à Garmin Connect"),
            icon = Icons.Rounded.History) {
            val n = WatchHistory.fetchFromWatch()
            Toast.makeText(ctx, tr(if (n > 0) "Récupération lancée : cela peut prendre quelques minutes" else "Connectez d'abord la montre"), Toast.LENGTH_LONG).show()
        }
        RowDivider()
        SettingRow(tr("Importer des fichiers .fit"), subtitle = tr(if (importing) "Import en cours…" else "Fichiers .fit, ou export ZIP de Garmin Connect (garmin.com > Exporter vos données)"),
            icon = Icons.Rounded.FileOpen) { if (!importing) pick.launch(arrayOf("*/*")) }
    }
}

/** Historique complet de toutes les activités, par mois. */
@Composable
fun ActivitiesHistoryScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val list by produceState(emptyList<Bridge.Activity>(), refresh) {
        value = runCatching { Bridge.activities(0L) }.getOrDefault(emptyList())
    }
    DetailScaffold(tr("Toutes les activités"), onBack = { nav.popBackStack() }) {
        if (list.isEmpty()) MfCard { Text(tr("Aucune activité pour l'instant."), color = Mf.colors.textSecondary) }
        list.groupBy { localDate(it.start).withDayOfMonth(1) }.forEach { (month, items) ->
            SectionLabel(month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", app.superhealth.ui.Lang.locale))
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
