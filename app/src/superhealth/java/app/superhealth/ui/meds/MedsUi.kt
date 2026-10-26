package app.superhealth.ui.meds

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.meds.Meds
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.TextInput
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Teinte du plugin (comme sa fiche dans Réglages > Plugins). */
val MedsHue = Color(0xFF3FA7D6)

private fun hm(minute: Int) = app.superhealth.ui.timeOf(LocalDate.now().atStartOfDay().plusMinutes(minute.toLong())
    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())

/** Rappel médication : prises du jour à cocher, puis les médicaments (ajout, modification). */
@Composable
fun MedsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val meds by Meds.meds.collectAsState()
    val taken by Meds.taken.collectAsState()
    var editing by remember { mutableStateOf<Meds.Med?>(null) }
    // Android 13+ : sans cette autorisation, les rappels ne s'affichent pas
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun ensureNotif() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val today = LocalDate.now()
    @Suppress("UNUSED_VARIABLE") val tick = taken
    val doses = Meds.doses(today)
    val now = LocalTime.now().let { it.hour * 60 + it.minute }

    DetailScaffold(tr("Rappel médication"), onBack = { nav.popBackStack() }) {
        if (!Meds.available()) {
            MfCard { Text(tr("Activez le plugin dans Réglages > Plugins."), color = Mf.colors.textSecondary) }
            return@DetailScaffold
        }
        SectionLabel(tr("Aujourd'hui"))
        if (doses.isEmpty()) MfCard { Text(tr("Aucune prise prévue aujourd'hui."), color = Mf.colors.textSecondary) }
        else GroupCard {
            doses.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                val late = !d.taken && d.minute <= now
                Row(Modifier.fillMaxWidth().clickable { Meds.setTaken(ctx, today.toEpochDay(), d.key, !d.taken) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(hm(d.minute), Modifier.width(64.dp), style = MaterialTheme.typography.titleSmall,
                        color = if (late) MedsHue.onContainer() else Mf.colors.textSecondary)
                    Column(Modifier.weight(1f)) {
                        Text(d.med.name, style = MaterialTheme.typography.bodyLarge, textDecoration = if (d.taken) TextDecoration.LineThrough else null,
                            color = if (d.taken) Mf.colors.textTertiary else Mf.colors.text)
                        if (d.med.dose.isNotBlank()) Text(d.med.dose, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    val bg by animateColorAsState(if (d.taken) MedsHue else MedsHue.container(), label = "taken")
                    Box(Modifier.size(36.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, if (d.taken) tr("Pris") else tr("Marquer comme pris"), Modifier.size(20.dp),
                            tint = if (d.taken) Color.White else MedsHue)
                    }
                }
            }
        }

        SectionLabel(tr("Médicaments et compléments"))
        GroupCard {
            meds.sortedBy { it.times.minOrNull() ?: 0 }.forEachIndexed { i, m ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable { editing = m }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(MedsHue.container()), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Medication, null, Modifier.size(18.dp), tint = MedsHue)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.bodyLarge, color = if (m.active) Mf.colors.text else Mf.colors.textTertiary)
                        Text(listOfNotNull(m.dose.takeIf { it.isNotBlank() }, m.times.sorted().joinToString(", ") { hm(it) },
                            if (m.days.size == 7) null else m.days.sorted().joinToString(" ") { DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, app.superhealth.ui.Lang.locale) })
                            .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    app.superhealth.nutrition.ui.components.Toggle(m.active, { Meds.upsert(ctx, m.copy(active = it)) })
                }
            }
            if (meds.isNotEmpty()) RowDivider()
            Row(Modifier.fillMaxWidth().clickable { ensureNotif(); editing = Meds.Med(UUID.randomUUID().toString(), "") }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, null, tint = MedsHue)
                Spacer(Modifier.width(12.dp))
                Text(tr("Ajouter"), style = MaterialTheme.typography.bodyLarge, color = MedsHue.onContainer())
            }
        }
        Text(tr("Un rappel, pas un avis médical : suivez la prescription de votre médecin."), style = MaterialTheme.typography.bodySmall,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
        Spacer(Modifier.height(24.dp))
    }
    editing?.let { m -> MedSheet(m, isNew = meds.none { it.id == m.id }, onDismiss = { editing = null },
        onDelete = { Meds.remove(ctx, m.id); editing = null }) { Meds.upsert(ctx, it); editing = null } }
}

/** Ajout ou modification : nom, dose (formes du plugin en raccourci), heures (repères du plugin ou heure précise), jours. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedSheet(m: Meds.Med, isNew: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Meds.Med) -> Unit) {
    var name by remember { mutableStateOf(m.name) }
    var dose by remember { mutableStateOf(m.dose) }
    var times by remember { mutableStateOf(m.times.sorted()) }
    var days by remember { mutableStateOf(m.days) }
    var picking by remember { mutableStateOf(false) }
    val c = Meds.content()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
            Text(if (isNew) tr("Nouveau rappel") else tr("Modifier le rappel"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextInput(tr("Nom"), name, { name = it.take(40) }, placeholder = tr("Ex. : vitamine D"))
            TextInput(tr("Dose"), dose, { dose = it.take(40) }, placeholder = tr("Ex. : 1 comprimé"))
            if (c.forms.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                c.forms.forEach { f ->
                    Text("1 " + f.text, style = MaterialTheme.typography.labelLarge, color = MedsHue.onContainer(),
                        modifier = Modifier.clip(CircleShape).background(MedsHue.container()).clickable { dose = "1 " + f.text }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Text(tr("Heures"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                times.forEach { t ->
                    Row(Modifier.clip(CircleShape).background(MedsHue).clickable { times = times - t }.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(hm(t), style = MaterialTheme.typography.labelLarge, color = Color.White)
                        Icon(Icons.Rounded.Close, tr("Retirer"), Modifier.padding(start = 4.dp).size(14.dp), tint = Color.White)
                    }
                }
                // repères du plugin (matin, midi, soir…) puis une heure précise
                c.times.filter { it.minute !in times }.forEach { p ->
                    Text(p.name.text + " · " + hm(p.minute), style = MaterialTheme.typography.labelLarge, color = Mf.colors.text,
                        modifier = Modifier.clip(CircleShape).background(Mf.colors.chip).clickable { times = (times + p.minute).sorted() }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Text(tr("Autre heure"), style = MaterialTheme.typography.labelLarge, color = MedsHue.onContainer(),
                    modifier = Modifier.clip(CircleShape).background(MedsHue.container()).clickable { picking = true }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Text(tr("Jours"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..7).forEach { d ->
                    val sel = d in days
                    Box(Modifier.size(40.dp).clip(CircleShape).background(if (sel) MedsHue else Mf.colors.chip)
                        .clickable { days = if (sel && days.size > 1) days - d else days + d }, contentAlignment = Alignment.Center) {
                        Text(DayOfWeek.of(d).getDisplayName(java.time.format.TextStyle.NARROW, app.superhealth.ui.Lang.locale),
                            color = if (sel) Color.White else Mf.colors.text, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            PillButton(tr("Enregistrer"), Modifier.fillMaxWidth().padding(top = 20.dp), enabled = name.isNotBlank() && times.isNotEmpty()) {
                onSave(m.copy(name = name.trim(), dose = dose.trim(), times = times, days = days))
            }
            if (!isNew) PillButton(tr("Supprimer"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false, onClick = onDelete)
        }
    }
    if (picking) {
        val state = rememberTimePickerState(8, 0, is24Hour = !app.superhealth.ui.Lang.english)
        androidx.compose.material3.AlertDialog(onDismissRequest = { picking = false }, containerColor = Mf.colors.card,
            text = { TimePicker(state) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { times = (times + (state.hour * 60 + state.minute)).distinct().sorted(); picking = false }) {
                Text(tr("Ajouter"), color = MedsHue.onContainer()) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text(tr("Annuler"), color = Mf.colors.text) } })
    }
}
