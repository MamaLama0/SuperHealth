package app.superhealth.ui.activities

import app.superhealth.platform.*

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.has
import app.superhealth.SuperPrefs
import app.superhealth.modules.Modules
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import kotlinx.coroutines.launch

@Composable
fun ActivitySettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sh by SuperPrefs.settings.collectAsState()
    val installed by Modules.installed.collectAsState()
    val order = (sh.activitySections.mapNotNull { n -> ActivitySection.entries.firstOrNull { it.name == n } } + ActivitySection.entries).distinct()
    val chosen = if (sh.activityAsked) sh.activitySections else sh.sections().map { it.name }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun move(a: ActivitySection, delta: Int) {
        val names = chosen.toMutableList(); val index = names.indexOf(a.name); val to = index + delta
        if (index >= 0 && to in names.indices) { names.add(to, names.removeAt(index)); SuperPrefs.update { it.copy(activitySections = names, activityAsked = true) } }
    }
    DetailScaffold(tr("Activités affichées"), onBack = { nav.popBackStack() }) {
        Text(tr("Choisissez ce qui apparaît dans Activités. Masquer une section conserve vos séances et vos données."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        order.forEach { a ->
            val on = a.name in chosen && (a.feature == null || sh.has(a.feature)) && (a.plugin == null || Modules.isActive(a.plugin))
            MfCard(Modifier.padding(top = 10.dp)) {
                SwitchRow(tr(a.label), tr(a.summary), on) { enabled ->
                    if (busy == a.name) return@SwitchRow
                    if (enabled && a.plugin != null && !Modules.isActive(a.plugin)) scope.launch {
                        busy = a.name; error = null
                        if (Modules.ensure(ctx, a.plugin)) SuperPrefs.update { s -> s.copy(activitySections = (s.activitySections + a.name).distinct(), activityAsked = true) }
                        else error = tr("Plugin non installé")
                        busy = null
                    } else SuperPrefs.update { s -> s.copy(activitySections = if (enabled) (chosen + a.name).distinct() else chosen - a.name,
                        activityAsked = true, features = if (enabled && a.feature != null) s.features + a.feature.name else s.features) }
                }
                if (on) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton({ move(a, -1) }, enabled = chosen.indexOf(a.name) > 0) { Icon(Icons.Rounded.ArrowUpward, tr("Monter")) }
                    IconButton({ move(a, 1) }, enabled = chosen.indexOf(a.name) in 0 until chosen.lastIndex) { Icon(Icons.Rounded.ArrowDownward, tr("Descendre")) }
                }
                if (busy == a.name) Text(tr("Installation…"), style = MaterialTheme.typography.bodySmall)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (chosen.isEmpty()) Text(tr("Aucune section affichée. Vous pourrez revenir ici depuis les paramètres."), style = MaterialTheme.typography.bodySmall)
    }
}
