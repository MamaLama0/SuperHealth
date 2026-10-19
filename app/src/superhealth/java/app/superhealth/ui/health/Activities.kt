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
import app.superhealth.ui.theme.WorkoutBlue
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
fun ActivityRow(a: Bridge.Activity, onClick: () -> Unit) {
    val hr by produceState<Int?>(null, a.key) { value = runCatching { Bridge.averageHeartRate(a.start, a.end) }.getOrNull() }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val tint = if (a.kind == Bridge.Kind.STRENGTH) WorkoutBlue else HealthHues.Activity
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
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
    SectionLabel(tr("Autres activités")) {
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
