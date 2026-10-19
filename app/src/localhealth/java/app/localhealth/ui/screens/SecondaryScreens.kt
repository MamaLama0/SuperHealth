package app.localhealth.ui.screens

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localhealth.HealthViewModel
import app.localhealth.data.Category
import app.localhealth.data.DeviceUi
import app.localhealth.data.HealthRepository
import app.localhealth.data.Metric
import app.localhealth.data.Workout
import app.localhealth.ui.Navigator
import app.localhealth.ui.Route
import app.localhealth.ui.components.DetailTopBar
import app.localhealth.ui.components.GroupedItem
import app.localhealth.ui.components.GroupedRow
import app.localhealth.ui.components.GsIcon
import app.localhealth.ui.components.ListHeader
import app.localhealth.ui.components.TwoColumnGrid
import app.localhealth.ui.formatDuration
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AboutActivity
import nodomain.freeyourgadget.gadgetbridge.activities.AboutUserPreferencesActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ControlCenterv2
import nodomain.freeyourgadget.gadgetbridge.activities.DataManagementActivity
import nodomain.freeyourgadget.gadgetbridge.activities.OpenFwAppInstallerActivity
import nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.DiscoveryActivityV2
import nodomain.freeyourgadget.gadgetbridge.activities.preferences.HealthConnectPreferencesActivity
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutDetailsActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser

@Composable
private fun Page(title: String, nav: Navigator, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        DetailTopBar(title, onBack = nav::pop, actions = actions)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---- Connexions (devices and apps) --------------------------------------------------------

@Composable
fun ConnectionsScreen(vm: HealthViewModel, nav: Navigator) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val devices by vm.devices.collectAsStateWithLifecycle()
    val syncing = devices.any { it.busy || it.connecting }
    Page(stringResource(R.string.lh_connections), nav) {
        ListHeader(stringResource(R.string.lh_devices)) {
            if (syncing) {
                CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            } else {
                IconButton(onClick = vm::syncAll, enabled = devices.isNotEmpty()) {
                    GsIcon(R.drawable.gs_sync_vd_theme_24, cs.onSurfaceVariant, description = stringResource(R.string.lh_sync_devices))
                }
            }
        }
        val count = devices.size + 1
        devices.forEachIndexed { i, d -> DeviceRow(i, count, d, vm) }
        GroupedRow(
            count - 1, count, null, stringResource(R.string.lh_connect_watch),
            leading = { DeviceGlyph(R.drawable.gs_watch_vd_theme_48) },
            onClick = { context.startActivity(Intent(context, DiscoveryActivityV2::class.java)) },
        )

        Spacer(Modifier.height(16.dp))
        ListHeader(stringResource(R.string.lh_apps_services))
        GroupedRow(0, 2, null, stringResource(R.string.lh_health_connect), stringResource(R.string.lh_health_connect_desc),
            leading = { DeviceGlyph(R.drawable.gs_health_connect_vd_theme_24) },
            onClick = { context.startActivity(Intent(context, HealthConnectPreferencesActivity::class.java)) })
        GroupedRow(1, 2, null, stringResource(R.string.lh_your_data), stringResource(R.string.lh_your_data_desc),
            leading = { DeviceGlyph(R.drawable.gs_database_vd_theme_24) },
            onClick = { context.startActivity(Intent(context, DataManagementActivity::class.java)) })
    }
}

@Composable
private fun DeviceGlyph(icon: Int) {
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center) {
        GsIcon(icon, MaterialTheme.colorScheme.primary, size = 32.dp)
    }
}

@Composable
private fun DeviceRow(index: Int, count: Int, d: DeviceUi, vm: HealthViewModel) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    GroupedItem(index, count, onClick = {
        context.startActivity(Intent(context, DeviceSettingsActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, d.device))
    }) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(painterResource(d.icon), null, Modifier.size(34.dp),
                tint = if (d.connected) cs.primary else cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), color = cs.onSurface)
            Text(listOfNotNull(d.stateText, d.battery?.let { stringResource(R.string.lh_battery, it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = cs.onSurfaceVariant)
        }
        if (d.busy || d.connecting) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            FilledTonalButton(onClick = { vm.sync(d) }) {
                Text(stringResource(if (d.connected) R.string.lh_sync_short else R.string.lh_connect))
            }
        }
    }
}

// ---- Profile menu (avatar) ----------------------------------------------------------------

@Composable
fun ProfileScreen(@Suppress("UNUSED_PARAMETER") vm: HealthViewModel, nav: Navigator) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val user = ActivityUser()
    Column(Modifier.fillMaxSize().background(cs.secondaryContainer.copy(alpha = 0.55f)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = nav::pop) { GsIcon(R.drawable.gs_close_vd_theme_24, cs.onSurface, description = stringResource(R.string.lh_close)) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Surface(shape = RoundedCornerShape(32.dp), color = cs.surface, onClick = {
                context.startActivity(Intent(context, AboutUserPreferencesActivity::class.java))
            }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    app.localhealth.ui.components.Avatar(userInitial(), onClick = {
                        context.startActivity(Intent(context, AboutUserPreferencesActivity::class.java))
                    }, size = 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(user.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.lh_profile),
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp), color = cs.onSurface)
                        Text(stringResource(R.string.lh_local_only), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            color = cs.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val group1 = listOf(
                Triple(R.drawable.gs_user_attributes_vd_theme_24, R.string.lh_profile) { context.startActivity(Intent(context, AboutUserPreferencesActivity::class.java)) },
                Triple(R.drawable.gs_flag_vd_theme_24, R.string.lh_goals) { context.startActivity(Intent(context, AboutUserPreferencesActivity::class.java)) },
                Triple(R.drawable.gs_link_vd_theme_24, R.string.lh_health_connect) { context.startActivity(Intent(context, HealthConnectPreferencesActivity::class.java)) },
                Triple(R.drawable.gs_devices_wearables_vd_theme_24, R.string.lh_devices_and_apps) { nav.pop(); nav.push(Route.Connections) },
            )
            group1.forEachIndexed { i, (icon, label, action) ->
                GroupedRow(i, group1.size, icon, stringResource(label), container = cs.surface, iconTint = cs.onSurface, onClick = action)
            }
            Spacer(Modifier.height(12.dp))
            val group2 = listOf(
                Triple(R.drawable.gs_shield_with_heart_vd_theme_24, R.string.lh_your_data) { context.startActivity(Intent(context, DataManagementActivity::class.java)) },
                Triple(R.drawable.gs_settings_vd_theme_24, R.string.lh_settings) { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                Triple(R.drawable.gs_tune_vd_theme_24, R.string.lh_advanced) { context.startActivity(Intent(context, ControlCenterv2::class.java)) },
                Triple(R.drawable.gs_help_vd_theme_24, R.string.lh_help) { context.startActivity(Intent(context, AboutActivity::class.java)) },
            )
            group2.forEachIndexed { i, (icon, label, action) ->
                GroupedRow(i, group2.size, icon, stringResource(label), container = cs.surface, iconTint = cs.onSurface, onClick = action)
            }
        }
    }
}

// ---- Commencer (start a workout) ----------------------------------------------------------

@Composable
fun StartScreen(vm: HealthViewModel, nav: Navigator) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val devices by vm.devices.collectAsStateWithLifecycle()
    val recent by produceState(emptyList<Workout>()) {
        value = HealthRepository.workouts(System.currentTimeMillis() - 30 * 86_400_000L, System.currentTimeMillis())
    }
    Page(stringResource(R.string.lh_start), nav) {
        ListHeader(stringResource(R.string.lh_track_on_watch))
        Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                GsIcon(R.drawable.gs_watch_vd_theme_24, cs.primary)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.lh_track_on_watch_desc), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        val target = devices.firstOrNull { it.connected } ?: devices.firstOrNull()
        Surface(shape = CircleShape, color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth().height(59.dp), onClick = {
            if (target != null) {
                context.startActivity(Intent(context, OpenFwAppInstallerActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, target.device))
            } else {
                context.startActivity(Intent(context, DiscoveryActivityV2::class.java))
            }
        }) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                GsIcon(R.drawable.gs_add_vd_theme_24, cs.primary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(if (target != null) R.string.lh_send_route else R.string.lh_add_device_first),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = cs.primary)
            }
        }
        Spacer(Modifier.height(24.dp))
        ListHeader(stringResource(R.string.lh_recent_workouts)) {
            androidx.compose.material3.TextButton(onClick = { nav.pop(); nav.push(Route.History) }) {
                Text(stringResource(R.string.lh_see_all), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = cs.primary)
            }
        }
        WorkoutList(recent.take(6))
    }
}

@Composable
private fun WorkoutList(workouts: List<Workout>) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    if (workouts.isEmpty()) {
        Text(stringResource(R.string.lh_no_data), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(8.dp))
    }
    workouts.forEachIndexed { i, w ->
        GroupedRow(
            i, workouts.size, workoutIcon(w), w.name?.takeIf { it.isNotBlank() } ?: w.kind.getLabel(context),
            DateUtils.formatDateTime(context, w.start.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL) +
                " · " + formatDuration(context, (w.end.time - w.start.time) / 60000.0),
            iconTint = cs.onSurface,
            onClick = { context.startActivity(WorkoutDetailsActivity.createSingleWorkoutIntent(context, w.id, w.device)) },
        )
    }
}

/** Google Health's activity glyphs for the common Gadgetbridge activity kinds. */
private fun workoutIcon(w: Workout): Int {
    val k = w.kind.name
    return when {
        "SWIM" in k -> R.drawable.gs_fitbit_swim_vd_theme_24
        "CYCL" in k || "BIK" in k -> R.drawable.gs_fitbit_bike_vd_theme_24
        "HIK" in k -> R.drawable.gs_fitbit_hike_vd_theme_24
        "WALK" in k -> R.drawable.gs_fitbit_walk_vd_theme_24
        "RUN" in k || "JOG" in k -> R.drawable.gs_fitbit_run_vd_theme_24
        "YOGA" in k -> R.drawable.gs_fitbit_yoga_vd_theme_24
        "STRENGTH" in k || "WEIGHT" in k -> R.drawable.gs_fitbit_strength_training_vd_theme_24
        else -> R.drawable.gs_exercise_vd_theme_24
    }
}

// ---- Historique ---------------------------------------------------------------------------

@Composable
fun HistoryScreen(vm: HealthViewModel, nav: Navigator) {
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val workouts by produceState(emptyList<Workout>(), refresh) {
        value = HealthRepository.workouts(0L, System.currentTimeMillis())
    }
    Page(stringResource(R.string.lh_history), nav) {
        ListHeader(stringResource(R.string.lh_saved_workouts))
        WorkoutList(workouts)
    }
}

// ---- Category page ------------------------------------------------------------------------

@Composable
fun CategoryScreen(vm: HealthViewModel, nav: Navigator, category: Category) {
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val metrics = Metric.entries.filter { it.category == category }
    Page(stringResource(category.label), nav) {
        Spacer(Modifier.height(8.dp))
        TwoColumnGrid(metrics) { m, mod -> KeyStatCard(m, refresh, mod) { nav.push(Route.Detail(m)) } }
    }
}
