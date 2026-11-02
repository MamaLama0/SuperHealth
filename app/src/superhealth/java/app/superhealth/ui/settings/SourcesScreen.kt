package app.superhealth.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.tr

@Composable
fun SourcesScreen(nav: NavHostController) {
    val context = LocalContext.current
    fun link(url: String) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    DetailScaffold(tr("Sources et illustrations"), onBack = { nav.popBackStack() }) {
        SectionLabel(tr("Application et données"))
        Text("Gadgetbridge — AGPL · Open Food Facts — ODbL · Base suisse OSAV")
        SectionLabel(tr("Animation de série"))
        Text("streak animation improved — indoorraccoon. Remix de Dynamic streak fire — aristote.")
        Text(tr("Fichier original, couleurs et compteur pilotés par SuperHealth. Licence CC BY 4.0."))
        GroupCard {
            SettingRow(tr("Voir l'animation et son auteur")) { link("https://rive.app/marketplace/28627-54130-streak-animation-improved/") }
            RowDivider()
            SettingRow("Creative Commons Attribution 4.0") { link("https://creativecommons.org/licenses/by/4.0/") }
        }
        SectionLabel(tr("Badges et trophées"))
        Text(tr("Achievement Badges : illustrations locales fournies avec le projet, en bronze, argent, or, platine et diamant."))
        Text("Rive Android runtime — MIT")
    }
}
