package app.superhealth.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.DashCard
import app.superhealth.PlusAction
import app.superhealth.plusActions
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr

/** Personnalisation : quelles cartes, dans quel ordre. Les cartes « montre » s'affichent dès qu'une source existe. */
@Composable
fun DashboardEditScreen(vm: SuperViewModel, nav: NavHostController) {
    val sh by vm.settings.collectAsState()
    val shown = sh.dashboard
    fun set(list: List<DashCard>) = SuperPrefs.update { it.copy(dashboard = list) }
    DetailScaffold(tr("Personnaliser"), onBack = { nav.popBackStack() }, action = {
        TextButton(onClick = { set(DashCard.defaults) }) { Text(tr("Par défaut"), color = Mf.colors.accent, maxLines = 1) }
    }) {
        Text(tr("Choisissez les graphiques du tableau de bord et leur ordre."), style = MaterialTheme.typography.bodyMedium,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))

        // Accès direct au choix des constantes de la carte principale.
        GroupCard {
            Row(Modifier.fillMaxWidth().clickable { nav.navigate("vitals/edit") }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Constantes affichées"), style = MaterialTheme.typography.bodyLarge)
                    Text(sh.vitals.joinToString(" · ") { n -> tr(app.superhealth.health.data.Metric.entries.firstOrNull { it.name == n }?.label ?: n) },
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.graphicsLayer(rotationZ = -90f), tint = Mf.colors.textTertiary)
            }
        }
        SectionLabel(tr("Affichées"))
        if (shown.isEmpty()) Text(tr("Aucune carte affichée"), color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
        else GroupCard {
            shown.forEachIndexed { i, c ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(c.label), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tr(c.section.label), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                    IconButton(onClick = { set(shown.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, tr("Monter"), tint = if (i > 0) Mf.colors.text else Mf.colors.textTertiary)
                    }
                    IconButton(onClick = { set(shown.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < shown.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, tr("Descendre"), tint = if (i < shown.lastIndex) Mf.colors.text else Mf.colors.textTertiary)
                    }
                    Switch(true, { set(shown - c) }, Modifier.padding(end = 8.dp), colors = switchColors())
                }
            }
        }

        DashCard.Section.entries.forEach { section ->
            val hidden = DashCard.entries.filter { it.section == section && it !in shown }
            if (hidden.isEmpty()) return@forEach
            SectionLabel(tr(section.label))
            GroupCard {
                hidden.forEachIndexed { i, c ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr(c.label), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (c.watch) Text(tr("Montre ou Health Connect"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                        }
                        Switch(false, { set(shown + c) }, colors = switchColors())
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Constantes : quelles mesures de la montre (ou de Health Connect) dans la carte principale, et dans quel ordre. */
@Composable
fun VitalsEditScreen(vm: SuperViewModel, nav: NavHostController) {
    val sh by vm.settings.collectAsState()
    val shown = sh.vitals
    fun set(list: List<String>) = SuperPrefs.update { it.copy(vitals = list) }
    fun label(n: String) = app.superhealth.health.data.Metric.entries.firstOrNull { it.name == n }?.label ?: n
    DetailScaffold(tr("Constantes"), onBack = { nav.popBackStack() }, action = {
        TextButton(onClick = { set(listOf("RESTING_HR", "RESPIRATORY_RATE", "BODY_ENERGY")) }) { Text(tr("Par défaut"), color = Mf.colors.accent, maxLines = 1) }
    }) {
        Text(tr("La carte principale du tableau de bord. Choisissez ses mesures et leur ordre ; celles que ni la montre ni Health Connect ne fournissent restent masquées."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        SectionLabel(tr("Affichées"))
        if (shown.isEmpty()) Text(tr("Aucune mesure"), color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
        else GroupCard {
            shown.forEachIndexed { i, n ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(label(n)), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val m = app.superhealth.health.data.Metric.entries.firstOrNull { it.name == n }
                        if (m != null && !app.superhealth.bridge.Bridge.hasSource(m))
                            Text(tr("Pas de source pour l'instant"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                    IconButton(onClick = { set(shown.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, tr("Monter"), tint = if (i > 0) Mf.colors.text else Mf.colors.textTertiary)
                    }
                    IconButton(onClick = { set(shown.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < shown.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, tr("Descendre"), tint = if (i < shown.lastIndex) Mf.colors.text else Mf.colors.textTertiary)
                    }
                    Switch(true, { set(shown - n) }, Modifier.padding(end = 8.dp), colors = switchColors())
                }
            }
        }
        val hidden = app.superhealth.VITAL_CHOICES.filter { it !in shown }
        if (hidden.isNotEmpty()) {
            SectionLabel(tr("Disponibles"))
            GroupCard {
                hidden.forEachIndexed { i, n ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(label(n)), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Switch(false, { set(shown + n) }, colors = switchColors())
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Raccourcis du bouton « + » : lesquels, dans quel ordre. Un seul choisi : le « + » l'ouvre directement. */
@Composable
fun PlusEditScreen(vm: SuperViewModel, nav: NavHostController) {
    val sh by vm.settings.collectAsState()
    val shown = sh.plusActions
    fun set(list: List<PlusAction>) = SuperPrefs.update { it.copy(plus = list.map { a -> a.name }) }
    DetailScaffold(tr("Bouton +"), onBack = { nav.popBackStack() }, action = {
        TextButton(onClick = { set(PlusAction.defaults) }) { Text(tr("Par défaut"), color = Mf.colors.accent, maxLines = 1) }
    }) {
        Text(tr("Choisissez les raccourcis du bouton + et leur ordre. Avec un seul raccourci, le + l'ouvre directement."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        SectionLabel(tr("Affichés"))
        if (shown.isEmpty()) Text(tr("Aucun raccourci"), color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
        else GroupCard {
            shown.forEachIndexed { i, a ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(a.label), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tr(a.section.label), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                    IconButton(onClick = { set(shown.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, tr("Monter"), tint = if (i > 0) Mf.colors.text else Mf.colors.textTertiary)
                    }
                    IconButton(onClick = { set(shown.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < shown.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, tr("Descendre"), tint = if (i < shown.lastIndex) Mf.colors.text else Mf.colors.textTertiary)
                    }
                    Switch(true, { set(shown - a) }, Modifier.padding(end = 8.dp), colors = switchColors())
                }
            }
        }
        DashCard.Section.entries.forEach { section ->
            val hidden = PlusAction.entries.filter { it.section == section && it !in shown }
            if (hidden.isEmpty()) return@forEach
            SectionLabel(tr(section.label))
            GroupCard {
                hidden.forEachIndexed { i, a ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(a.label), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Switch(false, { set(shown + a) }, colors = switchColors())
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(checkedTrackColor = Mf.colors.accent, checkedThumbColor = Color.White,
    uncheckedBorderColor = Color.Transparent, uncheckedTrackColor = Mf.colors.chip)
