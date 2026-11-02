package app.superhealth.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.coach.SettingsHelp
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.coach.askCoach
import app.superhealth.ui.coach.coachAvailable
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf

@Composable
fun FindSettingScreen(nav: NavHostController) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val results = remember(query) { SettingsHelp.search(query) }
    DetailScaffold(tr("Trouver un réglage"), { nav.popBackStack() }, contentSpacing = 12.dp, action = {
        if (coachAvailable()) SettingsSearchAction { nav.askCoach(trf("Je cherche un réglage dans SuperHealth : %1\$s. Utilise la recherche des paramètres et donne-moi un bouton qui ouvre directement la bonne page. Explique brièvement quoi changer, sans modifier mes paramètres à ma place.", query.ifBlank { tr("aide-moi à trouver où configurer l'application") })) }
    }) {
        TextInput(tr("Que souhaitez-vous régler ?"), query, { query = it }, placeholder = tr("Calories par jour, repos, serveur, notifications…"))
        if (results.isEmpty()) MfCard {
            Text(tr("Aucun résultat"), style = MaterialTheme.typography.titleMedium)
            Text(tr("Essayez un mot plus court, comme calories, repos ou sommeil."), color = Mf.colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp))
            TextButton({ query = "" }) { Text(tr("Effacer la recherche")) }
        }
        if (results.isNotEmpty()) GroupCard {
            results.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                SettingRow(tr(d.label), subtitle = if (query.isBlank()) null else tr("Ouvrir ce réglage")) {
                    if (d.key == "stretch_editor" && app.superhealth.stretch.Stretch.draft.value == null) app.superhealth.stretch.Stretch.prepare()
                    nav.go(d.route)
                }
            }
        }
    }
}

@Composable
internal fun SettingsSearchAction(onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Mf.colors.card)
        .clickable(onClickLabel = tr("Trouver un réglage"), onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp)) {
            Icon(Icons.Rounded.Search, tr("Trouver un réglage"), Modifier.size(21.dp).align(Alignment.BottomStart), tint = Mf.colors.accent)
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(11.dp).align(Alignment.TopEnd), tint = Mf.colors.accent)
        }
    }
}
