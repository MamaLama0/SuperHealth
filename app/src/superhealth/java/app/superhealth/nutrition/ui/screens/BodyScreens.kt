package app.superhealth.nutrition.ui.screens

import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.nutrition.domain.Habits
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.ui.theme.Mf
import java.time.LocalDate

// ----- Habitudes -----

/** Page Habitudes : séries de journalisation et de pesée, calendrier des 12 dernières semaines. */
@Composable
fun HabitsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    var which by remember { mutableStateOf(0) }
    DetailScaffold(tr("Habitudes"), onBack) {
        val an = a ?: return@DetailScaffold
        val logged = an.days.filter { it.intakeKcal != null || it.fasted }.map { it.epochDay }.toSet()
        val partial = an.days.filter { it.partial }.map { it.epochDay }.toSet()
        val weighed = an.days.filter { it.weightKg != null }.map { it.epochDay }.toSet()
        val food = Habits.streak(logged, an.today)
        val weigh = Habits.streak(weighed, an.today)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StreakCard(tr("Journal"), food, Icons.Rounded.LocalFireDepartment, Modifier.weight(1f))
            StreakCard(tr("Pesées"), weigh, Icons.Rounded.MonitorWeight, Modifier.weight(1f))
        }
        SectionLabel(tr("Calendrier"))
        ChipChoice(listOf(0, 1), which, { if (it == 0) tr("Journal alimentaire") else tr("Pesées") }) { which = it }
        Spacer(Modifier.height(10.dp))
        MfCard {
            val days = if (which == 0) logged else weighed
            val on = Mf.colors.accent; val half = on.copy(alpha = 0.45f); val off = Mf.colors.chip
            HabitGrid(an.today, weeks = 12) { d ->
                when {
                    d > an.today -> Color.Transparent
                    which == 0 && d in partial && d in days -> half
                    d in days -> on
                    else -> off
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Legend(Mf.colors.accent, if (which == 0) tr("Journalisé") else tr("Pesée"))
                if (which == 0) Legend(Mf.colors.accent.copy(alpha = 0.45f), tr("Incomplet"))
                Legend(Mf.colors.chip, tr("Rien"))
            }
        }
        Text(tr("Un jour compte s'il est journalisé ou marqué jeûne."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(8.dp))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StreakCard(title: String, s: Habits.Streak, icon: ImageVector, modifier: Modifier) {
    MfCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (s.current > 0) Color(0xFFFF8A3D) else Mf.colors.textTertiary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${s.current}", style = MaterialTheme.typography.displaySmall)
            Text(tr(" j"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(tr("Série actuelle"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text(trf("Record : %1\$s j · %2\$s/30 j", s.longest, s.done30), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
    Text(" ${label}   ", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
}

/** Grille semaines × jours (lundi en haut), la semaine courante à droite. */
@Composable
private fun HabitGrid(today: Long, weeks: Int, color: (Long) -> Color) {
    val monday = today - LocalDate.ofEpochDay(today).dayOfWeek.value + 1
    val first = monday - (weeks - 1) * 7
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).map { app.superhealth.nutrition.ui.dayLetter(it) }.forEach { Text(it, fontSize = 9.sp, color = Mf.colors.textTertiary, modifier = Modifier.height(16.dp)) }
        }
        (0 until weeks).forEach { w ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { d ->
                    val day = first + w * 7 + d
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(color(day)))
                }
            }
        }
    }
}
