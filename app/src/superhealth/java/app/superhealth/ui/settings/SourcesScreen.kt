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
    DetailScaffold(tr("Sources"), onBack = { nav.popBackStack() }) {
        GroupCard {
            SettingRow("Gadgetbridge", subtitle = tr("Connexion aux montres")) { link("https://gadgetbridge.org/") }
            RowDivider()
            SettingRow("Open Food Facts", subtitle = tr("Données alimentaires")) { link("https://world.openfoodfacts.org/") }
            RowDivider()
            SettingRow("OSAV", subtitle = tr("Base suisse des aliments")) { link("https://www.valeursnutritives.ch/") }
        }
        GroupCard {
            SettingRow(tr("Animation de série"), subtitle = "indoorraccoon · aristote · CC BY 4.0") { link("https://rive.app/marketplace/28627-54130-streak-animation-improved/") }
            RowDivider()
            SettingRow(tr("Badges et trophées"), subtitle = "Achievement Badges · Garmin") { link("https://github.com/MamaLama0/SuperHealth/blob/superhealth/docs/CREDITS.md") }
            RowDivider()
            SettingRow(tr("Licences et crédits")) { link("https://github.com/MamaLama0/SuperHealth/blob/superhealth/docs/CREDITS.md") }
        }
    }
}
