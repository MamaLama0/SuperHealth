package app.superhealth.nutrition.ui.screens

import app.superhealth.platform.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.domain.*
import app.superhealth.nutrition.domain.ManualTargets.Split
import app.superhealth.nutrition.ui.*
import app.superhealth.nutrition.ui.components.*
import app.superhealth.ui.theme.Mf
import kotlin.math.roundToInt

/**
 * Cibles manuelles de la semaine : une cible pour tous les jours, deux cibles « jour ON » (entraînement) et « jour OFF »
 * posées sur les jours d'un toucher, ou une cible par jour. Le programme rendu a toujours ses 7 jours prêts.
 */
@Composable
fun ManualWeekEditor(p: Program, settings: Settings, onValid: (Boolean) -> Unit, onChange: (Program) -> Unit) {
    var split by remember { mutableStateOf(ManualTargets.split(p)) }
    var editOff by remember { mutableStateOf(false) }
    var day by remember { mutableIntStateOf(java.time.LocalDate.now().dayOfWeek.value) }
    val unit = settings.energyUnit.label.tr()
    fun kcal(t: DayTarget) = settings.energyValue(t.kcal).roundToInt()

    ChipChoice(Split.entries, split, { when (it) { Split.SAME -> tr("Chaque jour"); Split.ON_OFF -> tr("Jours ON / OFF"); Split.DAYS -> tr("Jour par jour") } }) {
        split = it; editOff = false; onValid(true); onChange(ManualTargets.withSplit(p, it))
    }
    Text(when (split) {
        Split.SAME -> tr("La même cible tous les jours.")
        Split.ON_OFF -> tr("Deux cibles : touchez les jours pour les passer en ON (entraînement) ou OFF (repos).")
        Split.DAYS -> tr("Touchez un jour pour régler sa cible.")
    }, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))

    when (split) {
        Split.SAME -> key(split) {
            TargetEditor(ManualTargets.base(p), settings) { t -> onValid(t != null); t?.let { onChange(ManualTargets.withBase(p, it)) } }
        }
        Split.ON_OFF -> {
            val on = ManualTargets.base(p)
            val off = p.manualOff ?: on
            WeekStrip(selected = { it in p.manualOnDays }, top = { if (it in p.manualOnDays) "ON" else "OFF" }) { d ->
                onChange(ManualTargets.withOnDays(p, if (d in p.manualOnDays) p.manualOnDays - d else p.manualOnDays + d))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlotCard(tr("Jour ON"), on, p.manualOnDays.size, !editOff, unit, settings, Modifier.weight(1f)) { editOff = false }
                SlotCard(tr("Jour OFF"), off, 7 - p.manualOnDays.size, editOff, unit, settings, Modifier.weight(1f)) { editOff = true }
            }
            Spacer(Modifier.height(10.dp))
            Text(if (editOff) tr("Réglage du jour OFF") else tr("Réglage du jour ON"), style = MaterialTheme.typography.labelMedium,
                color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            key(split, editOff) {
                TargetEditor(if (editOff) off else on, settings) { t ->
                    onValid(t != null)
                    t?.let { onChange(if (editOff) ManualTargets.withOff(p, it) else ManualTargets.withBase(p, it)) }
                }
            }
            WeekAverage(p, settings)
        }
        Split.DAYS -> {
            WeekStrip(selected = { it == day }, top = { kcal(ManualTargets.forDay(p, it)).toString() }) { day = it }
            Spacer(Modifier.height(10.dp))
            Text(DAY_NAMES[day - 1], style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            key(split, day) {
                TargetEditor(ManualTargets.forDay(p, day), settings) { t -> onValid(t != null); t?.let { onChange(ManualTargets.withDay(p, day, it)) } }
            }
            Spacer(Modifier.height(6.dp))
            PillButton(tr("Appliquer ce jour à toute la semaine"), Modifier.fillMaxWidth(), strong = false) {
                val t = ManualTargets.forDay(p, day)
                onChange(ManualTargets.withBase(p, t).copy(manualDays = List(7) { t }))
            }
            WeekAverage(p, settings)
        }
    }
}

/** Les 7 jours en pastilles d'égale largeur : la valeur en petit sous le nom du jour. */
@Composable
private fun WeekStrip(selected: (Int) -> Boolean, top: (Int) -> String, onTap: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..7).forEach { d ->
            val on = selected(d)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) Mf.colors.strong else Mf.colors.chip)
                .clickable { onTap(d) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(DAY_NAMES[d - 1].take(3), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = if (on) Mf.colors.onStrong else Mf.colors.text, maxLines = 1)
                Text(top(d), style = MaterialTheme.typography.labelSmall, color = if (on) Mf.colors.onStrong else Mf.colors.textSecondary,
                    maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SlotCard(title: String, t: DayTarget, days: Int, active: Boolean, unit: String, settings: Settings, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(Mf.colors.chip).border(2.dp, if (active) Mf.colors.strong else Mf.colors.chip, shape)
        .clickable(onClick = onClick).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(trf("%1\$s j", days), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
        }
        Text("${settings.energyValue(t.kcal).roundToInt()} $unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("P ${t.protein.roundToInt()} · L ${t.fat.roundToInt()} · G ${t.carbs.roundToInt()}", style = MaterialTheme.typography.labelSmall,
            color = Mf.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun WeekAverage(p: Program, settings: Settings) {
    val week = (1..7).map { ManualTargets.forDay(p, it) }
    Text(trf("Moyenne de la semaine : %1\$s %2\$s · P %3\$s g", settings.energyValue(week.sumOf { it.kcal } / 7).roundToInt(),
        settings.energyUnit.label.tr(), (week.sumOf { it.protein } / 7).roundToInt()),
        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}
