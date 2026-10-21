package app.superhealth.ui.settings

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.bridge.HealthConnectHub
import app.superhealth.nutrition.data.AppJson
import app.superhealth.nutrition.data.Backup
import app.superhealth.nutrition.domain.AccentColor
import app.superhealth.nutrition.domain.EnergyUnit
import app.superhealth.nutrition.domain.Language
import app.superhealth.nutrition.domain.Sex
import app.superhealth.nutrition.domain.ThemeMode
import app.superhealth.nutrition.domain.WeightUnit
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.ScreenTitle
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.weight
import app.superhealth.training.WorkoutApp
import app.superhealth.ui.Lang
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.color
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser

/**
 * Réglages : ce qui est commun aux trois modules (profil, langue, apparence, unités, connexions, données),
 * puis une page par module pour ses réglages propres.
 */
@Composable
fun SettingsScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sh by vm.settings.collectAsState()
    val s by vm.nutrition.settings.collectAsState()
    val profile by vm.nutrition.profile.collectAsState()
    val a by vm.nutrition.analytics.collectAsState()
    val devices by vm.devices.collectAsState()
    val syncing by vm.syncing.collectAsState()
    var editName by remember { mutableStateOf(false) }
    var nameTick by remember { mutableIntStateOf(0) }
    val name = remember(nameTick) { app.superhealth.ui.userName() }
    fun set(f: (app.superhealth.nutrition.domain.Settings) -> app.superhealth.nutrition.domain.Settings) = vm.nutrition.repo.prefs.setSettings(f(s))
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    val exportAll = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { scope.launch { toast(runCatching { exportBackup(ctx, vm, it); tr("Sauvegarde exportée") }.getOrElse { e -> trf("Erreur : %1\$s", e.message) }) } }
    }
    val importAll = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { toast(runCatching { restoreBackup(ctx, vm, it) }.getOrElse { e -> trf("Erreur : %1\$s", e.message) }) } }
    }

    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        ScreenTitle(tr("Réglages"))
        Column(Modifier.padding(horizontal = 16.dp)) {
            // Profil : la seule source de sexe, âge, taille et poids pour la nutrition, la musculation et la montre.
            MfCard(onClick = { nav.go("profile") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Mf.colors.accent), contentAlignment = Alignment.Center) {
                        Text((name ?: "?").take(1).uppercase(Lang.locale), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name ?: tr("Profil"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val age = LocalDate.now().year - profile.birthYear
                        Text(listOfNotNull(
                            if (profile.sex == Sex.MALE) tr("Homme") else tr("Femme"), trf("%1\$s ans", age), "${profile.heightCm.toInt()} cm",
                            a?.currentTrendKg?.let { s.weight(it) },
                        ).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 2)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
                }
            }
            Spacer(Modifier.height(8.dp))
            GroupCard { SettingRow(tr("Prénom"), name ?: tr("Non renseigné")) { editName = true } }

            SectionLabel(tr("Langue"))
            MfCard {
                ChipChoice(listOf(Language.FR, Language.EN), if (Lang.english) Language.EN else Language.FR, { it.label }) { l -> set { it.copy(language = l) } }
            }

            SectionLabel(tr("Apparence"))
            MfCard {
                Label(tr("Thème"))
                ChipChoice(ThemeMode.entries, s.themeMode, { tr(it.label) }) { m -> set { it.copy(themeMode = m) } }
                Spacer(Modifier.height(16.dp))
                Label(tr("Couleur d'accent"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AccentColor.entries.forEach { c ->
                        val sel = c == s.accent
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c.color())
                                .border(if (sel) 3.dp else 0.dp, if (sel) Mf.colors.strong else Color.Transparent, CircleShape)
                                .clickable { set { it.copy(accent = c) } },
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Rounded.Check, tr(c.label), tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
            }

            SectionLabel(tr("Unités et objectifs"))
            MfCard {
                Label(tr("Poids"))
                ChipChoice(WeightUnit.entries, s.weightUnit, { tr(it.label) }) { u -> set { it.copy(weightUnit = u) } }
                Spacer(Modifier.height(12.dp))
                Label(tr("Énergie"))
                ChipChoice(EnergyUnit.entries, s.energyUnit, { tr(it.label) }) { u -> set { it.copy(energyUnit = u) } }
                Spacer(Modifier.height(12.dp))
                Label(tr("Objectif de pas"))
                ChipChoice(listOf(0, 6000, 8000, 10000, 12000), s.stepGoal, { if (it == 0) tr("Aucun") else formatNumber(it.toDouble()) }) { g -> set { it.copy(stepGoal = g) } }
            }

            SectionLabel(tr("Modules"))
            GroupCard {
                SettingRow(tr("Nutrition"), subtitle = tr("Programme, IA, calculs"), icon = Icons.Rounded.Restaurant) { nav.go("n/settings") }
                RowDivider()
                SettingRow(tr("Musculation"), subtitle = tr("Salles, progression, échauffements, repos"), icon = Icons.Rounded.FitnessCenter) { nav.go("t/settings") }
                RowDivider()
                SwitchRow(tr("Montre (LocalHealth)"), if (sh.watchModule) trf("%1\$s appareil(s) associé(s)", devices.size) else tr("Désactivé"), sh.watchModule) { vm.setWatchModule(it) }
                if (sh.watchModule) {
                    RowDivider()
                    SettingRow(tr("Montre et appareils"), subtitle = tr("Associer, synchroniser, réglages de la montre"), icon = Icons.Rounded.Watch) { nav.go("h/devices") }
                }
            }

            // bases où sont cherchés les aliments et les codes-barres
            SectionLabel(tr("Bases d'aliments"))
            GroupCard {
                SwitchRow(tr("Open Food Facts"), tr("En ligne · recherche, code-barres, scores"), s.openFoodFactsEnabled) { v -> set { it.copy(openFoodFactsEnabled = v) } }
                RowDivider()
                SwitchRow(tr("Base suisse (OSAV)"), tr("Hors ligne · aliments génériques"), s.swissDbEnabled) { v -> set { it.copy(swissDbEnabled = v) } }
            }

            SectionLabel(tr("Connexions"))
            HealthConnectSection(vm, syncing)

            SectionLabel(tr("Tableau de bord"))
            GroupCard {
                SettingRow(tr("Personnaliser"), subtitle = trf("%1\$s carte(s) affichée(s)", sh.dashboard.size), icon = Icons.Rounded.Dashboard) { nav.go("dash/edit") }
                RowDivider()
                SettingRow(tr("Bouton +"), subtitle = trf("%1\$s raccourci(s)", sh.plus.size), icon = Icons.Rounded.AddCircle) { nav.go("plus/edit") }
                RowDivider()
                SwitchRow(tr("Pesée du jour"), tr("Demandée à l'ouverture tant qu'elle manque"), sh.weighPrompt) { v ->
                    app.superhealth.SuperPrefs.update { it.copy(weighPrompt = v) }
                }
            }

            SectionLabel(tr("Données"))
            GroupCard {
                SettingRow(tr("Sauvegarde complète"), subtitle = tr("Nutrition, musculation, montre, réglages (un seul fichier ZIP)"), icon = Icons.Rounded.Backup) {
                    exportAll.launch("superhealth-${LocalDate.now()}.zip")
                }
                RowDivider()
                SettingRow(tr("Restaurer"), subtitle = tr("Sauvegarde SuperHealth, La Bouffe ou La Salle"), icon = Icons.Rounded.Restore) {
                    importAll.launch(arrayOf("application/zip", "application/json", "*/*"))
                }
            }
            Text(tr("Toutes les données restent sur ce téléphone : aucun compte, aucun serveur."), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))

            SectionLabel(tr("Vie privée"))
            GroupCard {
                SwitchRow(tr("Cartes en ligne"), tr("Fond de carte OpenStreetMap sous les tracés. Désactivé : tracé seul, aucune requête."),
                    sh.onlineMaps) { v -> app.superhealth.SuperPrefs.update { it.copy(onlineMaps = v) } }
            }
            Text(tr("Aucun traceur. Connexions : Open Food Facts (désactivable), l'IA si activée, et ces cartes."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))

            SectionLabel(tr("Simulateurs et questionnaire"))
            GroupCard {
                SettingRow(tr("Simuler des cibles caloriques"), subtitle = tr("Pour un ami : rien n'est enregistré"), icon = Icons.Rounded.Restaurant) { nav.go("sim/nutrition") }
                RowDivider()
                SettingRow(tr("Simuler un programme de musculation"), subtitle = tr("Générer un programme sans rien enregistrer"), icon = Icons.Rounded.FitnessCenter) { nav.go("sim/training") }
                RowDivider()
                SettingRow(tr("Refaire le questionnaire d'accueil"), subtitle = tr("Remplace votre profil et vos objectifs ; « Passer » annule"), icon = Icons.Rounded.Restore) {
                    app.superhealth.SuperPrefs.update { it.copy(onboarded = false) }
                }
            }

            SectionLabel(tr("À propos"))
            GroupCard {
                val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
                SettingRow("SuperHealth", version, subtitle = tr("Usage personnel · sans compte · données sur l'appareil"), icon = Icons.Rounded.Info)
                RowDivider()
                SettingRow(tr("Sources"), subtitle = tr("MacroFactor et MacroFactor Workouts (interface, algorithmes) · Gadgetbridge (montres, AGPL) · Open Food Facts (ODbL) · OSAV"))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editName) {
        var text by remember { mutableStateOf(name ?: "") }
        AlertDialog(
            onDismissRequest = { editName = false }, containerColor = Mf.colors.card,
            title = { Text(tr("Prénom")) },
            text = {
                OutlinedTextField(text, { text = it.take(24) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Mf.colors.accent, unfocusedBorderColor = Mf.colors.divider))
            },
            confirmButton = {
                TextButton(onClick = {
                    GBApplication.getPrefs().preferences.edit().putString(ActivityUser.PREF_USER_NAME, text.trim()).apply()
                    nameTick++; editName = false
                }) { Text(tr("Valider"), color = Mf.colors.accent) }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text(tr("Annuler"), color = Mf.colors.text) } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
    Spacer(Modifier.height(6.dp))
}

/** Health Connect pour toute l'app : une seule demande d'autorisations, une seule synchronisation. */
@Composable
private fun HealthConnectSection(vm: SuperViewModel, syncing: Boolean) {
    val ctx = LocalContext.current
    val sh by vm.settings.collectAsState()
    val status = remember { HealthConnectHub.status(ctx) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(sh.healthConnect, sh.lastHcSync) { granted = HealthConnectHub.granted(ctx) }
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { g ->
        granted = g
        if (g.isNotEmpty()) { SuperPrefs.update { it.copy(healthConnect = true) }; vm.syncNow(ctx) }
    }
    GroupCard {
        when (status) {
            HealthConnectHub.Status.UNAVAILABLE -> SettingRow(tr("Health Connect"), subtitle = tr("Non disponible sur cet appareil"), icon = Icons.Rounded.Favorite)
            HealthConnectHub.Status.NEEDS_UPDATE -> SettingRow(tr("Health Connect"), subtitle = tr("Mise à jour requise (Play Store)"), icon = Icons.Rounded.Favorite) {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")))
                }
            }
            HealthConnectHub.Status.AVAILABLE -> {
                val on = sh.healthConnect && granted.isNotEmpty()
                SwitchRow(tr("Health Connect"), tr("Pas, poids, cœur, sommeil, repas et séances"), on) { v ->
                    if (v) ask.launch(HealthConnectHub.PERMISSIONS) else SuperPrefs.update { it.copy(healthConnect = false) }
                }
                if (on) {
                    RowDivider()
                    SettingRow(tr("Synchroniser maintenant"), if (syncing) tr("En cours…") else null,
                        subtitle = sh.lastHcSummary?.let { app.superhealth.ui.relativeDay(app.superhealth.ui.localDate(sh.lastHcSync)) + " " +
                            app.superhealth.ui.timeOf(sh.lastHcSync) + " · " + it }, icon = Icons.Rounded.Sync) { vm.syncNow(ctx) }
                    if (granted.size < HealthConnectHub.PERMISSIONS.size) {
                        RowDivider()
                        SettingRow(tr("Autorisations"), subtitle = trf("%1\$s sur %2\$s accordées", granted.size, HealthConnectHub.PERMISSIONS.size)) {
                            ask.launch(HealthConnectHub.PERMISSIONS)
                        }
                    }
                    RowDivider()
                    SettingRow(tr("Ouvrir Health Connect"), subtitle = tr("Sources, priorités et données")) {
                        runCatching { ctx.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                    }
                }
            }
        }
    }
}

// ---- Sauvegarde complète ------------------------------------------------------------------------------

/** Exécute une tâche de sauvegarde Gadgetbridge (synchrone) et lève une erreur si elle échoue. */
private fun runGbJob(make: (nodomain.freeyourgadget.gadgetbridge.util.backup.ZipBackupCallback) -> Runnable) {
    var error: String? = null
    var ok = false
    make(object : nodomain.freeyourgadget.gadgetbridge.util.backup.ZipBackupCallback {
        override fun onProgress(progress: Int, message: String?) {}
        override fun onSuccess(warnings: String?) { ok = true }
        override fun onFailure(errorMessage: String?) { error = errorMessage }
    }).run()
    if (!ok) error(error ?: "montre")
}

@kotlinx.serialization.Serializable
private data class LogRow(val metric: String, val ts: Long, val value: Double)

/**
 * Une seule archive pour tout : nutrition, musculation, réglages SuperHealth (tableau de bord, bouton +…), saisies santé
 * (hydratation, glycémie…) et, dans « montre.zip », la sauvegarde Gadgetbridge (base de la montre, réglages des appareils,
 * fichiers .fit des activités).
 */
private suspend fun exportBackup(ctx: Context, vm: SuperViewModel, uri: Uri) {
    val nutrition = AppJson.encodeToString(Backup.serializer(), vm.nutrition.repo.backup())
    val training = WorkoutApp.store.export()
    val logs = app.superhealth.health.data.ManualLogStore.get(ctx).query(null, 0L, Long.MAX_VALUE).map { LogRow(it.metric.name, it.timestampMs, it.value) }
    val health = AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(LogRow.serializer()), logs)
    withContext(Dispatchers.IO) {
        val watch = java.io.File(ctx.cacheDir, "montre-backup.zip").apply { delete() }
        runCatching { runGbJob { cb -> nodomain.freeyourgadget.gadgetbridge.util.backup.ZipBackupExportJob(ctx, cb, Uri.fromFile(watch)) } }
        ZipOutputStream(ctx.contentResolver.openOutputStream(uri, "wt")!!.buffered()).use { zip ->
            listOf("nutrition.json" to nutrition, "musculation.json" to training, "superhealth.json" to app.superhealth.SuperPrefs.exportSettings(),
                "sante.json" to health).forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            if (watch.length() > 0) { zip.putNextEntry(ZipEntry("montre.zip")); watch.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
        }
        watch.delete()
    }
}

/** ZIP SuperHealth, ou sauvegarde JSON de La Bouffe (nutrition) / La Salle (musculation). */
private suspend fun restoreBackup(ctx: Context, vm: SuperViewModel, uri: Uri): String {
    val watch = java.io.File(ctx.cacheDir, "montre-restore.zip").apply { delete() }
    val files = withContext(Dispatchers.IO) {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        if (bytes.size > 1 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            buildMap {
                ZipInputStream(bytes.inputStream()).use { zip ->
                    generateSequence { zip.nextEntry }.forEach { e ->
                        if (e.name == "montre.zip") watch.outputStream().use { zip.copyTo(it) } else put(e.name, zip.readBytes().decodeToString())
                    }
                }
            }
        } else {
            val text = bytes.decodeToString()
            if ("\"foods\"" in text && "\"entries\"" in text) mapOf("nutrition.json" to text) else mapOf("musculation.json" to text)
        }
    }
    val done = mutableListOf<String>()
    files["nutrition.json"]?.let { vm.nutrition.repo.restore(AppJson.decodeFromString(Backup.serializer(), it)); done += tr("nutrition") }
    files["musculation.json"]?.let { WorkoutApp.store.import(it); done += tr("musculation") }
    files["superhealth.json"]?.let { app.superhealth.SuperPrefs.importSettings(it); done += tr("réglages") }
    files["sante.json"]?.let { json ->
        val rows = AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(LogRow.serializer()), json)
        app.superhealth.health.data.ManualLogStore.get(ctx).replaceAll(rows.mapNotNull { r ->
            runCatching { app.superhealth.health.data.Metric.valueOf(r.metric) }.getOrNull()?.let { app.superhealth.health.data.ManualLog(0, it, r.ts, r.value) }
        })
        done += tr("saisies santé")
    }
    if (watch.length() > 0) {
        val ok = withContext(Dispatchers.IO) {
            runCatching { runGbJob { cb -> nodomain.freeyourgadget.gadgetbridge.util.backup.ZipBackupImportJob(ctx, cb, Uri.fromFile(watch)) } }.isSuccess
        }
        watch.delete()
        app.superhealth.health.data.HealthRepository.invalidate()
        if (ok) done += tr("montre") else done += tr("montre (échec)")
    }
    vm.bump()
    return if (done.isEmpty()) tr("Fichier illisible") else trf("Restauré : %1\$s", done.joinToString(", "))
}
