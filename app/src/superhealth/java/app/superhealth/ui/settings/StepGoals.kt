package app.superhealth.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.domain.Settings
import app.superhealth.ui.Lang
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.tr
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Pas d'un cran du sélecteur, et bornes d'un objectif (0 : aucun objectif). */
private const val STEP = 500
private const val MAX_GOAL = 60_000

/**
 * Objectif de pas personnalisé : une valeur libre (cran de 500 ou saisie), et au besoin une valeur par jour de la semaine
 * (week-end plus calme, jour de marche…). Un jour à 0 reprend l'objectif quotidien.
 */
@Composable
internal fun StepGoalEditor(s: Settings, set: ((Settings) -> Settings) -> Unit) {
    Stepper(null, s.stepGoal) { g -> set { it.copy(stepGoal = g) } }
    Spacer(Modifier.height(10.dp))
    val perDay = s.stepGoalDays.any { it > 0 }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Par jour de la semaine"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(perDay, { on ->
            set { it.copy(stepGoalDays = if (on) List(7) { _ -> it.stepGoal } else emptyList()) }
        }, colors = SwitchDefaults.colors(checkedTrackColor = Mf.colors.accent))
    }
    if (perDay) {
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                val v = s.stepGoalDays.getOrNull(d.value - 1)?.takeIf { it > 0 } ?: s.stepGoal
                Stepper(d.getDisplayName(TextStyle.FULL, Lang.locale).replaceFirstChar { it.titlecase(Lang.locale) }, v, compact = true) { g ->
                    set { st -> st.copy(stepGoalDays = List(7) { i -> if (i == d.value - 1) g else st.stepGoalDays.getOrNull(i) ?: st.stepGoal }) }
                }
            }
        }
    }
}

/** − valeur + ; toucher la valeur permet de la saisir. */
@Composable
private fun Stepper(label: String?, value: Int, compact: Boolean = false, onChange: (Int) -> Unit) {
    var edit by remember { mutableStateOf(false) }
    val hue = HealthHues.Activity
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label != null) Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        Round(Icons.Rounded.Remove, compact) { onChange((value - STEP).coerceAtLeast(0) / STEP * STEP) }
        Text(if (value == 0) tr("Aucun") else formatNumber(value.toDouble()),
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            color = hue, maxLines = 1,
            modifier = (if (label == null) Modifier.weight(1f) else Modifier.width(96.dp)).clip(CircleShape).clickable { edit = true }
                .padding(vertical = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Round(Icons.Rounded.Add, compact) { onChange(((value + STEP) / STEP * STEP).coerceAtMost(MAX_GOAL)) }
    }
    if (edit) {
        var text by remember { mutableStateOf(if (value > 0) value.toString() else "") }
        AlertDialog(
            onDismissRequest = { edit = false }, containerColor = Mf.colors.card,
            title = { Text(label ?: tr("Objectif de pas")) },
            text = {
                OutlinedTextField(text, { t -> text = t.filter(Char::isDigit).take(5) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mf.colors.accent, unfocusedBorderColor = Mf.colors.divider))
            },
            confirmButton = {
                TextButton(onClick = { onChange((text.toIntOrNull() ?: 0).coerceIn(0, MAX_GOAL)); edit = false }) { Text(tr("Valider"), color = Mf.colors.accent) }
            },
            dismissButton = { TextButton(onClick = { edit = false }) { Text(tr("Annuler"), color = Mf.colors.text) } },
        )
    }
}

@Composable
private fun Round(icon: androidx.compose.ui.graphics.vector.ImageVector, compact: Boolean, onClick: () -> Unit) {
    val s = if (compact) 32.dp else 40.dp
    Box(Modifier.size(s).clip(CircleShape).background(HealthHues.Activity.container()).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(s * 0.55f), tint = HealthHues.Activity)
    }
}
