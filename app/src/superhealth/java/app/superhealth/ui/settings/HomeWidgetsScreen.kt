package app.superhealth.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.widget.WidgetCatalog

/** L'épinglage est confirmé par Android ; le lanceur garde le choix de la position et de la taille. */
@Composable
fun HomeWidgetsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val manager = remember(ctx) { AppWidgetManager.getInstance(ctx) }
    val supported = remember(manager) { manager.isRequestPinAppWidgetSupported }
    var rejected by remember { mutableStateOf(false) }
    DetailScaffold(tr("Widgets Android"), { nav.popBackStack() }, contentSpacing = 12.dp) {
        MfCard {
            Text(tr("Vos suivis, sans ouvrir l'app"), style = MaterialTheme.typography.headlineSmall)
            Text(tr("Choisissez une carte, puis confirmez son ajout. Un appui ouvre le détail dans SuperHealth."),
                style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            Text(tr("Carré, compact, bandeau ou large : redimensionnez le widget sur votre écran d'accueil."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        if (!supported || rejected) MfCard {
            Text(tr("Faites un appui long sur votre écran d'accueil, puis choisissez Widgets → SuperHealth."),
                style = MaterialTheme.typography.bodyMedium)
        }
        WidgetCatalog.entries.groupBy { it.section }.forEach { (section, entries) ->
            SectionLabel(tr(section))
            entries.forEach { entry ->
                MfCard {
                    Text(ctx.getString(entry.name).removePrefix("Social · ").removePrefix("Groupe · ").removePrefix("Group · "), style = MaterialTheme.typography.titleMedium)
                    Text(ctx.getString(entry.description), style = MaterialTheme.typography.bodySmall,
                        color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    if (supported) TextButton(onClick = {
                        rejected = !runCatching { manager.requestPinAppWidget(ComponentName(ctx, entry.receiver), null, null) }.getOrDefault(false)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Ajouter à l'écran d'accueil"))
                    }
                }
            }
        }
    }
}
