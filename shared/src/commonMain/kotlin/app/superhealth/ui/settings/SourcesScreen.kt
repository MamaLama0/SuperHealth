package app.superhealth.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.tr

@Composable
fun SourcesScreen(nav: NavHostController) {
    DetailScaffold(tr("Sources et illustrations"),{nav.popBackStack()}) {
        SectionLabel(tr("Application et données"))
        Text("SuperHealth — AGPL · Open Food Facts — ODbL · Base suisse OSAV")
        Text("Apple Santé / HealthKit — données autorisées sur cet iPhone.")
        SectionLabel(tr("Badges et trophées"))
        Text(tr("Achievement Badges : illustrations locales fournies avec le projet, en bronze, argent, or, platine et diamant."))
        Text("Garmin — illustrations de badges. Les critères des séries SuperHealth sont indépendants de Garmin Connect.")
    }
}
