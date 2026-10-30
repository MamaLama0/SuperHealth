package app.superhealth.ui.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lock
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
import app.superhealth.has
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
    val sh by vm.settings.collectAsState()
    val s by vm.nutrition.settings.collectAsState()
    val profile by vm.nutrition.profile.collectAsState()
    val a by vm.nutrition.analytics.collectAsState()
    val devices by vm.devices.collectAsState()
    var editName by remember { mutableStateOf(false) }
    var nameTick by remember { mutableIntStateOf(0) }
    val name = remember(nameTick) { app.superhealth.ui.userName() }

    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        // onglet de la barre (« + » au milieu) : pas de retour
        ScreenTitle(tr("Réglages"), onBack = if (app.superhealth.ui.settingsIsTab(sh)) null else ({ nav.popBackStack() }))
        Column(Modifier.padding(horizontal = 16.dp)) {
            // Profil : la seule source de sexe, âge, taille et poids pour la nutrition, la musculation et la montre.
            MfCard(onClick = { nav.go("profile") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Mf.colors.chip), contentAlignment = Alignment.Center) {
                        Text((name ?: "?").take(1).uppercase(Lang.locale), style = MaterialTheme.typography.titleLarge, color = Mf.colors.text)
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
                RowDivider(56.dp)
                SettingRow(tr("Bouton +"), trf("%1\$s raccourcis", sh.plus.size), icon = Icons.Rounded.AddCircle, hue = HealthHues.Activity) { nav.go("plus/edit") }
                RowDivider(56.dp)
                IconSwitchRow(tr("Bandeau de pesée"), tr("Tant que la pesée du jour manque"), Icons.Rounded.MonitorWeight, HealthHues.Body, sh.weighPrompt) { v ->
                    SuperPrefs.update { it.copy(weighPrompt = v) }
                }
            }

            Group(tr("Fonctions")) {
                // chaque partie se coupe d'un geste (onglets, cartes et « + » suivent) ; toucher la ligne ouvre ses réglages
                fun toggle(f: app.superhealth.Feature, on: Boolean) = SuperPrefs.update { it.copy(features = if (on) it.features + f.name else it.features - f.name) }
                IconSwitchRow(tr("Nutrition"), if (sh.has(app.superhealth.Feature.NUTRITION)) tr("Journal, calories, poids") else tr("Désactivée"),
                    Icons.Rounded.Restaurant, HealthHues.Mind, sh.has(app.superhealth.Feature.NUTRITION), onClick = { nav.go("n/settings") }) { toggle(app.superhealth.Feature.NUTRITION, it) }
                RowDivider(56.dp)
                IconSwitchRow(tr("Musculation"), if (sh.has(app.superhealth.Feature.TRAINING)) tr("Programmes et séances") else tr("Désactivée"),
                    Icons.Rounded.FitnessCenter, HealthHues.Fitness, sh.has(app.superhealth.Feature.TRAINING), onClick = { nav.go("t/settings") }) { toggle(app.superhealth.Feature.TRAINING, it) }
                RowDivider(56.dp)
                // activités et montre : couper la partie coupe aussi le module montre (connexions Bluetooth)
                IconSwitchRow(tr("Activités et montre"), if (!sh.has(app.superhealth.Feature.HEALTH)) tr("Désactivée") else if (sh.watchModule) trf("%1\$s appareil(s)", devices.size) else tr("Montre désactivée"),
                    Icons.Rounded.Watch, HealthHues.Respiration, sh.has(app.superhealth.Feature.HEALTH), onClick = { nav.go("h/devices") }) { on ->
                    toggle(app.superhealth.Feature.HEALTH, on); vm.setWatchModule(on)
                }
                RowDivider(56.dp)
                ModulesRow(nav)
            }

            // bien-être à part : journal du moral et ressenti du matin réunis (le ressenti se règle dans le journal), cycle, rappels
            Group(tr("Bien-être")) {
                fun toggle(f: app.superhealth.Feature, on: Boolean) = SuperPrefs.update { it.copy(features = if (on) it.features + f.name else it.features - f.name) }
                IconSwitchRow(tr("Journal du moral"), if (!sh.has(app.superhealth.Feature.JOURNAL)) tr("Désactivé")
                    else if (sh.morningCheck) tr("Humeur, notes et ressenti du matin") else tr("Humeur et notes"),
                    Icons.Rounded.SentimentSatisfied, HealthHues.Mind, sh.has(app.superhealth.Feature.JOURNAL), onClick = { nav.go("journal") }) {
                    toggle(app.superhealth.Feature.JOURNAL, it)
                }
                // cycle : proposé selon le sexe déclaré dans le profil (gardé s'il est déjà activé)
                if (sh.has(app.superhealth.Feature.CYCLE) || app.superhealth.nutrition.NutriApp.repository.prefs.profile.collectAsState().value.sex ==
                    app.superhealth.nutrition.domain.Sex.FEMALE) {
                RowDivider(56.dp)
                IconSwitchRow(tr("Cycle menstruel"), if (sh.has(app.superhealth.Feature.CYCLE)) tr("Règles et phases") else tr("Désactivé"),
                    Icons.Rounded.CalendarMonth, app.superhealth.ui.wellbeing.CycleHue, sh.has(app.superhealth.Feature.CYCLE), onClick = { nav.go("cycle") }) { on ->
                    toggle(app.superhealth.Feature.CYCLE, on)
                    // la carte arrive avec la fonction (retirable ensuite dans Cartes)
                    if (on) SuperPrefs.update { s -> if (app.superhealth.DashCard.CYCLE in s.dashboard) s else s.copy(dashboard = s.dashboard + app.superhealth.DashCard.CYCLE,
                        plus = (s.plus + app.superhealth.PlusAction.PERIOD.name).distinct()) }
                }
                }
                RowDivider(56.dp)
                SettingRow(tr("Sommeil"), subtitle = tr("Fenêtre d'endormissement, comparaison aux habitudes"), icon = Icons.Rounded.Bedtime, hue = HealthHues.Sleep) {
                    nav.go("settings/sleep")
                }
                RowDivider(56.dp)
                SettingRow(tr("Rappels"), subtitle = tr("Pesée, journal, moral, coucher"), icon = Icons.Rounded.NotificationsActive, hue = HealthHues.Sleep) {
                    nav.go("settings/reminders")
                }
            }

            Group(tr("Général")) {
                SettingRow(tr("Apparence et langue"), subtitle = "${tr(s.themeMode.label)} · ${s.accent.let { tr(it.label) }} · ${if (Lang.english) "English" else "Français"}",
                    icon = Icons.Rounded.Palette, hue = Mf.colors.accent) { nav.go("settings/look") }
                RowDivider(56.dp)
                SettingRow(tr("Unités et objectifs"), "${tr(s.weightUnit.label)} · ${tr(s.energyUnit.label)}",
                    icon = Icons.Rounded.Straighten, hue = HealthHues.Activity) { nav.go("settings/units") }
                RowDivider(56.dp)
                SettingRow(tr("Sécurité"), if (sh.appLock) tr("Verrouillage par empreinte ou code") else tr("Verrouillage désactivé"),
                    icon = androidx.compose.material.icons.Icons.Rounded.Lock, hue = HealthHues.Respiration) { nav.go("settings/security") }
            }

            Group(tr("Données")) {
                SettingRow(tr("Health Connect"), if (sh.healthConnect) tr("Activé") else tr("Désactivé"), icon = Icons.Rounded.Favorite, hue = HealthHues.Heart) {
                    nav.go("settings/hc")
                }
                RowDivider(56.dp)
                SettingRow(tr("Import d'activités"), tr("Fichiers GPX, TCX, FIT ou ZIP, historique de la montre"), icon = Icons.Rounded.FileOpen,
                    hue = HealthHues.Fitness) { nav.go("settings/activities") }
                RowDivider(56.dp)
                SettingRow(tr("Sauvegarde"), subtitle = when {
                    sh.lastBackupError != null -> trf("Échec : %1\$s", sh.lastBackupError)
                    sh.lastBackup > 0 -> trf("Dernière %1\$s", app.superhealth.ui.relativeDay(app.superhealth.ui.localDate(sh.lastBackup)).lowercase(Lang.locale))
                    else -> tr("Chiffrée, vers un fichier, Nextcloud ou WebDAV")
                }, icon = Icons.Rounded.Backup, hue = HealthHues.Respiration) { nav.go("backup") }
                RowDivider(56.dp)
                IconSwitchRow(tr("Cartes en ligne"), tr("Fond OpenStreetMap sous les tracés"), Icons.Rounded.Map, HealthHues.Activity, sh.onlineMaps) { v ->
                    SuperPrefs.update { it.copy(onlineMaps = v) }
                }
            }
            Note(tr("Aucun compte, aucun traceur. Les sauvegardes ne quittent le téléphone que chiffrées."))

            Group(tr("Outils")) {
                SettingRow(tr("Simuler des cibles caloriques"), icon = Icons.Rounded.Calculate, hue = HealthHues.Mind) { nav.go("sim/nutrition") }
                RowDivider(56.dp)
                SettingRow(tr("Simuler un programme"), icon = Icons.Rounded.FitnessCenter, hue = HealthHues.Fitness) { nav.go("sim/training") }
                RowDivider(56.dp)
                SettingRow(tr("Refaire le questionnaire"), subtitle = tr("« Passer » annule"), icon = Icons.Rounded.Restore, hue = app.superhealth.ui.theme.Base.Grey) {
                    SuperPrefs.update { it.copy(onboarded = false) }
                }
            }

            Group(tr("À propos")) {
                VersionRow(nav)
                RowDivider(56.dp)
                IconSwitchRow(tr("Vérifier chaque jour"), tr("Une requête par jour à GitHub"), Icons.Rounded.Autorenew, null, sh.updateCheck) { v ->
                    SuperPrefs.update { it.copy(updateCheck = v) }
                }
                RowDivider(56.dp)
                SettingRow(tr("Sources"), subtitle = tr("Gadgetbridge (AGPL) · Open Food Facts (ODbL) · OSAV"), icon = Icons.Rounded.Info, hue = app.superhealth.ui.theme.Base.Grey)
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
                        // pastille de couleur ; la choisie est cerclée de sa couleur, avec un liseré
                        Modifier.size(38.dp).border(if (sel) 2.5.dp else 0.dp, if (sel) c.color() else Color.Transparent, CircleShape).padding(4.dp)
                            .clip(CircleShape).background(c.color()).clickable { set { it.copy(accent = c) } },
                        contentAlignment = Alignment.Center,
                    ) {}
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MfCard {
            Label(tr("Taille du texte"))
            ChipChoice(app.superhealth.nutrition.domain.TextSize.entries, s.textSize, { tr(it.label) }) { t -> set { it.copy(textSize = t) } }
            Spacer(Modifier.height(14.dp))
            Text(tr("Aperçu : 2 140 kcal, 150 g de protéines."), style = MaterialTheme.typography.bodyLarge, color = Mf.colors.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        MfCard {
            // place du « + » dans la barre du bas (au milieu, Réglages devient un onglet si le nombre d'onglets le permet)
            Label(tr("Bouton +"))
            val sh by SuperPrefs.settings.collectAsState()
            val sides = listOf("LEFT", "CENTER", "RIGHT")
            ChipChoice(sides, sh.plusSide.takeIf { it in sides } ?: "RIGHT", { tr(when (it) { "LEFT" -> "Gauche"; "CENTER" -> "Milieu"; else -> "Droite" }) }) { v ->
                SuperPrefs.update { it.copy(plusSide = v) }
            }
        }
        Spacer(Modifier.height(12.dp))
        MfCard {
            Label(tr("Langue"))
            ChipChoice(listOf(Language.FR, Language.EN), if (Lang.english) Language.EN else Language.FR, { it.label }) { l -> set { it.copy(language = l) } }
        }
        Spacer(Modifier.height(12.dp))
        // style des cartes (du plus neutre au plus vibrant, en clair et en sombre) et de la musculation
        app.superhealth.nutrition.ui.components.GroupCard {
            SettingRow(tr("Style"), subtitle = tr("Cartes et musculation, en clair et en sombre"), icon = Icons.Rounded.Palette, hue = Mf.colors.accent) {
                nav.go("settings/style")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Réglages > Sommeil : les ajouts de la 2.0, chacun désactivable. */
@Composable
fun SleepSettingsScreen(nav: NavHostController) {
    val sh by SuperPrefs.settings.collectAsState()
    DetailScaffold(tr("Sommeil"), onBack = { nav.popBackStack() }) {
        app.superhealth.nutrition.ui.components.GroupCard {
            app.superhealth.nutrition.ui.components.SwitchRow(tr("Fenêtre d'endormissement"), tr("Sur le Coucher conseillé : l'heure où vous vous endormez le mieux"), sh.sleepGate) { v ->
                SuperPrefs.update { it.copy(sleepGate = v) }
            }
            RowDivider(16.dp)
            app.superhealth.nutrition.ui.components.SwitchRow(tr("Comparaison aux habitudes"), tr("Sur l'écran Sommeil : durée, efficacité, sommeil réparateur et éveils"), sh.sleepCompare) { v ->
                SuperPrefs.update { it.copy(sleepCompare = v) }
            }
        }
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
    app.superhealth.nutrition.ui.components.GroupHeader(title)
    GroupCard(content = content)
}

@Composable
private fun Note(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

/**
 * Interrupteur avec pastille d'icône, aligné sur les autres lignes. Avec [onClick], le texte ouvre la page du réglage quand
 * il est actif (montre → appareils) ; sinon tout appui bascule l'interrupteur.
 */
@Composable
private fun IconSwitchRow(title: String, value: String, icon: ImageVector, hue: Color?, checked: Boolean, onClick: (() -> Unit)? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable { if (checked && onClick != null) onClick() else onChange(!checked) }
        .padding(start = 16.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(21.dp), tint = Mf.colors.textSecondary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 1.dp))
        }
        Spacer(Modifier.width(10.dp))
        app.superhealth.nutrition.ui.components.Toggle(checked, onChange)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
    Spacer(Modifier.height(8.dp))
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

