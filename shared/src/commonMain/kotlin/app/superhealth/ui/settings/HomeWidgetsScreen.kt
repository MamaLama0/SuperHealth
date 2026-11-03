package app.superhealth.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr

@Composable
fun HomeWidgetsScreen(nav: NavHostController) {
    DetailScaffold(tr("Widgets iPhone"),{nav.popBackStack()},contentSpacing=12.dp) {
        MfCard {
            Text(tr("Vos suivis, sans ouvrir l'app"),style=MaterialTheme.typography.headlineSmall)
            Text(tr("Faites un appui long sur l'écran d'accueil, touchez Modifier puis Ajouter un widget. Recherchez SuperHealth et choisissez la taille."),modifier=Modifier.padding(top=8.dp))
            Text(tr("Petit, moyen ou grand : choisissez le format dans la galerie iOS. Un appui ouvre le détail dans SuperHealth."),color=Mf.colors.textSecondary,modifier=Modifier.padding(top=8.dp))
        }
        listOf(
            "Social" to listOf("Classement","Progression","Séries","Quêtes de la semaine","Amis","Vue sociale"),
            "Santé" to listOf("Anneaux d'activité","Pas","Sommeil","Constantes"),
            "Nutrition" to listOf("Calories","Poids"),
            "Musculation" to listOf("Prochaine séance")
        ).forEach { (section,items) ->
            SectionLabel(tr(section))
            GroupCard { items.forEachIndexed { index,name ->
                if(index>0)RowDivider()
                SettingRow(tr(name),subtitle=if(section=="Social")tr("Petit, moyen et grand")else null) {}
            } }
        }
        MfCard { Text(tr("Les widgets affichent le dernier instantané de l'app. iOS décide des actualisations en arrière-plan ; ouvrez SuperHealth pour rafraîchir vos données."),color=Mf.colors.textSecondary) }
    }
}
