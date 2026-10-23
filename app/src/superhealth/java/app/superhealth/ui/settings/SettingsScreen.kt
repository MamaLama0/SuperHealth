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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser

/**
 * Réglages : une page d'accueil courte, en groupes (accueil, suivi, général, données, outils, à propos), chaque ligne
 * menant à sa page ; seuls les interrupteurs simples restent sur place.
 */
@Composable
fun SettingsScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val sh by vm.settings.collectAsState()
    val s by vm.nutrition.settings.collectAsState()
    val profile by vm.nutrition.profile.collectAsState()
    val a by vm.nutrition.analytics.collectAsState()
    val devices by vm.devices.collectAsState()
    var editName by remember { mutableStateOf(false) }
    var nameTick by remember { mutableIntStateOf(0) }
    val name = remember(nameTick) { app.superhealth.ui.userName() }

    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        ScreenTitle(tr("Réglages"))
        Column(Modifier.padding(horizontal = 16.dp)) {
            // Profil : la seule source de sexe, âge, taille et poids pour la nutrition, la musculation et la montre.
            MfCard(onClick = { nav.go("profile") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Mf.colors.accent), contentAlignment = Alignment.Center) {
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
                    IconButton(onClick = { editName = true }) { Icon(Icons.Rounded.Edit, tr("Prénom"), tint = Mf.colors.textSecondary) }
                }
            }

            Group(tr("Accueil")) {
                SettingRow(tr("Cartes"), trf("%1\$s affichées", sh.dashboard.size), icon = Icons.Rounded.Dashboard, hue = HealthHues.Body) { nav.go("dash/edit") }
                RowDivider()
                SettingRow(tr("Bouton +"), trf("%1\$s raccourcis", sh.plus.size), icon = Icons.Rounded.AddCircle, hue = HealthHues.Activity) { nav.go("plus/edit") }
                RowDivider()
                IconSwitchRow(tr("Bandeau de pesée"), tr("Tant que la pesée du jour manque"), Icons.Rounded.MonitorWeight, HealthHues.Body, sh.weighPrompt) { v ->
                    SuperPrefs.update { it.copy(weighPrompt = v) }
                }
            }

            Group(tr("Fonctions")) {
                SettingRow(tr("Nutrition"), subtitle = tr("Programme, IA, calculs, bases d'aliments"), icon = Icons.Rounded.Restaurant, hue = HealthHues.Mind) { nav.go("n/settings") }
                RowDivider()
                SettingRow(tr("Musculation"), subtitle = tr("Salles, progression, échauffements, repos"), icon = Icons.Rounded.FitnessCenter, hue = HealthHues.Fitness) { nav.go("t/settings") }
                RowDivider()
                IconSwitchRow(tr("Montre"), if (sh.watchModule) trf("%1\$s appareil(s)", devices.size) else tr("Désactivée"), Icons.Rounded.Watch, HealthHues.Respiration,
                    sh.watchModule, onClick = { nav.go("h/devices") }) { vm.setWatchModule(it) }
                RowDivider()
                ModulesRow(nav)
            }

            Group(tr("Général")) {
                SettingRow(tr("Apparence et langue"), subtitle = "${tr(s.themeMode.label)} · ${s.accent.let { tr(it.label) }} · ${if (Lang.english) "English" else "Français"}",
                    icon = Icons.Rounded.Palette, hue = Mf.colors.accent) { nav.go("settings/look") }
                RowDivider()
                SettingRow(tr("Unités et objectifs"), "${tr(s.weightUnit.label)} · ${tr(s.energyUnit.label)}",
                    icon = Icons.Rounded.Straighten, hue = HealthHues.Activity) { nav.go("settings/units") }
            }

            Group(tr("Données")) {
                SettingRow(tr("Health Connect"), if (sh.healthConnect) tr("Activé") else tr("Désactivé"), icon = Icons.Rounded.Favorite, hue = HealthHues.Heart) {
                    nav.go("settings/hc")
                }
                RowDivider()
                SettingRow(tr("Sauvegarde"), subtitle = when {
                    sh.lastBackupError != null -> trf("Échec : %1\$s", sh.lastBackupError)
                    sh.lastBackup > 0 -> trf("Dernière %1\$s", app.superhealth.ui.relativeDay(app.superhealth.ui.localDate(sh.lastBackup)).lowercase(Lang.locale))
                    else -> tr("Chiffrée, vers un fichier, Nextcloud ou WebDAV")
                }, icon = Icons.Rounded.Backup, hue = HealthHues.Respiration) { nav.go("backup") }
                RowDivider()
                IconSwitchRow(tr("Cartes en ligne"), tr("Fond OpenStreetMap sous les tracés"), Icons.Rounded.Map, HealthHues.Activity, sh.onlineMaps) { v ->
                    SuperPrefs.update { it.copy(onlineMaps = v) }
                }
            }
            Note(tr("Aucun compte, aucun traceur. Les sauvegardes ne quittent le téléphone que chiffrées."))

            Group(tr("Outils")) {
                SettingRow(tr("Simuler des cibles caloriques"), subtitle = tr("Pour un ami, rien n'est enregistré"), icon = Icons.Rounded.Calculate, hue = HealthHues.Mind) { nav.go("sim/nutrition") }
                RowDivider()
                SettingRow(tr("Simuler un programme"), subtitle = tr("Musculation, rien n'est enregistré"), icon = Icons.Rounded.FitnessCenter, hue = HealthHues.Fitness) { nav.go("sim/training") }
                RowDivider()
                SettingRow(tr("Refaire le questionnaire"), subtitle = tr("« Passer » annule"), icon = Icons.Rounded.Restore) {
                    SuperPrefs.update { it.copy(onboarded = false) }
                }
            }

            Group(tr("À propos")) {
                VersionRow(nav)
                RowDivider()
                IconSwitchRow(tr("Vérifier chaque jour"), tr("Une requête par jour à GitHub"), Icons.Rounded.Autorenew, null, sh.updateCheck) { v ->
                    SuperPrefs.update { it.copy(updateCheck = v) }
                }
                RowDivider()
                SettingRow(tr("Sources"), subtitle = tr("Gadgetbridge (AGPL) · Open Food Facts (ODbL) · OSAV · Kenney (CC0)"), icon = Icons.Rounded.Info)
                RowDivider()
                // attribution exigée par la licence gratuite de RepDB (images d'exercices), avec un lien visible
                SettingRow(tr("Images d'exercices"), subtitle = "Exercise data by RepDB (repdb.co)", icon = Icons.Rounded.FitnessCenter) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://repdb.co"))) }
                }
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

/** Apparence et langue : thème, couleur d'accent, langue de l'app. */
@Composable
fun LookSettingsScreen(vm: SuperViewModel, nav: NavHostController) {
    val s by vm.nutrition.settings.collectAsState()
    fun set(f: (app.superhealth.nutrition.domain.Settings) -> app.superhealth.nutrition.domain.Settings) = vm.nutrition.repo.prefs.setSettings(f(s))
    DetailScaffold(tr("Apparence et langue"), onBack = { nav.popBackStack() }) {
        MfCard {
            Label(tr("Thème"))
            ChipChoice(ThemeMode.entries, s.themeMode, { tr(it.label) }) { m -> set { it.copy(themeMode = m) } }
            Spacer(Modifier.height(18.dp))
            Label(tr("Couleur d'accent"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AccentColor.entries.forEach { c ->
                    val sel = c == s.accent
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c.color())
                            .border(if (sel) 3.dp else 0.dp, if (sel) Mf.colors.strong else Color.Transparent, CircleShape)
                            .clickable { set { it.copy(accent = c) } },
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Icon(Icons.Rounded.Check, tr(c.label), tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MfCard {
            Label(tr("Langue"))
            ChipChoice(listOf(Language.FR, Language.EN), if (Lang.english) Language.EN else Language.FR, { it.label }) { l -> set { it.copy(language = l) } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Unités et objectifs : poids, énergie, objectif de pas. */
@Composable
fun UnitsSettingsScreen(vm: SuperViewModel, nav: NavHostController) {
    val s by vm.nutrition.settings.collectAsState()
    fun set(f: (app.superhealth.nutrition.domain.Settings) -> app.superhealth.nutrition.domain.Settings) = vm.nutrition.repo.prefs.setSettings(f(s))
    DetailScaffold(tr("Unités et objectifs"), onBack = { nav.popBackStack() }) {
        MfCard {
            Label(tr("Poids"))
            ChipChoice(WeightUnit.entries, s.weightUnit, { tr(it.label) }) { u -> set { it.copy(weightUnit = u) } }
            Spacer(Modifier.height(14.dp))
            Label(tr("Énergie"))
            ChipChoice(EnergyUnit.entries, s.energyUnit, { tr(it.label) }) { u -> set { it.copy(energyUnit = u) } }
        }
        Spacer(Modifier.height(12.dp))
        MfCard {
            Label(tr("Objectif de pas"))
            StepGoalEditor(s) { f -> set(f) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Health Connect : activation, synchronisation, autorisations. */
@Composable
fun HealthConnectSettingsScreen(vm: SuperViewModel, nav: NavHostController) {
    val syncing by vm.syncing.collectAsState()
    DetailScaffold(tr("Health Connect"), onBack = { nav.popBackStack() }) {
        HealthConnectSection(vm, syncing)
        Spacer(Modifier.height(24.dp))
    }
}

/** Groupe de la page d'accueil des réglages : petit intitulé, puis une carte de lignes. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Mf.colors.textSecondary,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
    GroupCard(content = content)
}

@Composable
private fun Note(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp))

/**
 * Interrupteur avec pastille d'icône, aligné sur les autres lignes. Avec [onClick], le texte ouvre la page du réglage quand
 * il est actif (montre → appareils) ; sinon tout appui bascule l'interrupteur.
 */
@Composable
private fun IconSwitchRow(title: String, value: String, icon: ImageVector, hue: Color?, checked: Boolean, onClick: (() -> Unit)? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { if (checked && onClick != null) onClick() else onChange(!checked) }.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(hue?.container() ?: Mf.colors.chip), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = hue?.onContainer() ?: Mf.colors.text)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Mf.colors.accent, checkedThumbColor = Color.White,
            uncheckedBorderColor = Color.Transparent, uncheckedTrackColor = Mf.colors.chip))
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

