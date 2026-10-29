package app.superhealth.nutrition.ui.screens

import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.nutrition.data.Analytics
import app.superhealth.nutrition.data.CheckInState
import app.superhealth.nutrition.data.DayFlagEntity
import app.superhealth.nutrition.data.WeighInEntity
import app.superhealth.nutrition.domain.Activity
import app.superhealth.nutrition.domain.Athlete
import app.superhealth.nutrition.domain.BmrEquation
import app.superhealth.nutrition.domain.CalorieFloor
import app.superhealth.nutrition.domain.DietPreference
import app.superhealth.nutrition.domain.ExerciseLevel
import app.superhealth.nutrition.domain.Goal
import app.superhealth.nutrition.domain.GoalStatus
import app.superhealth.nutrition.domain.GoalType
import app.superhealth.nutrition.domain.LifetimeWeight
import app.superhealth.nutrition.domain.ProgramStyle
import app.superhealth.nutrition.domain.ProteinPreference
import app.superhealth.nutrition.domain.Settings
import app.superhealth.nutrition.domain.Sex
import app.superhealth.nutrition.domain.Targets
import app.superhealth.nutrition.domain.TdeeMode
import app.superhealth.nutrition.domain.Training
import app.superhealth.nutrition.domain.WeightChange
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.components.AssetImage
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.ProgressBar
import app.superhealth.nutrition.ui.components.Ring
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.ScreenTitle
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.num
import app.superhealth.ui.theme.CaloriesColor
import app.superhealth.ui.theme.CarbsColor
import app.superhealth.ui.theme.FatColor
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.ProteinColor
import app.superhealth.nutrition.ui.toKcal
import app.superhealth.nutrition.ui.toKg
import app.superhealth.nutrition.ui.toNum
import app.superhealth.nutrition.ui.weight
import app.superhealth.nutrition.ui.weightValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

val DAY_NAMES = listOf(tr("Lundi"), tr("Mardi"), tr("Mercredi"), tr("Jeudi"), tr("Vendredi"), tr("Samedi"), tr("Dimanche"))
private val longFmt get() = DateTimeFormatter.ofPattern("EEEE d MMMM", app.superhealth.nutrition.ui.Lang.locale)
private val dateFmt get() = DateTimeFormatter.ofPattern("d MMM yyyy", app.superhealth.nutrition.ui.Lang.locale)

fun goalLabel(t: GoalType) = when (t) { GoalType.CUT -> tr("Perte de poids"); GoalType.BULK -> tr("Prise de poids"); GoalType.MAINTENANCE -> tr("Maintien") }
/** Symbole de l'objectif (perte : tendance à la baisse, prise : à la hausse, maintien : équilibre). */
fun goalIcon(t: GoalType) = when (t) {
    GoalType.CUT -> androidx.compose.material.icons.Icons.AutoMirrored.Rounded.TrendingDown
    GoalType.BULK -> androidx.compose.material.icons.Icons.AutoMirrored.Rounded.TrendingUp
    else -> androidx.compose.material.icons.Icons.Rounded.Balance
}
fun fmtDate(day: Long): String = LocalDate.ofEpochDay(day).format(dateFmt)

@Composable
fun StrategyScreen(vm: AppViewModel, nav: NavHostController) {
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        ScreenTitle(tr("Stratégie"), onBack = { nav.popBackStack() })
        val an = a ?: return@Column
        val p = an.program
        Column(Modifier.padding(horizontal = 16.dp)) {
            CheckInBanner(an) { nav.navigate("checkin") }
            Spacer(Modifier.height(12.dp))
            GoalCard(an, s, onEdit = { nav.navigate("goal") })

            SectionLabel(tr("Cibles de la semaine"))
            val monday = an.today - LocalDate.ofEpochDay(an.today).dayOfWeek.value + 1
            GroupCard {
                (0 until 7).forEach { i ->
                    val d = monday + i
                    val t = an.weekTarget(d).macros
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(DAY_NAMES[i], style = MaterialTheme.typography.bodyMedium, fontWeight = if (d == an.today) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        Dot(CaloriesColor); Text(" ${s.energyValue(t.kcal).roundToInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp))
                        Dot(ProteinColor); Text(" ${t.protein.roundToInt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
                        Dot(FatColor); Text(" ${t.fat.roundToInt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
                        Dot(CarbsColor); Text(" ${t.carbs.roundToInt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
                    }
                }
            }

            SectionLabel(tr("Programme"))
            GroupCard {
                SettingRow(tr("Style"), when (p.style) { ProgramStyle.COACHED -> tr("Coaché"); ProgramStyle.MANUAL -> tr("Manuel") }) { nav.navigate("program") }
                RowDivider()
                SettingRow(tr("Diète"), p.dietPreference.label.tr()) { nav.navigate("program") }
                RowDivider()
                SettingRow(tr("Protéines"), trf("%1\$s · %2\$s g/kg MM", p.proteinPreference.label.tr(), (num(Targets.proteinPerKgLbm(an.currentGoal?.type ?: GoalType.MAINTENANCE, p.training, p.proteinPreference), 2)))) { nav.navigate("program") }
                RowDivider()
                SettingRow(tr("Entraînement"), p.training.label.tr()) { nav.navigate("program") }
                RowDivider()
                SettingRow(tr("Plancher calorique"), "${p.calorieFloor.label.tr()}${if (p.calorieFloor != CalorieFloor.NONE)
                    " · ${s.energyValue(Targets.dynamicFloor(p.calorieFloor, an.profile.heightCm, an.lbmAt(an.today))).roundToInt()}" else ""}") { nav.navigate("program") }
                RowDivider()
                SettingRow(tr("Répartition"), if (p.highDays.isEmpty()) tr("Identique chaque jour") else
                    tr("Jours hauts : ") + p.highDays.sorted().joinToString(", ") { DAY_NAMES[it - 1].take(3) + "." }) { nav.navigate("program") }
                RowDivider()
                SettingRow(tr("Dépense"), if (p.tdeeMode == TdeeMode.DYNAMIC) trf("Dynamique · %1\$s %2\$s", (s.energyValue(an.currentTdee).roundToInt()), s.energyUnit.label.tr()) else tr("Fixe")) { nav.navigate("expenditure") }
            }

            if (an.checkIns.isNotEmpty()) {
                SectionLabel(tr("Historique des check-ins"))
                GroupCard {
                    an.checkIns.reversed().take(12).forEachIndexed { i, c ->
                        if (i > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(fmtDate(c.day), style = MaterialTheme.typography.bodyMedium)
                                Text(trf("Dépense %1\$s · %2\$s", (s.energyValue(c.tdee).roundToInt()), (c.trendKg?.let { s.weight(it) } ?: "—")), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                            }
                            Text("${s.energyValue(c.kcal).roundToInt()} ${s.energyUnit.label.tr()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (an.goals.size > 1) {
                SectionLabel(tr("Historique des objectifs"))
                GroupCard {
                    an.goals.reversed().forEachIndexed { i, g ->
                        if (i > 0) RowDivider()
                        SettingRow(goalLabel(g.type), g.status.label.tr(), subtitle = "${fmtDate(g.startDay)}${g.closedDay?.let { " → " + fmtDate(it) } ?: ""}")
                    }
                }
            }

            SectionLabel(tr("Profil"))
            GroupCard {
                SettingRow(tr("Profil et métabolisme"), trf("%1\$s, %2\$s cm", (if (an.profile.sex == Sex.MALE) tr("Homme") else tr("Femme")), (num(an.profile.heightCm, 0)))) { nav.navigate("profile") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CheckInBanner(an: Analytics, onCheckIn: () -> Unit) {
    when (an.checkInState) {
        CheckInState.NOW -> MfCard(onClick = onCheckIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Mf.colors.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.EventAvailable, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("C'est l'heure du check-in"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("Votre programme va être mis à jour selon vos données de la semaine."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
            }
        }
        CheckInState.FUTURE -> MfCard {
            val left = (an.nextCheckInDay - an.today).toInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(1f - left / 7f, Mf.colors.accent, 56.dp, stroke = 5.dp) {
                    Text(trf("%1\$s j", left), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Prochain check-in"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
                    Text(LocalDate.ofEpochDay(an.nextCheckInDay).format(longFmt).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                    Text(tr("Continuez à journaliser vos repas et votre poids."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
            }
        }
        CheckInState.NONE -> MfCard {
            Text(tr("Programme manuel : les cibles ne bougent que si vous les modifiez."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
}

@Composable
private fun GoalCard(an: Analytics, s: Settings, onEdit: () -> Unit) {
    val g = an.currentGoal
    MfCard(padding = PaddingValues(0.dp)) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.padding(bottom = 10.dp).size(44.dp).clip(RoundedCornerShape(12.dp)).background(app.superhealth.ui.theme.HealthHues.Body),
                contentAlignment = Alignment.Center) {
                Icon(goalIcon(g?.type ?: GoalType.MAINTENANCE), null, Modifier.size(26.dp), tint = androidx.compose.ui.graphics.Color.White)
            }
            Text(tr("Objectif actuel"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
            Text(g?.let { goalLabel(it.type) } ?: tr("Aucun objectif"), style = MaterialTheme.typography.headlineSmall)
            if (g != null) {
                val trend = an.currentTrendKg
                val target = g.targetWeightKg
                Spacer(Modifier.height(10.dp))
                Row {
                    GoalStat(s.weight(g.startWeightKg), tr("Départ"), Modifier.weight(1f))
                    GoalStat(trend?.let { s.weight(it) } ?: "—", tr("Tendance"), Modifier.weight(1f))
                    GoalStat(target?.let { s.weight(it) } ?: "—", tr("Cible"), Modifier.weight(1f))
                }
                val reached = target != null && trend != null && g.status == GoalStatus.IN_PROGRESS &&
                    ((g.type == GoalType.CUT && trend <= target) || (g.type == GoalType.BULK && trend >= target))
                if (reached) {
                    Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Mf.colors.accent.copy(alpha = 0.12f)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AssetImage(if (Mf.colors.dark) "misc/ribbon_base_dark2.webp" else "misc/ribbon_base.webp", Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(tr("Objectif atteint !"), style = MaterialTheme.typography.titleMedium)
                            Text(tr("Cible atteinte ! Nouvel objectif ou maintien ?"),
                                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                        }
                    }
                }
                if (target != null && trend != null && g.type != GoalType.MAINTENANCE) {
                    val total = abs(target - g.startWeightKg).coerceAtLeast(0.1)
                    val done = if (g.type == GoalType.CUT) g.startWeightKg - trend else trend - g.startWeightKg
                    val frac = (done / total).coerceIn(0.0, 1.0)
                    Spacer(Modifier.height(12.dp))
                    ProgressBar(frac.toFloat(), Mf.colors.accent, height = 8.dp)
                    Text(trf("%1\$s %% accompli · reste %2\$s", ((frac * 100).roundToInt()), (s.weight(abs(target - trend)))), style = MaterialTheme.typography.bodySmall,
                        color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    if (g.type != GoalType.MAINTENANCE) {
                        val kgWeek = g.ratePctPerWeek / 100 * (trend ?: g.startWeightKg)
                        GoalStat("${num(g.ratePctPerWeek, 2)} %", trf("≈ %1\$s %2\$s/sem.", (num(s.weightValue(kgWeek), 2)), s.weightUnit.label.tr()), Modifier.weight(1f))
                    } else {
                        val approach = if (trend != null) Targets.maintenanceApproach(g, trend) else 0
                        GoalStat("±${s.weight(Targets.MAINTENANCE_BAND_KG)}", when (approach) {
                            -1 -> tr("Hors plage : perte lente"); 1 -> tr("Hors plage : prise lente"); else -> tr("Dans la plage de maintien")
                        }, Modifier.weight(1f))
                    }
                    if (g.type == GoalType.MAINTENANCE) GoalStat(fmtDate(g.startDay), tr("Depuis le"), Modifier.weight(1f))
                    else {
                        val eta = g.endDay ?: if (target != null && trend != null) Targets.endDayFromRate(an.today, trend, target, g.ratePctPerWeek) else null
                        GoalStat(eta?.let { fmtDate(it) } ?: "—", if (g.endDay != null) tr("Date de fin visée") else tr("Arrivée estimée"), Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            PillButton(if (g == null) tr("Définir un objectif") else tr("Modifier l'objectif"), Modifier.fillMaxWidth(), icon = Icons.Rounded.Flag, onClick = onEdit)
        }
    }
}

@Composable
private fun GoalStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(7.dp).clip(CircleShape).background(color))

@Composable
private fun FieldTitle(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp))

/** Création d'un objectif : type, poids cible, et au choix le rythme ou la date de fin (l'autre est recalculé). */
@Composable
fun GoalEditScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val an = a ?: return
    val current = an.currentGoal
    val trend = an.currentTrendKg ?: current?.startWeightKg ?: Analytics.DEFAULT_WEIGHT
    var type by remember { mutableStateOf(current?.type ?: GoalType.CUT) }
    var target by remember { mutableStateOf(current?.targetWeightKg?.let { num(s.weightValue(it), 1) } ?: "") }
    var byDate by remember { mutableStateOf(current?.endDay != null) }
    var rate by remember { mutableStateOf(num(current?.ratePctPerWeek?.takeIf { it > 0 } ?: 0.5, 2)) }
    var endDay by remember { mutableLongStateOf(current?.endDay ?: (an.today + 84)) }
    val targetKg = target.toNum()?.let { s.toKg(it) }
    val computedRate = if (byDate && targetKg != null) Targets.rateFromEndDay(an.today, trend, targetKg, endDay) else rate.toNum()
    val computedEnd = if (!byDate && targetKg != null && computedRate != null) Targets.endDayFromRate(an.today, trend, targetKg, computedRate) else endDay
    // âge (Senior) : par tranche, perte seulement au-dessus d'un IMC, déficit borné, perte et IMC de la cible bornés
    val age = LocalDate.now().year - an.profile.birthYear
    val maxRate = if (type == GoalType.CUT) minOf(Targets.MAX_RATE, app.superhealth.nutrition.domain.Senior.maxCutRatePct(age, trend) ?: Targets.MAX_RATE) else Targets.MAX_RATE
    val cutAllowed = type != GoalType.CUT || app.superhealth.nutrition.domain.Senior.cutAllowed(age, trend, an.profile.heightCm)
    val minTarget = if (type == GoalType.CUT) app.superhealth.nutrition.domain.Senior.minTargetKg(age, an.profile.heightCm, trend) else null
    val rateOk = type == GoalType.MAINTENANCE || (computedRate != null && computedRate in Targets.MIN_RATE..maxRate)
    val directionOk = targetKg == null || type == GoalType.MAINTENANCE || (type == GoalType.CUT && targetKg < trend) || (type == GoalType.BULK && targetKg > trend)
    val floorOk = minTarget == null || targetKg == null || targetKg >= minTarget

    DetailScaffold(tr("Objectif"), onBack) {
        MfCard {
            FieldTitle(tr("Type d'objectif"))
            ChipChoice(GoalType.entries, type, { it.label.tr() }) { type = it }
            NumberField(if (type == GoalType.MAINTENANCE) tr("Poids à maintenir") else tr("Poids cible"), target, { target = it }, suffix = s.weightUnit.label.tr())
            Text(trf("Tendance actuelle : %1\$s", (s.weight(trend))), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
            if (type != GoalType.MAINTENANCE) {
                FieldTitle(tr("Définir par"))
                ChipChoice(listOf(false, true), byDate, { if (it) tr("Date de fin") else tr("Rythme") }) { byDate = it }
                if (!byDate) {
                    NumberField(tr("Rythme"), rate, { rate = it }, suffix = tr("% du poids / sem."))
                    ChipChoice(listOf(0.25, 0.5, 0.75, 1.0), rate.toNum() ?: 0.0, { "${num(it, 2)} %" }) { rate = num(it, 2) }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(fmtDate(endDay), Modifier.clip(CircleShape).background(Mf.colors.chip).clickable {
                        val d = LocalDate.ofEpochDay(endDay)
                        DatePickerDialog(ctx, { _, y, m, dd -> endDay = LocalDate.of(y, m + 1, dd).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (!cutAllowed) Text(noCutText(age),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        if (type != GoalType.MAINTENANCE && targetKg != null) {
            Spacer(Modifier.height(12.dp))
            MfCard {
                Row {
                    GoalStat(computedRate?.let { trf("%1\$s %%/sem.", (num(it, 2))) } ?: "—", tr("Rythme"), Modifier.weight(1f))
                    GoalStat(computedRate?.let { "${num(s.weightValue(it / 100 * trend), 2)} ${s.weightUnit.label.tr()}" } ?: "—", tr("par semaine"), Modifier.weight(1f))
                    GoalStat(computedEnd?.let { fmtDate(it) } ?: "—", tr("Fin"), Modifier.weight(1f))
                }
                computedRate?.let { r ->
                    Text(trf("Écart calorique moyen : %1\$s%2\$s %3\$s/jour", (if (type == GoalType.CUT) "−" else "+"), (s.energyValue(r / 100 * trend * Targets.KCAL_PER_KG / 7).roundToInt()), s.energyUnit.label.tr()),
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
                }
                if (!rateOk) Text(trf("Rythme hors limites (%1\$s–%2\$s %%/sem.) : choisissez une date plus %3\$s.", (num(Targets.MIN_RATE, 2)), (num(maxRate, if (maxRate < 1) 2 else 1)), (if ((computedRate ?: 0.0) > maxRate) tr("lointaine") else tr("proche"))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                if (!directionOk) Text(tr("Le poids cible ne correspond pas au type d'objectif."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (maxRate < Targets.MAX_RATE) Text(trf("À votre âge, perte limitée à %1\$s %%/sem. (déficit de %2\$s kcal/j au plus).", num(maxRate, 2),
                    app.superhealth.nutrition.domain.Senior.maxDeficit(app.superhealth.nutrition.domain.Senior.band(age))!!.roundToInt()),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                if (!floorOk) Text(minTargetText(age, an.profile.heightCm, trend, s::weight),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                Text(tr("Estimation optimiste : prévoyez un peu de marge."),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        PillButton(tr("Enregistrer l'objectif"), Modifier.fillMaxWidth(), enabled = rateOk && directionOk && floorOk && (type == GoalType.MAINTENANCE || targetKg != null)) {
            val g = Goal(
                startDay = an.today, type = type, startWeightKg = trend, targetWeightKg = targetKg ?: if (type == GoalType.MAINTENANCE) trend else null,
                ratePctPerWeek = if (type == GoalType.MAINTENANCE) 0.0 else (computedRate ?: 0.5), endDay = if (byDate && type != GoalType.MAINTENANCE) endDay else null,
            )
            vm.repo.startGoal(g)
            // Nouvel objectif → nouveau programme immédiat (comme « Set New Program » de MacroFactor).
            vm.repo.saveCheckIn(Targets.checkIn(an.today, an.tdeeAt(an.today), trend, an.lbmAt(an.today), an.program, g, an.profile.heightCm, java.time.LocalDate.now().year - an.profile.birthYear, an.profile.sex == app.superhealth.nutrition.domain.Sex.FEMALE))
            onBack()
        }
        current?.let { cur ->
            Spacer(Modifier.height(10.dp))
            if (cur.status == GoalStatus.IN_PROGRESS) PillButton(tr("Marquer l'objectif comme atteint"), Modifier.fillMaxWidth(), strong = false) {
                vm.repo.updateGoal(cur.copy(status = GoalStatus.ACHIEVED, closedDay = an.today)); onBack()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Parcours de check-in hebdomadaire : pesée, revue de la semaine (jeûne / incomplet), mise à jour du programme. */
@Composable
fun CheckInScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    val flags by vm.dayFlags.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    var weight by remember { mutableStateOf("") }
    val an = a ?: return
    val week = (an.today - 7 until an.today).toList()
    val byDay = an.days.associateBy { it.epochDay }
    val recentWeigh = an.days.any { it.epochDay > an.today - 7 && it.weightKg != null }
    DetailScaffold(tr("Check-in hebdomadaire"), onBack) {
        ProgressBar((step + 1) / 3f, Mf.colors.accent, height = 5.dp)
        Spacer(Modifier.height(12.dp))
        when (step) {
            0 -> {
                SectionLabel(tr("Pesée"))
                MfCard {
                    Text(if (recentWeigh) tr("Vous avez des pesées récentes. Vous pouvez en ajouter une pour aujourd'hui.")
                         else tr("Aucune pesée depuis 7 jours."),
                        style = MaterialTheme.typography.bodyMedium)
                    NumberField(tr("Poids du jour (facultatif)"), weight, { weight = it }, suffix = s.weightUnit.label.tr())
                }
                Spacer(Modifier.height(16.dp))
                PillButton(tr("Continuer"), Modifier.fillMaxWidth()) {
                    weight.toNum()?.let { w -> vm.launch { weighIns.upsert(WeighInEntity(an.today, s.toKg(w), null)) } }
                    step = 1
                }
            }
            1 -> {
                SectionLabel(tr("Revue de la semaine"))
                Text(tr("Marquez les jours de jeûne et ceux mal journalisés."),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(Modifier.height(8.dp))
                GroupCard {
                    week.forEachIndexed { i, d ->
                        val f = flags[d]
                        val kcal = byDay[d]?.intakeKcal
                        if (i > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(LocalDate.ofEpochDay(d).format(DateTimeFormatter.ofPattern("EEEE d", app.superhealth.nutrition.ui.Lang.locale)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                                Text(kcal?.let { "${s.energyValue(it).roundToInt()} ${s.energyUnit.label.tr()}" } ?: tr("Rien de journalisé"), style = MaterialTheme.typography.bodySmall,
                                    color = if (kcal == null) MaterialTheme.colorScheme.error else Mf.colors.textSecondary)
                            }
                            FlagChip(tr("Jeûne"), f?.fasted == true) { vm.launch { dayFlags.upsert((f ?: DayFlagEntity(d)).let { it.copy(fasted = !it.fasted) }) } }
                            Spacer(Modifier.width(6.dp))
                            FlagChip(tr("Incomplet"), f?.partial == true) { vm.launch { dayFlags.upsert((f ?: DayFlagEntity(d)).let { it.copy(partial = !it.partial) }) } }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                PillButton(tr("Continuer"), Modifier.fillMaxWidth()) { step = 2 }
            }
            else -> {
                val next = an.provisionalCheckIn(an.today)
                val prev = an.checkInFor(an.today - 1)
                SectionLabel(tr("Mise à jour du programme"))
                MfCard {
                    Row {
                        GoalStat("${s.energyValue(next.tdee).roundToInt()}", tr("Dépense estimée"), Modifier.weight(1f))
                        GoalStat(next.trendKg?.let { s.weight(it) } ?: "—", tr("Tendance"), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    CompareRow(tr("Calories"), prev?.kcal?.let(s::energyValue), s.energyValue(next.kcal), CaloriesColor)
                    CompareRow(tr("Protéines (g)"), prev?.protein, next.protein, ProteinColor)
                    CompareRow(tr("Lipides (g)"), prev?.fat, next.fat, FatColor)
                    CompareRow(tr("Glucides (g)"), prev?.carbs, next.carbs, CarbsColor)
                    if (next.limitedByFloor) Text(tr("Cible limitée par le plancher calorique."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    ageLimitText(next, LocalDate.now().year - an.profile.birthYear)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp)) }
                }
                Spacer(Modifier.height(16.dp))
                PillButton(tr("Appliquer le nouveau programme"), Modifier.fillMaxWidth()) { vm.repo.saveCheckIn(next); onBack() }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FlagChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(label, Modifier.clip(CircleShape).background(if (on) Mf.colors.strong else Mf.colors.chip).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium, color = if (on) Mf.colors.onStrong else Mf.colors.text)
}

@Composable
private fun CompareRow(label: String, before: Double?, after: Double, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(color); Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        before?.let { Text("${it.roundToInt()}  →  ", style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary) }
        Text("${after.roundToInt()}", style = MaterialTheme.typography.titleMedium)
        before?.let {
            val d = after - it
            if (abs(d) >= 1) Text("  ${if (d > 0) "+" else "−"}${abs(d).roundToInt()}", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
        }
    }
}

@Composable
fun ProgramEditScreen(vm: AppViewModel, onBack: () -> Unit) {
    val program by vm.program.collectAsState()
    val profile by vm.profile.collectAsState()
    val s by vm.settings.collectAsState()
    val a by vm.analytics.collectAsState()
    var p by remember { mutableStateOf(program) }
    var bmr by remember { mutableStateOf(profile.bmrEquation) }
    var staticTdee by remember { mutableStateOf(num(s.energyValue(program.staticTdee), 0)) }
    var initTdee by remember { mutableStateOf(program.initialTdeeOverride?.let { num(s.energyValue(it), 0) } ?: "") }
    var mk by remember { mutableStateOf(num(s.energyValue(program.manualKcal), 0)) }
    var mp by remember { mutableStateOf(num(program.manualProtein, 0)) }
    var mf by remember { mutableStateOf(num(program.manualFat, 0)) }
    var mc by remember { mutableStateOf(num(program.manualCarbs, 0)) }
    val goalType = a?.currentGoal?.type ?: GoalType.MAINTENANCE

    DetailScaffold(tr("Programme"), onBack) {
        MfCard {
            FieldTitle(tr("Style de programme"))
            ChipChoice(ProgramStyle.entries, p.style, { if (it == ProgramStyle.COACHED) tr("Coaché") else tr("Manuel") }) { p = p.copy(style = it) }
            Text(if (p.style == ProgramStyle.COACHED) tr("Cibles recalculées à chaque check-in.")
                 else tr("Vous fixez vos cibles ; la dépense et la progression restent suivies."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
            if (p.style == ProgramStyle.COACHED) {
                FieldTitle(tr("Diète"))
                DietPreference.entries.forEach { d ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (p.dietPreference == d) Mf.colors.chip else Color.Transparent)
                        .clickable { p = p.copy(dietPreference = d) }.padding(12.dp)) {
                        Column {
                            Text(d.label.tr(), style = MaterialTheme.typography.titleSmall)
                            Text(d.description.tr(), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                        }
                    }
                }
                FieldTitle(tr("Entraînement pendant le programme"))
                ChipChoice(Training.entries, p.training, { it.label.tr() }) { p = p.copy(training = it) }
                FieldTitle(tr("Protéines"))
                ChipChoice(ProteinPreference.entries, p.proteinPreference, { "${it.label.tr()} · ${num(Targets.proteinPerKgLbm(goalType, p.training, it), 2)}" }) {
                    p = p.copy(proteinPreference = it)
                }
                Text(tr("g par kg de masse maigre."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
                FieldTitle(tr("Plancher calorique"))
                ChipChoice(CalorieFloor.entries, p.calorieFloor, { it.label.tr() }) { p = p.copy(calorieFloor = it) }
                a?.let { an ->
                    val lbm = an.lbmAt(an.today); val h = an.profile.heightCm
                    Text(when (p.calorieFloor) {
                        CalorieFloor.NONE -> tr("Aucun plancher. À réserver à un suivi encadré.")
                        else -> trf("Plancher : %1\$s %2\$s", (s.energyValue(Targets.dynamicFloor(p.calorieFloor, h, lbm)).roundToInt()), s.energyUnit.label.tr())
                    }, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
                }
                FieldTitle(tr("Répartition des calories"))
                var shift by remember { mutableStateOf(p.highDays.isNotEmpty()) }
                ChipChoice(listOf(false, true), shift, { if (it) tr("Jours plus élevés") else tr("Uniforme") }) { shift = it; if (!it) p = p.copy(highDays = emptySet()) }
                if (shift) {
                    Text(tr("Mangez plus certains jours, même moyenne sur la semaine."),
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        (1..7).forEach { d ->
                            FlagChip(DAY_NAMES[d - 1].take(3), d in p.highDays) {
                                p = p.copy(highDays = if (d in p.highDays) p.highDays - d else p.highDays + d)
                            }
                        }
                    }
                    a?.let { an ->
                        val avg = an.provisionalCheckIn(an.today).kcal
                        val floorK = maxOf(Targets.dynamicFloor(p.calorieFloor, an.profile.heightCm, an.lbmAt(an.today)),
                            Targets.dynamicFloor(CalorieFloor.LOW, an.profile.heightCm, an.lbmAt(an.today)))
                        val dist = Targets.distribute(avg, floorK, p.highDays)
                        if (dist.shifted) {
                            val lo = dist.kcal.min(); val hi = dist.kcal.max()
                            Text(trf("Jours hauts %1\$s · autres %2\$s %3\$s", (s.energyValue(hi).roundToInt()), (s.energyValue(lo).roundToInt()), s.energyUnit.label.tr()),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(4.dp))
                        } else if (p.highDays.isNotEmpty()) Text(tr("Répartition impossible avec ce plancher : jours identiques."),
                            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
                    }
                }
                FieldTitle(tr("Jour du check-in"))
                ChipChoice((1..7).toList(), p.checkInDay, { DAY_NAMES[it - 1] }) { p = p.copy(checkInDay = it) }
            } else {
                NumberField(tr("Énergie"), mk, { mk = it }, suffix = s.energyUnit.label.tr())
                Row {
                    NumberField(tr("Protéines"), mp, { mp = it }, Modifier.weight(1f).padding(end = 6.dp), suffix = "g")
                    NumberField(tr("Lipides"), mf, { mf = it }, Modifier.weight(1f).padding(horizontal = 3.dp), suffix = "g")
                    NumberField(tr("Glucides"), mc, { mc = it }, Modifier.weight(1f).padding(start = 6.dp), suffix = "g")
                }
            }
        }
        SectionLabel(tr("Dépense énergétique"))
        MfCard {
            ChipChoice(TdeeMode.entries, p.tdeeMode, { it.label.tr() }) { p = p.copy(tdeeMode = it) }
            if (p.tdeeMode == TdeeMode.STATIC) NumberField(tr("Dépense fixe"), staticTdee, { staticTdee = it }, suffix = s.energyUnit.label.tr())
            FieldTitle(tr("Équation du métabolisme de base"))
            ChipChoice(BmrEquation.entries, bmr, { it.label.tr() }) { bmr = it }
            NumberField(tr("Dépense initiale imposée (facultatif)"), initTdee, { initTdee = it }, suffix = s.energyUnit.label.tr())
        }
        Spacer(Modifier.height(16.dp))
        PillButton(tr("Enregistrer et mettre à jour les cibles"), Modifier.fillMaxWidth()) {
            val newP = p.copy(
                staticTdee = staticTdee.toNum()?.let { s.toKcal(it) } ?: p.staticTdee,
                initialTdeeOverride = initTdee.toNum()?.let { s.toKcal(it) },
                manualKcal = mk.toNum()?.let { s.toKcal(it) } ?: p.manualKcal,
                manualProtein = mp.toNum() ?: p.manualProtein, manualFat = mf.toNum() ?: p.manualFat, manualCarbs = mc.toNum() ?: p.manualCarbs,
            )
            vm.repo.prefs.setProgram(newP)
            vm.repo.prefs.setProfile(profile.copy(bmrEquation = bmr))
            a?.let { an ->
                val w = an.currentTrendKg ?: Analytics.DEFAULT_WEIGHT
                vm.repo.saveCheckIn(Targets.checkIn(an.today, an.tdeeAt(an.today), w, an.lbmAt(an.today), newP, an.currentGoal, an.profile.heightCm, java.time.LocalDate.now().year - an.profile.birthYear, an.profile.sex == app.superhealth.nutrition.domain.Sex.FEMALE))
            }
            onBack()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel, onBack: () -> Unit) {
    val profile by vm.profile.collectAsState()
    var pr by remember { mutableStateOf(profile) }
    var birthYear by remember { mutableStateOf(profile.birthYear.toString()) }
    var height by remember { mutableStateOf(num(profile.heightCm, 0)) }
    var bf by remember { mutableStateOf(profile.bodyFatPct?.let { num(it, 1) } ?: "") }
    DetailScaffold(tr("Profil"), onBack) {
        MfCard {
            FieldTitle(tr("Sexe"))
            ChipChoice(Sex.entries, pr.sex, { if (it == Sex.MALE) tr("Homme") else tr("Femme") }) { pr = pr.copy(sex = it) }
            NumberField(tr("Année de naissance"), birthYear, { birthYear = it }, keyboard = KeyboardType.Number)
            NumberField(tr("Taille"), height, { height = it }, suffix = "cm")
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(tr("Masse grasse de référence (facultatif)"), bf, { bf = it }, Modifier.weight(1f), suffix = "%")
                bf.toNum()?.let { BodyFatSilhouette(pr.sex, it, Modifier.padding(start = 12.dp).size(72.dp)) }
            }
            Text(trf("Sans valeur : %1\$s %% (valeur par défaut).", (if (pr.sex == Sex.MALE) 25 else 35)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
            FieldTitle(tr("Activité hors entraînement"))
            ChipChoice(Activity.entries, pr.activity, { it.label.tr() }) { pr = pr.copy(activity = it) }
            FieldTitle(tr("Séances de sport par semaine"))
            ChipChoice(ExerciseLevel.entries, pr.exercise, { it.label.tr() }) { pr = pr.copy(exercise = it) }
            FieldTitle(tr("Profil sportif"))
            ChipChoice(Athlete.entries, pr.athlete, { it.label.tr() }) { pr = pr.copy(athlete = it) }
            FieldTitle(tr("Évolution récente du poids"))
            ChipChoice(WeightChange.entries, pr.weightChange, { it.label.tr() }) { pr = pr.copy(weightChange = it) }
            FieldTitle(tr("Historique de poids"))
            ChipChoice(LifetimeWeight.entries, pr.lifetimeWeight, { it.label.tr() }) { pr = pr.copy(lifetimeWeight = it) }
        }
        Spacer(Modifier.height(16.dp))
        PillButton(tr("Enregistrer"), Modifier.fillMaxWidth()) {
            vm.repo.prefs.setProfile(pr.copy(birthYear = birthYear.toIntOrNull() ?: pr.birthYear, heightCm = height.toNum() ?: pr.heightCm, bodyFatPct = bf.toNum()))
            onBack()
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Pourquoi l'âge a retenu la cible (Senior) : une phrase, ou null. */
internal fun ageLimitText(ci: app.superhealth.nutrition.domain.CheckIn, age: Int): String? = when (ci.ageLimit) {
    "NOT_OBESE" -> noCutText(age)
    "CAPPED" -> trf("Déficit limité à %1\$s kcal/j à votre âge, pour préserver le muscle.",
        app.superhealth.nutrition.domain.Senior.rules(age).maxDeficit?.roundToInt() ?: 0)
    else -> null
}

/** Perte non proposée : seuil d'IMC de la tranche d'âge (Senior). */
internal fun noCutText(age: Int): String = trf("À votre âge, la perte de poids n'est conseillée qu'à partir d'un IMC de %1\$s : vos calories restent au maintien.",
    num(app.superhealth.nutrition.domain.Senior.rules(age).loseFromBmi, 1))

/** Cible trop basse : dit ce qui la borne, l'IMC plancher ou la perte maximale par objectif (Senior). */
internal fun minTargetText(age: Int, heightCm: Double, startKg: Double, weight: (Double) -> String): String {
    val r = app.superhealth.nutrition.domain.Senior.rules(age)
    val min = app.superhealth.nutrition.domain.Senior.minTargetKg(age, heightCm, startKg)
    val byFloor = app.superhealth.nutrition.domain.Senior.weightAtBmi(r.floorBmi, heightCm) >= min - 0.01
    return if (byFloor) trf("Visez au moins %1\$s (IMC %2\$s) : en dessous, le risque augmente.", weight(min), num(r.floorBmi, 1))
    else trf("À votre âge, perdez au plus %1\$s %% par objectif : visez au moins %2\$s, puis refaites le point.", ((r.maxLoss ?: 0.0) * 100).roundToInt(), weight(min))
}
