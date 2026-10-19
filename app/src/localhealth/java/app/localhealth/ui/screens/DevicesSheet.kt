package app.localhealth.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.localhealth.data.DeviceUi
import app.localhealth.ui.components.HealthCard
import app.localhealth.ui.components.IconBadge
import app.localhealth.ui.theme.extendedColors
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ControlCenterv2
import nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.DiscoveryActivityV2
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesSheet(devices: List<DeviceUi>, onSync: (DeviceUi) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.lh_devices), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp))
            devices.forEach { d -> DeviceCard(d, onSync) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.lh_add_device)) },
                leadingContent = { Icon(Icons.Rounded.Add, null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickableItem { context.startActivity(Intent(context, DiscoveryActivityV2::class.java)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.lh_settings)) },
                leadingContent = { Icon(Icons.Rounded.Settings, null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickableItem { context.startActivity(Intent(context, SettingsActivity::class.java)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.lh_advanced)) },
                leadingContent = { Icon(Icons.Rounded.Tune, null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickableItem { context.startActivity(Intent(context, ControlCenterv2::class.java)) },
            )
        }
    }
}

@Composable
private fun DeviceCard(d: DeviceUi, onSync: (DeviceUi) -> Unit) {
    val context = LocalContext.current
    HealthCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), onClick = {
        context.startActivity(
            Intent(context, DeviceSettingsActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, d.device)
        )
    }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Watch, MaterialTheme.extendedColors.blue, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(d.stateText, d.battery?.let { stringResource(R.string.lh_battery, it) })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (d.busy || d.connecting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.lh_syncing), style = MaterialTheme.typography.labelLarge)
                } else {
                    FilledTonalButton(onClick = { onSync(d) }) {
                        Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (d.connected) R.string.lh_sync else R.string.lh_connect))
                    }
                }
            }
        }
    }
}

@Composable
fun NoDeviceCard(onAdd: () -> Unit) {
    HealthCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column {
            Text(stringResource(R.string.lh_no_device_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.lh_no_device_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAdd) { Text(stringResource(R.string.lh_add_device)) }
        }
    }
}

private fun Modifier.clickableItem(onClick: () -> Unit) = this.clickable(onClick = onClick)
