package app.superhealth.nutrition.ui.screens

import app.superhealth.nutrition.data.usdaOn
import androidx.compose.material.icons.rounded.Add
import app.superhealth.nutrition.ui.trf
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.CalendarMonth
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
import androidx.compose.ui.platform.LocalContext
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
import androidx.health.connect.client.PermissionController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.SuperPrefs
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
import java.time.LocalDate

private suspend fun write(ctx: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
}

private suspend fun writeZip(ctx: Context, uri: Uri, files: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
    java.util.zip.ZipOutputStream(ctx.contentResolver.openOutputStream(uri, "wt")!!).use { zip ->
        files.forEach { (name, text) -> zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
    }
}

private suspend fun read(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
}

@Composable
fun MoreScreen(vm: AppViewModel, nav: NavHostController, section: String? = null) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmWipe by remember { mutableStateOf(false) }
    val stamp = LocalDate.now().toString()

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    fun safely(block: suspend () -> String) = scope.launch { toast(runCatching { block() }.getOrElse { trf("Erreur : %1\$s", it.message) }) }
    fun set(f: (app.superhealth.nutrition.domain.Settings) -> app.superhealth.nutrition.domain.Settings) = vm.repo.prefs.setSettings(f(s))

    // un seul export : archive ZIP avec le journal, les pesées et tendance, la dépense et les cibles
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { safely {
            val an = vm.analytics.value!!
            writeZip(ctx, it, listOf("journal.csv" to Csv.foodLog(vm.repo.entries.all()), "pesees.csv" to Csv.weights(an), "depense.csv" to Csv.expenditure(an)))
            tr("Données exportées")
        } }
    }
    val importWeights = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { safely { val rows = Csv.parseWeights(read(ctx, it)); vm.repo.weighIns.upsertAll(rows); trf("%1\$s pesée(s) importée(s)", rows.size) } }
    }

    val title = tr(when (section) { "program" -> "Programme et objectifs"; "daily" -> "Journal et repas"; "calculations" -> "Calculs et dépense"; "food" -> "Aliments et IA"; "data" -> "Données nutrition"; else -> "Nutrition" })
    DetailScaffold(title, onBack = { nav.popBackStack() }) {
        Column {
            if (section == null) {

            app.superhealth.ui.settings.GuidedSetupCard(tr("Ma nutrition, pas à pas"), tr("Objectif, calories et macros : un parcours adapté à vos choix.")) { nav.navigate("setup/nutrition") }
            app.superhealth.nutrition.ui.components.GroupHeader(tr("Mes réglages"))
            GroupCard {
                SettingRow(tr("Programme et objectifs"), subtitle = tr("Mode, poids, cibles et remplacements")) { nav.navigate("n/settings/program") }
                RowDivider()
                SettingRow(tr("Journal et repas"), subtitle = tr("Recettes, habitudes, horaires et pesées")) { nav.navigate("n/settings/daily") }
                RowDivider()
                SettingRow(tr("Calculs et dépense"), subtitle = tr("Ajustements et activité quotidienne")) { nav.navigate("n/settings/calculations") }
                RowDivider()
                SettingRow(tr("Aliments et IA"), subtitle = tr("Bases de recherche et saisie assistée")) { nav.navigate("n/settings/food") }
                RowDivider()
                SettingRow(tr("Données nutrition"), subtitle = tr("Exports CSV, pesées et effacement")) { nav.navigate("n/settings/data") }
            }
            }
            if (section == "program") {

            GroupCard {
                SettingRow(tr("Programme et objectif"), icon = Icons.Rounded.Flag) { nav.navigate("strategy") }
                RowDivider()
                SettingRow(tr("Cibles manuelles et répartition"), subtitle = tr("Calories, macros, jours personnalisés")) { nav.navigate("program") }
                RowDivider()
                SettingRow(tr("Remplacer mes cibles"), subtitle = tr("Aujourd'hui, 7 jours ou sans date de fin")) { nav.navigate("targets/override") }


            }
            }
            if (section == "daily") {
            // réglages du quotidien, avec le programme : horaires des repas, pesée du matin, jour du check-in
            app.superhealth.nutrition.ui.components.GroupHeader(tr("Quotidien"), Modifier.padding(start = 0.dp))
            val sh = SuperPrefs.settings.collectAsState().value
            val program = vm.program.collectAsState().value
            GroupCard {
                SwitchRow(tr("Résumé du journal replié"), tr("Touchez le résumé pour le replier ou le déplier"), s.foodSummaryCollapsed) { v -> set { it.copy(foodSummaryCollapsed = v) } }
            }
            Spacer(Modifier.height(8.dp))
            GroupCard {
                SettingRow(tr("Recettes"), icon = Icons.AutoMirrored.Rounded.MenuBook) { nav.navigate("search/5/-1") }
                RowDivider()
                SettingRow(tr("Habitudes"), icon = Icons.Rounded.EventAvailable) { nav.navigate("habits") }
                RowDivider()
                SettingRow(tr("Mesures et photos"), icon = Icons.Rounded.Straighten) { nav.navigate("body") }
            }
            GroupCard {
                var hours by remember { mutableStateOf(false) }
                SettingRow(tr("Horaires des repas"), sh.mealEnds.joinToString(" · ") { "%d:%02d".format(it / 60, it % 60) }, icon = Icons.Rounded.Schedule) { hours = true }
                if (hours) MealHoursDialog { hours = false }
                RowDivider()
                SwitchRow(tr("Bandeau de pesée"), tr("Tant que la pesée du jour manque"), sh.weighPrompt) { v -> SuperPrefs.update { it.copy(weighPrompt = v) } }
                RowDivider()
                app.superhealth.ui.wellbeing.WeighPromptTimeRow()
                if (program.style != app.superhealth.nutrition.domain.ProgramStyle.MANUAL) {
                RowDivider()
                var checkInDayOpen by remember { mutableStateOf(false) }
                SettingRow(tr("Jour du check-in"), checkInDayName(program.checkInDay), subtitle = tr("Poids analysé et calories recalculées ce jour-là"), icon = Icons.Rounded.CalendarMonth) { checkInDayOpen = true }
                if (checkInDayOpen) CheckInDayDialog(program.checkInDay, onDismiss = { checkInDayOpen = false }) { d ->
                    checkInDayOpen = false
                    vm.repo.prefs.setProgram(program.copy(checkInDay = d))
                }
                }
            }

            }
            if (section == "food") {
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

            // bases où sont cherchés les aliments et les codes-barres
            app.superhealth.nutrition.ui.components.GroupHeader(tr("Bases d'aliments"), Modifier.padding(start = 0.dp))
            GroupCard {
                SwitchRow(tr("Open Food Facts"), tr("En ligne · recherche, code-barres, scores"), s.openFoodFactsEnabled) { v -> set { it.copy(openFoodFactsEnabled = v) } }
                RowDivider()
                SwitchRow(tr("Base suisse (OSAV)"), tr("Hors ligne · aliments génériques"), s.swissDbEnabled) { v -> set { it.copy(swissDbEnabled = v) } }
                RowDivider()
                SwitchRow(tr("Base américaine (USDA)"), tr("Hors ligne · aliments génériques en anglais"), s.usdaOn) { v -> set { it.copy(usdaDbEnabled = v) } }
            }

            }
            if (section == "calculations") {
            app.superhealth.nutrition.ui.components.GroupHeader(tr("Calculs"), Modifier.padding(start = 0.dp))
            GroupCard {
                SwitchRow(tr("Ajustement prédictif"), tr("Anticipe un changement d'objectif"), s.predictiveGoalAdjustment) { v ->
                    set { it.copy(predictiveGoalAdjustment = v) }
                }
                RowDivider()
                SwitchRow(tr("Poids connu au jour du calcul"), tr("Estime la dépense de chaque jour avec les pesées disponibles à cette date"), s.useFilteredWeightForExpenditure) { v ->
                    set { it.copy(useFilteredWeightForExpenditure = v) }
                }
                RowDivider()
                SwitchRow(tr("Pas dans la dépense"), tr("Réagit plus vite aux changements d'activité"), s.useSteps) { v -> set { it.copy(useSteps = v) } }
            }

            }
            if (section == "data") {
            app.superhealth.nutrition.ui.components.GroupHeader(tr("Données"), Modifier.padding(start = 0.dp))
            GroupCard {
                SettingRow(tr("Sauvegarde"), subtitle = tr("Une seule sauvegarde chiffrée pour toute l'app"), icon = Icons.Rounded.Backup) { nav.navigate("backup") }
                RowDivider()
                SettingRow(tr("Exporter les données"), subtitle = tr("CSV (ZIP) : journal, pesées, dépense et cibles"), icon = Icons.Rounded.TableChart) { exportCsv.launch("superhealth-nutrition-${stamp}.zip") }
                RowDivider()
                SettingRow(tr("Importer des pesées"), subtitle = tr("CSV : date,poids[,MG]"), icon = Icons.Rounded.Upload) { importWeights.launch(arrayOf("text/*", "*/*")) }
                RowDivider()
                SettingRow(tr("Effacer les données nutrition"), icon = Icons.Rounded.DeleteForever, danger = true) { confirmWipe = true }
            }

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

/** Version affichée (versionName de l'APK). */
@Composable
private fun appVersion(): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
}

/** Jour du check-in : le poids est analysé et les calories recalculées ce jour-là. */
@Composable
private fun CheckInDayDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var pick by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Mf.colors.card,
        title = { Text(tr("Jour du check-in")) },
        text = {
            Column {
                Text(tr("Ce jour-là, votre poids est analysé et vos calories recalculées pour la semaine."),
                    style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
                Spacer(Modifier.height(14.dp))
                app.superhealth.nutrition.ui.components.ChipChoice((1..7).toList(), pick, { checkInDayName(it) }) { pick = it }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(pick) }) { Text(tr("OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuler")) } })
}
