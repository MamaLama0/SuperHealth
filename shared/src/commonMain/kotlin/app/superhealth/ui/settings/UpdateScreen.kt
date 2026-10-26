package app.superhealth.ui.settings

import app.superhealth.platform.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.ui.tr
import app.superhealth.update.Updater

/** Version installée ; un appui ouvre les versions publiées (l'ipa s'installe avec l'outil de sideload). */
@Composable
fun VersionRow(nav: NavHostController) {
    SettingRow("SuperHealth " + Updater.installed(appContext), tr("iOS"), icon = Icons.Rounded.SystemUpdate, hue = app.superhealth.ui.theme.Base.Grey,
        onClick = { openUrl("https://github.com/" + SuperPrefs.settings.value.updateRepo + "/releases") })
}
