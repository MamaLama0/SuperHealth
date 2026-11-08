package app.superhealth.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.superhealth.SuperPrefs
import app.superhealth.health.data.Hydration
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.ProgressBar
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.util.Locale

private val WaterBlue = Color(0xFF0A84FF)
private val SaltAmber = Color(0xFFFF9F0A)

private fun l(v: Double) = String.format(Locale.getDefault(), "%.1f", v)

/** 3.8 : objectif d'eau du jour expliqué, contenants, sodium de la journée et mesure du taux de sudation. */
@Composable
fun HydrationPanel(vm: SuperViewModel) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val settings by SuperPrefs.settings.collectAsState()
    var sweatSheet by remember { mutableStateOf(false) }
    var bump by remember { mutableStateOf(0) }
    val plan by produceState<Hydration.Plan?>(null, refresh, settings.hydrationAuto, settings.sweatRateLh, settings.sweatSodiumMgL, bump) {
        value = runCatching { Hydration.plan(ctx) }.getOrNull()
    }
    val p = plan ?: return

    SectionLabel(tr("Aujourd'hui"))
    MfCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatNumber(p.drunkMl), style = MaterialTheme.typography.displaySmall)
            Text(" / " + trf("%1\$s ml", formatNumber(p.goalMl)), style = MaterialTheme.typography.bodyLarge, color = Mf.colors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(p.progress, WaterBlue, height = 8.dp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            settings.waterSizes.forEach { ml ->
                Row(Modifier.weight(1f).clip(CircleShape).background(WaterBlue.copy(alpha = 0.14f))
                    .clickable { vm.log(Metric.HYDRATION, ml.toDouble()); bump++ }.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = WaterBlue)
                    Text("$ml ml", style = MaterialTheme.typography.labelLarge, color = WaterBlue)
                }
            }
        }
        if (p.plannedMl != null) {
            Spacer(Modifier.height(14.dp))
            Line(tr("Plan nutrition"), trf("%1\$s ml", formatNumber(p.plannedMl)), tr("Objectif de la semaine programmée"))
            if (p.goalMl - p.plannedMl > 25 && p.trainingMl > 0) Line(tr("Entraînement"), "+ " + trf("%1\$s ml", formatNumber(p.trainingMl)),
                trf("%1\$s L de sueur estimés, remplacés à 125 %%", l(p.sweatL)))
        } else if (settings.hydrationAuto) {
            Spacer(Modifier.height(14.dp))
            Line(tr("Base"), trf("%1\$s ml", formatNumber(p.baseMl)),
                p.weightKg?.let { trf("35 ml/kg × %1\$s kg", l(it)) } ?: tr("Repère EFSA (pas de poids connu)"))
            if (p.trainingMl > 0) Line(tr("Entraînement"), "+ " + trf("%1\$s ml", formatNumber(p.trainingMl)),
                trf("%1\$s L de sueur estimés, remplacés à 125 %%", l(p.sweatL)))
            if (p.proteinMl > 0) Line(tr("Protéines"), "+ " + trf("%1\$s ml", formatNumber(p.proteinMl)),
                trf("%1\$s g de protéines : plus d'urée à éliminer", formatNumber(p.proteinG)))
            if (p.foodWaterMl > 0) Line(tr("Eau du journal"), trf("%1\$s ml", formatNumber(p.foodWaterMl)),
                tr("Aliments et boissons notés : en plus, non comptés dans l'objectif"))
        }
    }

    if (p.sessions.isNotEmpty()) {
        SectionLabel(tr("Séances du jour"))
        GroupCard {
            p.sessions.forEachIndexed { i, s ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(trf("%1\$s min", s.minutes), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    Text(trf("≈ %1\$s L de sueur", l(s.sweatL)), style = MaterialTheme.typography.bodyMedium, color = WaterBlue)
                }
            }
        }
        Text(tr("Autour de la séance : 5–7 ml/kg dans les 4 h avant, quelques gorgées pendant, puis 1,25 à 1,5 L par kg perdu après (ACSM). Une perte de plus de 2 % du poids réduit la force et l'endurance."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
    }

    SectionLabel(tr("Sodium"))
    MfCard {
        val na = p.sodiumMg
        if (na == null) Text(tr("Notez vos repas dans le journal pour suivre le sodium."), color = Mf.colors.textSecondary)
        else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatNumber(na), style = MaterialTheme.typography.headlineMedium)
                Text(" / " + trf("%1\$s mg", formatNumber(p.sodiumTargetMg)), style = MaterialTheme.typography.bodyLarge, color = Mf.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar((na / p.sodiumTargetMg).toFloat().coerceAtMost(1f), if (na > p.sodiumTargetMg) Color(0xFFFF453A) else SaltAmber, height = 6.dp)
            Spacer(Modifier.height(10.dp))
            Text(when {
                p.sweatSodiumMg > 0 && na < p.sweatSodiumMg -> trf("Votre sueur a emporté ≈ %1\$s mg de sodium : salez un peu le repas d'après séance ou prenez une boisson d'effort.", formatNumber(p.sweatSodiumMg))
                na > p.sodiumTargetMg -> tr("Au-dessus du repère du jour : privilégiez des aliments peu transformés.")
                p.sweatSodiumMg > 0 -> trf("Repère relevé de %1\$s mg pour compenser la sueur des séances.", formatNumber(p.sweatSodiumMg))
                else -> tr("Repère de l'OMS : moins de 2 000 mg par jour (5 g de sel).")
            }, style = MaterialTheme.typography.bodyMedium)
            p.naK?.let { r ->
                Spacer(Modifier.height(6.dp))
                Text(trf("Rapport sodium/potassium : %1\$s (idéal ≤ 1 ; fruits, légumes et laitages font baisser).", String.format(Locale.getDefault(), "%.2f", r)),
                    style = MaterialTheme.typography.bodySmall, color = if (r > 1) SaltAmber else Mf.colors.textSecondary)
            }
        }
    }

    SectionLabel(tr("Réglages"))
    GroupCard {
        SwitchRow(tr("Objectif automatique"), tr("Selon le poids, les séances et les protéines du jour"), settings.hydrationAuto) { v ->
            SuperPrefs.update { it.copy(hydrationAuto = v) }
        }
        RowDivider()
        Row(Modifier.fillMaxWidth().clickable { sweatSheet = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Taux de sudation"), style = MaterialTheme.typography.bodyLarge)
                Text(if (settings.sweatRateLh != null) tr("Mesuré par pesée") else tr("Valeur moyenne : pesez-vous avant et après une séance pour le mesurer"),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Text(trf("%1\$s L/h", l(p.sweatRateLh)), style = MaterialTheme.typography.bodyLarge, color = WaterBlue)
        }
        RowDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Sueur salée"), style = MaterialTheme.typography.bodyLarge)
                Text(tr("Traces blanches sur les vêtements, sueur qui pique les yeux"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
            listOf(600.0 to tr("Peu"), 950.0 to tr("Moyen"), 1500.0 to tr("Beaucoup")).forEach { (v, label) ->
                val on = settings.sweatSodiumMgL == v
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else Mf.colors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp).clip(CircleShape).background(if (on) SaltAmber else Mf.colors.chip)
                        .clickable { SuperPrefs.update { it.copy(sweatSodiumMgL = v) } }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
    if (sweatSheet) SweatRateSheet { sweatSheet = false }
}

@Composable
private fun Line(title: String, value: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SweatRateSheet(onDismiss: () -> Unit) {
    var before by remember { mutableStateOf("") }
    var after by remember { mutableStateOf("") }
    var drank by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("60") }
    fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    val rate = num(before)?.let { b -> num(after)?.let { a -> Hydration.sweatRate(b, a, num(drank) ?: 0.0, num(minutes)?.toInt() ?: 0) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(tr("Mesurer son taux de sudation"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(tr("Pesez-vous juste avant et juste après une séance typique, sans vêtements mouillés et sans aller aux toilettes entre les deux. Notez ce que vous avez bu pendant."),
                style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            Field(tr("Poids avant (kg)"), before) { before = it }
            Field(tr("Poids après (kg)"), after) { after = it }
            Field(tr("Bu pendant la séance (ml)"), drank) { drank = it }
            Field(tr("Durée (min)"), minutes) { minutes = it }
            Spacer(Modifier.height(10.dp))
            Text(rate?.let { trf("Taux de sudation : %1\$s L/h", l(it)) } ?: tr("Renseignez les deux pesées et la durée."),
                style = MaterialTheme.typography.titleMedium, color = if (rate != null) WaterBlue else Mf.colors.textSecondary)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                if (SuperPrefs.settings.value.sweatRateLh != null) TextButton(onClick = { SuperPrefs.update { it.copy(sweatRateLh = null) }; onDismiss() }) { Text(tr("Revenir à la moyenne")) }
                TextButton(enabled = rate != null, onClick = { SuperPrefs.update { it.copy(sweatRateLh = rate) }; onDismiss() }) { Text(tr("Enregistrer")) }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}
