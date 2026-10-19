package app.localhealth.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localhealth.HealthViewModel
import app.localhealth.ui.screens.DevicesSheet
import app.localhealth.ui.screens.FitnessScreen
import app.localhealth.ui.screens.HealthScreen
import app.localhealth.ui.screens.NoDeviceCard
import app.localhealth.ui.screens.TodayScreen
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.DiscoveryActivityV2

private enum class Tab(val label: Int, val icon: ImageVector) {
    TODAY(R.string.lh_nav_today, Icons.Rounded.Today),
    FITNESS(R.string.lh_nav_fitness, Icons.Rounded.FitnessCenter),
    HEALTH(R.string.lh_nav_health, Icons.Rounded.HealthAndSafety),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthApp(vm: HealthViewModel) {
    val context = LocalContext.current
    val devices by vm.devices.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showDevices by rememberSaveable { mutableStateOf(false) }
    val syncing = devices.any { it.busy || it.connecting }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Tab.entries[tab].label), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { vm.syncAll() }, enabled = devices.isNotEmpty() && !syncing) {
                        Icon(Icons.Rounded.Sync, stringResource(R.string.lh_sync))
                    }
                    IconButton(onClick = { showDevices = true }) {
                        Icon(Icons.Rounded.Watch, stringResource(R.string.lh_devices))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { if (devices.isEmpty()) vm.refresh() else vm.syncAll() },
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
        ) {
            val inner = PaddingValues(bottom = padding.calculateBottomPadding())
            Column {
                if (devices.isEmpty()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        NoDeviceCard { context.startActivity(Intent(context, DiscoveryActivityV2::class.java)) }
                    }
                }
                when (Tab.entries[tab]) {
                    Tab.TODAY -> TodayScreen(date, summary, vm::setDate, inner)
                    Tab.FITNESS -> FitnessScreen(summary, workouts, inner)
                    Tab.HEALTH -> HealthScreen(summary, inner)
                }
            }
        }
    }

    if (showDevices) {
        DevicesSheet(devices, onSync = vm::sync, onDismiss = { showDevices = false })
    }
}
