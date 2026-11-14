package app.superhealth.ui.settings

import app.superhealth.platform.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.ProgressBar
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.update.Updater

/** Version installée ; un appui ouvre les versions publiées (l'ipa s'installe avec l'outil de sideload). */
@Composable
fun VersionRow(nav: NavHostController) {
    SettingRow("SuperHealth " + Updater.installed(appContext), tr("iOS"), icon = Icons.Rounded.SystemUpdate, hue = app.superhealth.ui.theme.Base.Grey,
        onClick = { openUrl("https://github.com/" + SuperPrefs.settings.value.updateRepo + "/releases") })
}

/** Les mises à jour iOS sont installées par SideStore, avec les droits Apple Santé de l'app. */
@Composable
fun UpdateScreen(nav: NavHostController) {
    DetailScaffold(tr("Mises à jour"),{nav.popBackStack()}) {
        MfCard {
            Text("SuperHealth "+appVersion(),style=MaterialTheme.typography.headlineSmall)
            Text(tr("Sur iPhone, installez les nouvelles versions avec SideStore + HealthKit."),modifier=Modifier.padding(vertical=12.dp))
            PillButton(tr("Voir les versions iOS"),Modifier.fillMaxWidth()) { openUrl("https://github.com/MamaLama0/SuperHealth/releases?q=ios-v") }
        }
    }
}
