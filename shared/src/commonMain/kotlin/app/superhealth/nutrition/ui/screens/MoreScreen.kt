package app.superhealth.nutrition.ui.screens

import app.superhealth.platform.*

import androidx.compose.material.icons.rounded.Add
import app.superhealth.nutrition.ui.trf
import app.superhealth.platform.Context
import app.superhealth.platform.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.superhealth.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.nutrition.data.AppJson
import app.superhealth.nutrition.data.Backup
import app.superhealth.nutrition.data.Csv
import app.superhealth.nutrition.domain.AccentColor
import app.superhealth.nutrition.domain.EnergyUnit
import app.superhealth.nutrition.domain.ThemeMode
import app.superhealth.nutrition.domain.TrendAlpha
import app.superhealth.nutrition.domain.TrendVersion
import app.superhealth.nutrition.domain.ExpenditureVersion
import app.superhealth.nutrition.domain.WeightUnit
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.tr
import app.superhealth.nutrition.domain.Language
import app.superhealth.nutrition.domain.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.ScreenTitle
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.superhealth.platform.LocalDate

@Composable
fun MoreScreen(vm: AppViewModel, nav: NavHostController) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmWipe by remember { mutableStateOf(false) }
    val stamp = LocalDate.now().toString()

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    fun safely(block: suspend () -> String) = scope.launch { toast(runCatching { block() }.getOrElse { trf("Erreur : %1\$s", it.message) }) }
    fun set(f: (app.superhealth.nutrition.domain.Settings) -> app.superhealth.nutrition.domain.Settings) = vm.repo.prefs.setSettings(f(s))

    fun exportJson() = safely { saveFile("superhealth-nutrition-${stamp}.json", "application/json", AppJson.encodeToString(Backup.serializer(), vm.repo.backup()).encodeToByteArray()); tr("Sauvegarde exportée") }
    val importJson = rememberFilePicker { files ->
        files.firstOrNull()?.let { f -> safely { vm.repo.restore(AppJson.decodeFromString(Backup.serializer(), f.bytes.decodeToString())); tr("Sauvegarde restaurée") } }
    }
    // un seul export : archive ZIP avec le journal, les pesées et tendance, la dépense et les cibles
    fun exportCsv() = safely {
        val an = vm.analytics.value!!
        val zip = Zip.write(listOf("journal.csv" to Csv.foodLog(vm.repo.entries.all()), "pesees.csv" to Csv.weights(an), "depense.csv" to Csv.expenditure(an))
            .map { (n, t) -> n to t.encodeToByteArray() })
        saveFile("superhealth-nutrition-${stamp}.zip", "application/zip", zip)
        tr("Données exportées")
    }
    val importWeights = rememberFilePicker { files ->
        files.firstOrNull()?.let { f -> safely { val rows = Csv.parseWeights(f.bytes.decodeToString()); vm.repo.weighIns.upsertAll(rows); trf("%1\$s pesée(s) importée(s)", rows.size) } }
    }

    DetailScaffold(tr("Nutrition"), onBack = { nav.popBackStack() }) {
        Column {
            GroupCard {
                SettingRow(tr("Programme et objectif"), icon = Icons.Rounded.Flag) { nav.navigate("strategy") }
                RowDivider()
                SettingRow(tr("Recettes"), icon = Icons.AutoMirrored.Rounded.MenuBook) { nav.navigate("search/5/-1") }
                RowDivider()
                SettingRow(tr("Habitudes"), icon = Icons.Rounded.EventAvailable) { nav.navigate("habits") }
                RowDivider()
                SettingRow(tr("Mesures et photos"), icon = Icons.Rounded.Straighten) { nav.navigate("body") }
            }

            app.superhealth.nutrition.ui.components.GroupHeader("IA", Modifier.padding(start = 0.dp))
            GroupCard {
                SwitchRow(tr("Fonctions IA"), tr("Décrire ou photographier un repas"), s.aiEnabled) { v -> set { it.copy(aiEnabled = v) } }
                if (s.aiEnabled) {
                    RowDivider()
                    val ai by vm.repo.prefs.ai.collectAsState()
                    SettingRow(tr("Fournisseur"), subtitle = if (ai.ready) "${ai.provider.label.tr()} · ${ai.effectiveModel}" else tr("Non configuré"),
                        icon = Icons.Rounded.AutoAwesome) { nav.navigate("aisettings") }
                }
            }

            app.superhealth.nutrition.ui.components.GroupHeader(tr("Calculs"), Modifier.padding(start = 0.dp))
            GroupCard {
                SwitchRow(tr("Ajustement prédictif"), tr("Anticipe un changement d'objectif"), s.predictiveGoalAdjustment) { v ->
                    set { it.copy(predictiveGoalAdjustment = v) }
                }
                RowDivider()
                SwitchRow(tr("Pas dans la dépense"), tr("Réagit plus vite aux changements d'activité"), s.useSteps) { v -> set { it.copy(useSteps = v) } }
            }

            // bases où sont cherchés les aliments et les codes-barres
            app.superhealth.nutrition.ui.components.GroupHeader(tr("Bases d'aliments"), Modifier.padding(start = 0.dp))
            GroupCard {
                SwitchRow(tr("Open Food Facts"), tr("En ligne · recherche, code-barres, scores"), s.openFoodFactsEnabled) { v -> set { it.copy(openFoodFactsEnabled = v) } }
                RowDivider()
                SwitchRow(tr("Base suisse (OSAV)"), tr("Hors ligne · aliments génériques"), s.swissDbEnabled) { v -> set { it.copy(swissDbEnabled = v) } }
            }

            app.superhealth.nutrition.ui.components.GroupHeader(tr("Données"), Modifier.padding(start = 0.dp))
            GroupCard {
                SettingRow(tr("Exporter une sauvegarde"), subtitle = tr("JSON complet"), icon = Icons.Rounded.Backup) { exportJson() }
                RowDivider()
                SettingRow(tr("Restaurer une sauvegarde"), subtitle = tr("Remplace toutes les données"), icon = Icons.Rounded.Restore) { importJson() }
                RowDivider()
                SettingRow(tr("Exporter les données"), subtitle = tr("CSV (ZIP) : journal, pesées, dépense et cibles"), icon = Icons.Rounded.TableChart) { exportCsv() }
                RowDivider()
                SettingRow(tr("Importer des pesées"), subtitle = tr("CSV : date,poids[,MG]"), icon = Icons.Rounded.Upload) { importWeights() }
                RowDivider()
                SettingRow(tr("Effacer les données nutrition"), icon = Icons.Rounded.DeleteForever, danger = true) { confirmWipe = true }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false }, containerColor = Mf.colors.card,
            title = { Text(tr("Tout effacer ?")) },
            text = { Text(tr("Tout sera supprimé définitivement. Pensez à exporter une sauvegarde.")) },
            confirmButton = { TextButton(onClick = { confirmWipe = false; scope.launch { vm.repo.wipe() } }) { Text(tr("Effacer"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(tr("Annuler"), color = Mf.colors.text) } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(tr(text), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
    Spacer(Modifier.height(6.dp))
}
