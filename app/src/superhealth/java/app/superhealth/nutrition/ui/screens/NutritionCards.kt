package app.superhealth.nutrition.ui.screens

import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.LocalFireDepartment
import app.superhealth.nutrition.data.CheckInState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.nutrition.data.Analytics
import app.superhealth.nutrition.domain.Expenditure
import app.superhealth.nutrition.domain.GoalType
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.domain.ProgramStyle
import app.superhealth.nutrition.domain.Settings
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.components.AssetImage
import app.superhealth.ui.charts.BarChart
import app.superhealth.ui.charts.LineChart
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.Ring
import app.superhealth.nutrition.ui.components.ScreenTitle
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.ui.charts.Series
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.macros
import app.superhealth.nutrition.ui.num
import app.superhealth.nutrition.ui.shortDate
import app.superhealth.ui.theme.Base
import app.superhealth.ui.theme.CaloriesColor
import app.superhealth.ui.theme.CarbsColor
import app.superhealth.ui.theme.FatColor
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.ProteinColor
import app.superhealth.nutrition.ui.weight
import app.superhealth.nutrition.ui.weightValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk

private val longDate get() = DateTimeFormatter.ofPattern("EEEE d MMMM", app.superhealth.nutrition.ui.Lang.locale)

@Composable
internal fun RemainingRing(label: String, value: Double, target: Double, color: Color) {
    val left = target - value
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(if (target > 0) (value / target).toFloat() else 0f, color, 70.dp, stroke = 6.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(abs(left).roundToInt().toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (left >= 0) tr("restant") else tr("en trop"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun InsightCard(title: String, value: String, unit: String, delta: String?, modifier: Modifier, onClick: () -> Unit, chart: @Composable () -> Unit) {
    MfCard(modifier, onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(" ${unit}", style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
        }
        delta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
        Spacer(Modifier.height(10.dp))
        chart()
    }
}

@Composable
internal fun ExpenditureCard(an: Analytics, s: Settings, modifier: Modifier, onClick: () -> Unit) {
    val pts = an.expenditure.takeLast(28)
    val first = pts.firstOrNull()?.tdee
    InsightCard(
        tr("Dépense"), s.energyValue(an.currentTdee).roundToInt().toString(), s.energyUnit.label.tr(),
        when (an.expenditure.lastOrNull()?.status) {
            Expenditure.Status.HOLDING -> tr("En pause (données insuffisantes)")
            Expenditure.Status.INITIAL, null -> tr("Estimation initiale")
            else -> first?.let { signed(s.energyValue(an.currentTdee - it), 0) + trf(" sur %1\$s j", pts.size) }
        },
        modifier, onClick,
    ) { LineChart(listOf(Series(pts.map { it.epochDay to s.energyValue(it.tdee) }, Base.Purple)), height = 56.dp, showAxis = false) }
}

@Composable
internal fun WeightCard(an: Analytics, s: Settings, modifier: Modifier, onClick: () -> Unit) {
    val pts = an.trend.takeLast(28)
    val week = an.trend.takeLast(8)
    InsightCard(
        tr("Tendance du poids"), an.currentTrendKg?.let { num(s.weightValue(it), 1) } ?: "—", s.weightUnit.label.tr(),
        if (week.size >= 2) signed(s.weightValue(week.last().trend - week.first().trend), 2) + tr(" / sem.") else tr("Ajoutez des pesées"),
        modifier, onClick,
    ) {
        LineChart(listOf(
            Series(pts.filter { it.scale != null }.map { it.epochDay to s.weightValue(it.scale!!) }, Mf.colors.textTertiary, dots = true),
            Series(pts.map { it.epochDay to s.weightValue(it.trend) }, Mf.colors.accent),
        ), height = 56.dp, showAxis = false)
    }
}

@Composable
internal fun IntakeCard(an: Analytics, s: Settings, onClick: () -> Unit) {
    val days = (an.today - 6..an.today).toList()
    val byDay = an.days.associateBy { it.epochDay }
    val avg = days.mapNotNull { byDay[it]?.intakeKcal }.takeIf { it.isNotEmpty() }?.average()
    MfCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Apports des 7 derniers jours"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(avg?.let { s.energyValue(it).roundToInt().toString() } ?: "—", style = MaterialTheme.typography.headlineMedium)
                    Text(trf(" %1\$s / jour en moyenne", s.energyUnit.label.tr()), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
        }
        Spacer(Modifier.height(12.dp))
        BarChart(
            days.map { byDay[it]?.intakeKcal?.let(s::energyValue) }, days.map { s.energyValue(an.targetFor(it).macros.kcal) }, CaloriesColor,
            height = 90.dp, labels = days.map { app.superhealth.nutrition.ui.dayLetter(LocalDate.ofEpochDay(it).dayOfWeek.value) },
        )
    }
}

@Composable
fun StrategySummaryCard(an: Analytics, s: Settings, onClick: () -> Unit) {
    val p = an.program
    val g = an.currentGoal
    val t = an.targetFor(an.today)
    MfCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssetImage(
                goalIllustration(g?.type ?: GoalType.MAINTENANCE),
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (p.style == ProgramStyle.MANUAL) tr("Programme manuel") else goalLabel(g?.type ?: GoalType.MAINTENANCE), style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    buildString {
                        if (p.style == ProgramStyle.COACHED && g != null && g.type != GoalType.MAINTENANCE) append(trf("%1\$s %% / sem. · ", (num(g.ratePctPerWeek, 2))))
                        append(trf("%1\$s %2\$s aujourd'hui", (s.energyValue(t.macros.kcal).roundToInt()), s.energyUnit.label.tr()))
                    },
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
                )
                g?.targetWeightKg?.let { Text(trf("Objectif : %1\$s", (s.weight(it))), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
                t.checkInDay?.let { Text(trf("Dernier check-in : %1\$s", (shortDate(it))), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
        }
    }
}

fun signed(v: Double, decimals: Int) = (if (v >= 0) "+" else "−") + num(abs(v), decimals)

@Composable
internal fun MiniTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    MfCard(modifier, onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = Mf.colors.accent)
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
