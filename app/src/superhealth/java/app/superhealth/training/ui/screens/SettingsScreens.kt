package app.superhealth.training.ui.screens

import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.training.domain.Sex
import app.superhealth.training.domain.Language
import app.superhealth.training.domain.ThemeMode
import app.superhealth.training.domain.WarmUpMode
import app.superhealth.training.domain.WeightUnit
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.AsyncAsset
import app.superhealth.training.ui.GymTemplate
import app.superhealth.training.ui.clean
import app.superhealth.training.ui.components.ChipChoice
import app.superhealth.training.ui.components.GroupCard
import app.superhealth.training.ui.components.MfCard
import app.superhealth.training.ui.components.NumberField
import app.superhealth.training.ui.components.PillButton
import app.superhealth.training.ui.components.RowDivider
import app.superhealth.training.ui.components.ScreenTitle
import app.superhealth.training.ui.components.SectionLabel
import app.superhealth.training.ui.components.SettingRow
import app.superhealth.training.ui.components.SwitchRow
import app.superhealth.training.ui.components.TextInput
import app.superhealth.training.ui.restLabel
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.training.ui.toKg
import app.superhealth.training.ui.toNum
import app.superhealth.training.ui.w
import app.superhealth.training.ui.weight
import java.time.LocalDate

/** Plus : profil, réglages d'entraînement (Smart Progression, échauffements, repos), salles, données. */
@Composable
fun MoreScreen(vm: AppViewModel, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val s = st.settings
    val ctx = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { runCatching { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(vm.exportJson().toByteArray()) } }
            .onSuccess { Toast.makeText(ctx, tr("Sauvegarde exportée"), Toast.LENGTH_SHORT).show() } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> val txt = runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes().decodeToString() } }.getOrNull()
            val ok = txt != null && vm.importJson(txt).isSuccess
            Toast.makeText(ctx, if (ok) tr("Sauvegarde restaurée") else tr("Fichier illisible"), Toast.LENGTH_SHORT).show() }
    }
    DetailScaffold(tr("Musculation"), nav) {
        Column {
            app.superhealth.nutrition.ui.components.GroupHeader(tr("Expérience"), Modifier.padding(start = 0.dp))
            MfCard { ChipChoice(app.superhealth.training.domain.Experience.entries, s.experience, { it.label.tr() }) { v -> vm.settings { it.copy(experience = v) } } }

            app.superhealth.nutrition.ui.components.GroupHeader(tr("Salles"), Modifier.padding(start = 0.dp))
            GroupCard {
                st.gyms.forEachIndexed { i, g ->
                    if (i > 0) RowDivider()
                    SettingRow(g.name, if (g.id == s.activeGymId) tr("Active") else null, subtitle = trf("%1\$s équipements · barre %2\$s", g.equipmentIds.size, (s.weight(g.barWeight))), icon = Icons.Rounded.Home) {
                        nav.navigate("gym/${g.id}")
                    }
                }
                if (st.gyms.isNotEmpty()) RowDivider()
                SettingRow(tr("Gérer les salles"), icon = Icons.Rounded.Add) { nav.navigate("gyms") }
            }

            app.superhealth.nutrition.ui.components.GroupHeader(tr("Progression intelligente"), Modifier.padding(start = 0.dp))
            GroupCard {
                SwitchRow(tr("Appliquer en séance"), tr("Ajuste les séries suivantes"), s.smartProgression) { v -> vm.settings { it.copy(smartProgression = v) } }
                RowDivider()
                SettingRow(tr("Pré-remplissage"), if (s.fillWithSmart) tr("Progression") else tr("Séance précédente")) { vm.settings { it.copy(fillWithSmart = !it.fillWithSmart) } }
                RowDivider()
                SwitchRow(tr("Élargir la plage de reps"), tr("Si la charge seule ne suffit pas"), s.expandRepRange) { v -> vm.settings { it.copy(expandRepRange = v) } }
                RowDivider()
                SwitchRow(tr("Garder la même charge"), tr("Weight Match"), s.weightMatch) { v -> vm.settings { it.copy(weightMatch = v) } }
            }

            app.superhealth.nutrition.ui.components.GroupHeader(tr("Échauffements"), Modifier.padding(start = 0.dp))
            MfCard {
                ChipChoice(WarmUpMode.entries, s.warmUp, { it.label.tr() }) { v -> vm.settings { it.copy(warmUp = v) } }
                if (s.warmUp == WarmUpMode.SCHEME) Text(tr("Schéma : ") + s.warmUpScheme.joinToString(" · ") { trf("%1\$s %% × %2\$s", ((it.percent * 100).toInt()), it.reps) },
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
            GroupCard(Modifier.padding(top = 8.dp)) {
                SwitchRow(tr("Masquer les échauffements faits"), null, s.hideCompletedWarmUps) { v -> vm.settings { it.copy(hideCompletedWarmUps = v) } }
                RowDivider()
                SwitchRow(tr("Simplifier les disques"), tr("Pas de petits disques à l'échauffement"), s.simplifyWarmUpPlates) { v -> vm.settings { it.copy(simplifyWarmUpPlates = v) } }
            }

            app.superhealth.nutrition.ui.components.GroupHeader(tr("Minuteur de repos"), Modifier.padding(start = 0.dp))
            GroupCard {
                SettingRow(tr("Repos par défaut"), restLabel(s.restDefaultSec)) { vm.settings { it.copy(restDefaultSec = next(it.restDefaultSec, listOf(60, 90, 120, 150, 180, 240))) } }
                RowDivider()
                SettingRow(tr("Après l'échauffement"), restLabel(s.restAfterWarmUpSec)) { vm.settings { it.copy(restAfterWarmUpSec = next(it.restAfterWarmUpSec, listOf(0, 30, 60, 90, 120))) } }
                RowDivider()
                SettingRow(tr("Entre gauche et droite"), restLabel(s.restBetweenSidesSec)) { vm.settings { it.copy(restBetweenSidesSec = next(it.restBetweenSidesSec, listOf(0, 15, 30, 45, 60))) } }
                RowDivider()
                SwitchRow(tr("Son de fin de repos"), null, s.restSound) { v -> vm.settings { it.copy(restSound = v) } }
            }

            app.superhealth.nutrition.ui.components.GroupHeader(tr("Données"), Modifier.padding(start = 0.dp))
            GroupCard {
                SettingRow(tr("Exporter une sauvegarde"), subtitle = tr("JSON complet"), icon = Icons.Rounded.Download) { export.launch("superhealth-musculation-${LocalDate.now()}.json") }
                RowDivider()
                SettingRow(tr("Restaurer une sauvegarde"), subtitle = tr("Remplace toutes les données"), icon = Icons.Rounded.Upload) { import.launch(arrayOf("application/json", "*/*")) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun next(v: Int, options: List<Int>) = options[(options.indexOf(v) + 1) % options.size]

/** Profils de salle (GymProfilesView). */
@Composable
fun GymsScreen(vm: AppViewModel, nav: NavHostController) {
    val st by vm.state.collectAsState()
    DetailScaffold(tr("Salles"), nav) {
        GroupCard {
            st.gyms.forEachIndexed { i, g ->
                if (i > 0) RowDivider()
                SettingRow(g.name, if (g.id == st.settings.activeGymId) tr("Active") else null, subtitle = trf("%1\$s équipements", g.equipmentIds.size)) { nav.navigate("gym/${g.id}") }
            }
        }
        app.superhealth.nutrition.ui.components.GroupHeader(tr("Nouvelle salle depuis un modèle"), Modifier.padding(start = 0.dp))
        GroupCard {
            GymTemplate.entries.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                SettingRow(t.label.tr(), icon = Icons.Rounded.Add) { val g = vm.gymFromTemplate(t); vm.saveGym(g); nav.navigate("gym/${g.id}") }
            }
        }
    }
}

private val COMMON_PLATES = listOf(50.0, 25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 2.0, 1.25, 1.0, 0.5)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
/** Édition d'une salle : charges (barre, disques, haltères, machines) et équipements par catégorie, avec les rendus de l'APK. */
@Composable
fun GymEditScreen(vm: AppViewModel, id: String, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    val g = st.gyms.firstOrNull { it.id == id } ?: return
    val s = st.settings
    var open by remember { mutableStateOf<String?>(null) }
    DetailScaffold(g.name, nav) {
        var name by remember { mutableStateOf(g.name) }
        TextInput(tr("Nom"), name, { name = it; vm.saveGym(g.copy(name = it.ifBlank { g.name })) })
        if (st.settings.activeGymId != g.id) PillButton(tr("Utiliser cette salle"), Modifier.fillMaxWidth().padding(vertical = 6.dp)) { vm.settings { it.copy(activeGymId = g.id) } }
        app.superhealth.nutrition.ui.components.GroupHeader(tr("Charges"), Modifier.padding(start = 0.dp))
        MfCard {
            var bar by remember { mutableStateOf(clean(s.w(g.barWeight))) }
            NumberField(tr("Barre olympique"), bar, { bar = it; it.toNum()?.let { v -> vm.saveGym(g.copy(barWeight = s.toKg(v))) } }, suffix = s.unit.label.tr())
            Text(tr("Disques disponibles"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                COMMON_PLATES.forEach { p ->
                    val on = p in g.plates
                    Text(clean(s.w(p)), Modifier.clip(CircleShape).background(if (on) Mf.colors.strong else Mf.colors.chip).clickable {
                        vm.saveGym(g.copy(plates = if (on) g.plates - p else (g.plates + p).sortedDescending()))
                    }.padding(horizontal = 12.dp, vertical = 7.dp), color = if (on) Mf.colors.onStrong else Mf.colors.text, style = MaterialTheme.typography.labelLarge)
                }
            }
            var db1 by remember { mutableStateOf(clean(s.w(g.dumbbellStep))) }
            var mc by remember { mutableStateOf(clean(s.w(g.machineStep))) }
            Row {
                NumberField(tr("Pas des haltères"), db1, { db1 = it; it.toNum()?.let { v -> vm.saveGym(g.copy(dumbbellStep = s.toKg(v))) } }, Modifier.weight(1f).padding(end = 6.dp), suffix = s.unit.label.tr())
                NumberField(tr("Pas des machines"), mc, { mc = it; it.toNum()?.let { v -> vm.saveGym(g.copy(machineStep = s.toKg(v))) } }, Modifier.weight(1f).padding(start = 6.dp), suffix = s.unit.label.tr())
            }
        }
        SectionLabel(trf("Équipements (%1\$s)", g.equipmentIds.size))
        db.equipment.groupBy { it.category ?: tr("Autre") }.toSortedMap().forEach { (cat, list) ->
            val count = list.count { it.id in g.equipmentIds }
            GroupCard(Modifier.padding(vertical = 4.dp)) {
                SettingRow(catLabel(cat), "${count} / ${list.size}") { open = if (open == cat) null else cat }
                if (open == cat) list.sortedBy { it.name }.forEach { eq ->
                    val on = eq.id in g.equipmentIds
                    RowDivider()
                    Row(Modifier.fillMaxWidth().clickable { vm.saveGym(g.copy(equipmentIds = if (on) g.equipmentIds - eq.id else g.equipmentIds + eq.id)) }
                        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncAsset(if (eq.img) "eq/${eq.id}.webp" else null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), crop = false, fallback = eq.name)
                        Spacer(Modifier.width(12.dp))
                        Text(eq.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Rounded.CheckCircle, null, tint = if (on) WorkoutBlue else Mf.colors.chip)
                    }
                }
            }
        }
        if (st.gyms.size > 1) Text(tr("Supprimer cette salle"), Modifier.fillMaxWidth().clickable { vm.deleteGym(g.id); nav.popBackStack() }.padding(14.dp),
            color = app.superhealth.ui.theme.Base.Red, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Catégories d'équipement (appEquipmentCategory) en français. */
private fun catLabel(c: String) = when (c) {
    "Free Weights" -> tr("Poids libres"); "Loadable Bars" -> tr("Barres chargeables"); "Bands & Ropes" -> tr("Bandes et cordes"); "Body Weights" -> tr("Poids du corps")
    "Benches & Racks" -> tr("Bancs et racks"); "Accessories & Functional" -> tr("Accessoires et fonctionnel"); "Cable Machines" -> tr("Machines à câbles")
    "Plate Loaded Machines" -> tr("Machines à disques"); "Pin Loaded Machines" -> tr("Machines à charge guidée"); "Fixed Weight Bars" -> tr("Barres à poids fixe")
    "Loadable Accessories" -> tr("Accessoires chargeables"); "Cardio" -> tr("Cardio"); else -> tr("Autre")
}

/** Version affichée (versionName de l'APK). */
@Composable
private fun appVersion(): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
}
