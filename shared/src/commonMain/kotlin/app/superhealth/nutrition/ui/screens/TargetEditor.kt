package app.superhealth.nutrition.ui.screens

import app.superhealth.platform.*

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.domain.*
import app.superhealth.nutrition.ui.*
import app.superhealth.nutrition.ui.components.*
import app.superhealth.ui.theme.Mf
import kotlin.math.roundToInt

/** Un seul sens de calcul actif : énergie + répartition, ou grammes → énergie. */
@Composable
fun TargetEditor(initial: DayTarget, settings: Settings, onChange: (DayTarget?) -> Unit) {
    var byCalories by remember { mutableStateOf(true) }
    var kcal by remember { mutableStateOf(num(settings.energyValue(initial.kcal), 0)) }
    val initialEnergy = (4 * initial.protein + 9 * initial.fat + 4 * initial.carbs).coerceAtLeast(1.0)
    var protein by remember { mutableStateOf(num(initial.protein, 1)) }
    var fat by remember { mutableStateOf(num(initial.fat, 1)) }
    var carbs by remember { mutableStateOf(num(initial.carbs, 1)) }
    var pPct by remember { mutableStateOf(num(400 * initial.protein / initialEnergy, 1)) }
    var fPct by remember { mutableStateOf(num(900 * initial.fat / initialEnergy, 1)) }
    // Complément exact après arrondi des deux premières parts.
    var cPct by remember { mutableStateOf(num(100 - (pPct.toNum() ?: 30.0) - (fPct.toNum() ?: 30.0), 1)) }
    val draft = runCatching {
        if (byCalories) ManualTargets.fromCalories(settings.toKcal(requireNotNull(kcal.toNum())),
            requireNotNull(pPct.toNum()), requireNotNull(fPct.toNum()), requireNotNull(cPct.toNum()))
        else ManualTargets.fromMacros(requireNotNull(protein.toNum()), requireNotNull(fat.toNum()), requireNotNull(carbs.toNum()))
    }.getOrNull()?.takeIf { it.kcal > 0 }
    val changed by rememberUpdatedState(onChange)
    LaunchedEffect(draft) { changed(draft) }
    ChipChoice(listOf(true, false), byCalories, { if (it) tr("Calories → macros") else tr("Macros → calories") }) { mode ->
        draft?.let { d ->
            kcal = num(settings.energyValue(d.kcal), 0)
            protein = num(d.protein, 1); fat = num(d.fat, 1); carbs = num(d.carbs, 1)
            pPct = num(400 * d.protein / d.kcal, 1); fPct = num(900 * d.fat / d.kcal, 1)
            cPct = num(100 - (pPct.toNum() ?: 0.0) - (fPct.toNum() ?: 0.0), 1)
        }
        byCalories = mode
    }
    if (byCalories) {
        NumberField(tr("Énergie"), kcal, { kcal = it }, suffix = settings.energyUnit.label)
        Text(tr("Répartition de l'énergie (total : 100 %)"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(tr("Protéines"), if (byCalories) pPct else protein, { if (byCalories) pPct = it else protein = it }, Modifier.weight(1f), suffix = if (byCalories) "%" else "g")
        NumberField(tr("Lipides"), if (byCalories) fPct else fat, { if (byCalories) fPct = it else fat = it }, Modifier.weight(1f), suffix = if (byCalories) "%" else "g")
        NumberField(tr("Glucides"), if (byCalories) cPct else carbs, { if (byCalories) cPct = it else carbs = it }, Modifier.weight(1f), suffix = if (byCalories) "%" else "g")
    }
    Text(draft?.let { "${settings.energyValue(it.kcal).roundToInt()} ${settings.energyUnit.label} · P ${num(it.protein, 1)} g · L ${num(it.fat, 1)} g · G ${num(it.carbs, 1)} g" }
        ?: tr("Saisissez des valeurs positives et une répartition totale de 100 %."),
        style = MaterialTheme.typography.bodySmall, color = if (draft == null) MaterialTheme.colorScheme.error else Mf.colors.textSecondary)
}

@Composable
fun TargetOverrideScreen(vm: AppViewModel, onBack: () -> Unit) {
    val an = vm.analytics.collectAsState().value ?: return
    val settings by vm.settings.collectAsState()
    val today = an.today
    var duration by remember { mutableStateOf(7) }
    val base = an.targetFor(today).macros.let { DayTarget(it.kcal, it.protein, it.fat, it.carbs) }
    var draft by remember { mutableStateOf<DayTarget?>(base) }
    DetailScaffold(tr("Remplacer mes cibles"), onBack) {
        MfCard {
            Text(tr("Un remplacement s'applique dès aujourd'hui, avant les cibles du programme. Le programme reprend ensuite automatiquement."), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            ChipChoice(listOf(1, 7, 0), duration, { when (it) { 1 -> tr("Aujourd'hui"); 7 -> tr("7 jours"); else -> tr("Sans date de fin") } }) { duration = it }
            Spacer(Modifier.height(12.dp))
            TargetEditor(base, settings) { draft = it }
        }
        Spacer(Modifier.height(16.dp))
        PillButton(tr("Appliquer le remplacement"), Modifier.fillMaxWidth(), enabled = draft != null) {
            draft?.let { t -> vm.repo.prefs.setProgram(ManualTargets.replaceOverride(an.program, TargetOverride(today, if (duration == 0) null else today + duration - 1, t))) }
            onBack()
        }
        if (an.program.overrides.any { it.includes(today) }) {
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Reprendre les cibles du programme"), Modifier.fillMaxWidth(), strong = false) {
                vm.repo.prefs.setProgram(an.program.copy(overrides = an.program.overrides.mapNotNull {
                    if (!it.includes(today)) it else if (it.startDay < today) it.copy(endDay = today - 1) else null
                }))
                onBack()
            }
        }
    }
}
