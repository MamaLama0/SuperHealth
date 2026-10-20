package app.superhealth.nutrition.ui.screens

import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.data.FoodEntity
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.domain.Settings
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.FoodIcon
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.PercentPill
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.Ring
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.hourMinuteLabel
import app.superhealth.nutrition.ui.macros
import app.superhealth.nutrition.ui.minuteOf
import app.superhealth.nutrition.ui.num
import app.superhealth.ui.theme.CaloriesColor
import app.superhealth.ui.theme.CarbsColor
import app.superhealth.ui.theme.FatColor
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.ProteinColor
import app.superhealth.nutrition.ui.toKcal
import app.superhealth.nutrition.ui.toNum
import kotlin.math.roundToInt

/** Bloc « 33 Calories | 17 % 1,4 Protéines (g) | … » de la fiche aliment MacroFactor. */
@Composable
fun NutrientHeader(m: Macros, s: Settings) {
    val kcalFromMacros = (m.protein * 4 + m.fat * 9 + m.carbs * 4).coerceAtLeast(1.0)
    fun pct(g: Double, f: Int) = (g * f / kcalFromMacros * 100).roundToInt()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1.3f)) {
            Text(s.energyValue(m.kcal).roundToInt().toString(), style = MaterialTheme.typography.displayMedium)
            Text(s.energyUnit.label.tr().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        MacroColumn(pct(m.protein, 4), m.protein, tr("Protéines (g)"), ProteinColor, Modifier.weight(1f))
        MacroColumn(pct(m.fat, 9), m.fat, tr("Lipides (g)"), FatColor, Modifier.weight(1f))
        MacroColumn(pct(m.carbs, 4), m.carbs, tr("Glucides (g)"), CarbsColor, Modifier.weight(1f))
    }
}

@Composable
private fun MacroColumn(pct: Int, grams: Double, label: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        PercentPill(pct, color)
        Spacer(Modifier.height(4.dp))
        Text(num(grams, if (grams < 10) 1 else 0), style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
    }
}

/** « Impact sur les cibles » : part de la cible du jour couverte par cet aliment. */
@Composable
fun TargetImpact(m: Macros, target: Macros?) {
    if (target == null) return
    Text(tr("Impact sur les cibles"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        listOf(
            Triple(tr("Calories"), m.kcal / target.kcal, CaloriesColor), Triple(tr("Protéines"), m.protein / target.protein, ProteinColor),
            Triple(tr("Lipides"), m.fat / target.fat, FatColor), Triple(tr("Glucides"), m.carbs / target.carbs, CarbsColor),
        ).forEach { (label, r, c) ->
            val f = if (r.isFinite()) r else 0.0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Ring(f.toFloat(), c, 58.dp, stroke = 5.dp) { Text("${(f * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SheetHeader(name: String, subtitle: String?, onClose: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FoodIcon(name, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
        }
        trailing()
        Box(Modifier.size(36.dp).clip(CircleShape).background(Mf.colors.chip).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, tr("Fermer"), Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RoundAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(6.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Mf.colors.chip), contentAlignment = Alignment.Center) { Icon(icon, null) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Fiche d'une entrée du journal (consultation, quantité, heure, suppression). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(
    e: FoodEntryEntity, target: Macros?, s: Settings, onDismiss: () -> Unit, onDelete: () -> Unit, onDuplicate: () -> Unit,
    onUpdate: (FoodEntryEntity) -> Unit, onChangeTime: () -> Unit, onCopy: () -> Unit = {},
) {
    var qty by remember { mutableStateOf(e.grams?.let { num(it, if (it < 10) 1 else 0) } ?: "") }
    val newGrams = qty.toNum()?.takeIf { it > 0 }
    val factor = if (e.grams != null && e.grams > 0 && newGrams != null) newGrams / e.grams else 1.0
    val m = e.macros().let { Macros(it.kcal * factor, it.protein * factor, it.fat * factor, it.carbs * factor) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Mf.colors.card, dragHandle = null) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding()) {
            SheetHeader(e.name, trf("Journalisé à %1\$s", (hourMinuteLabel(e.minuteOf()))), onDismiss)
            Spacer(Modifier.height(20.dp))
            NutrientHeader(m, s)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction(tr("Supprimer"), Icons.Rounded.RemoveCircleOutline, onDelete)
                RoundAction(tr("Dupliquer"), Icons.Rounded.ContentPaste, onDuplicate)
                RoundAction(tr("Copier"), Icons.Rounded.ContentCopy, onCopy)
                RoundAction(tr("Heure"), Icons.Rounded.Schedule, onChangeTime)
            }
            if (e.grams != null) {
                NumberField(tr("Quantité"), qty, { qty = it }, suffix = "g")
                PillButton(tr("Enregistrer la quantité"), Modifier.fillMaxWidth().padding(top = 8.dp), enabled = newGrams != null && factor != 1.0) {
                    onUpdate(e.copy(grams = newGrams, kcal = e.kcal * factor, protein = e.protein * factor, fat = e.fat * factor, carbs = e.carbs * factor, fiber = e.fiber * factor))
                }
            }
            TargetImpact(m, target)
            MicrosSection(app.superhealth.nutrition.data.Micros.parse(e.microsJson).mapValues { it.value * factor })
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Unités de saisie de la quantité : grammes, portions, ou énergie (« log by calories »). */
private enum class QtyUnit { GRAMS, SERVINGS, ENERGY }

/**
 * Fiche d'un aliment avant ajout : quantité en grammes, en portions ou en calories (la quantité est déduite de l'énergie
 * voulue, comme « Log by Calories » de MacroFactor), aperçu des macros et de l'impact.
 * Recette : [onExplode] journalise les ingrédients séparément, [onEdit] ouvre l'éditeur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSheet(
    food: FoodEntity, target: Macros?, s: Settings, onDismiss: () -> Unit, onToggleFavorite: (() -> Unit)?,
    addLabel: String = tr("Ajouter au journal"), onExplode: ((Double) -> Unit)? = null, onEdit: (() -> Unit)? = null,
    /** Dernière quantité utilisée pour cet aliment (en grammes) : proposée à la place de la portion par défaut. */
    lastGrams: (suspend () -> Double?)? = null, onAdd: (Double) -> Unit,
) {
    val hasServing = food.servingGrams != 100.0
    var unit by remember { mutableStateOf(if (hasServing) QtyUnit.SERVINGS else QtyUnit.GRAMS) }
    var qty by remember { mutableStateOf(if (hasServing) "1" else "100") }
    if (lastGrams != null) LaunchedEffect(food) {
        val g = lastGrams() ?: return@LaunchedEffect
        // Portion définie et quantité qui en est un multiple « rond » : en portions, sinon en grammes.
        val servings = g / food.servingGrams
        if (hasServing && kotlin.math.abs(servings * 4 - kotlin.math.round(servings * 4)) < 0.01) qty = num(servings, 2)
        else { unit = QtyUnit.GRAMS; qty = num(g, 0) }
    }
    var fav by remember { mutableStateOf(food.favorite) }
    val grams = qty.toNum()?.let {
        when (unit) {
            QtyUnit.SERVINGS -> it * food.servingGrams
            QtyUnit.GRAMS -> it
            QtyUnit.ENERGY -> if (food.kcal100 > 0) s.toKcal(it) / food.kcal100 * 100 else null
        }
    }?.takeIf { it > 0 }
    val k = (grams ?: 0.0) / 100
    val m = Macros(food.kcal100 * k, food.protein100 * k, food.fat100 * k, food.carbs100 * k)
    val isRecipe = food.source == app.superhealth.nutrition.data.Repository.RECIPE
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Mf.colors.card, dragHandle = null) {
      Column(Modifier.navigationBarsPadding()) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp)) {
            val subtitle = listOf(food.brand, when { food.source == "off" -> tr("Open Food Facts"); isRecipe -> trf("Recette · %1\$s portion(s)", (num(food.recipeServings ?: 1.0, 0))); else -> "" })
            SheetHeader(food.name, subtitle.filter { it.isNotBlank() }.joinToString(" · "), onDismiss) {
                if (onEdit != null) Box(Modifier.padding(end = 8.dp).size(36.dp).clip(CircleShape).background(Mf.colors.chip).clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Edit, tr("Modifier"), Modifier.size(20.dp)) }
                if (onToggleFavorite != null) Box(Modifier.padding(end = 8.dp).size(36.dp).clip(CircleShape).background(Mf.colors.chip)
                    .clickable { fav = !fav; onToggleFavorite() }, contentAlignment = Alignment.Center) {
                    Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, tr("Favori"), Modifier.size(20.dp), tint = if (fav) FatColor else Mf.colors.text)
                }
            }
            Spacer(Modifier.height(20.dp))
            NutrientHeader(m, s)
            Spacer(Modifier.height(16.dp))
            val units = listOfNotNull(QtyUnit.SERVINGS.takeIf { hasServing }, QtyUnit.GRAMS, QtyUnit.ENERGY.takeIf { food.kcal100 > 0 })
            ChipChoice(units, unit, {
                when (it) {
                    QtyUnit.SERVINGS -> trf("%1\$s (%2\$s g)", food.servingName, (num(food.servingGrams, 0)))
                    QtyUnit.GRAMS -> "grammes"
                    QtyUnit.ENERGY -> s.energyUnit.label.tr()
                }
            }) { u ->
                // conserve la quantité équivalente lors du changement d'unité
                val g = grams
                unit = u
                qty = when {
                    g == null -> if (u == QtyUnit.SERVINGS) "1" else "100"
                    u == QtyUnit.SERVINGS -> num(g / food.servingGrams, 2)
                    u == QtyUnit.GRAMS -> num(g, 0)
                    else -> num(s.energyValue(food.kcal100 * g / 100), 0)
                }
            }
            NumberField(when (unit) { QtyUnit.SERVINGS -> tr("Nombre de portions"); QtyUnit.GRAMS -> tr("Quantité"); QtyUnit.ENERGY -> tr("Énergie voulue") },
                qty, { qty = it }, suffix = when (unit) { QtyUnit.SERVINGS -> tr("×"); QtyUnit.GRAMS -> "g"; QtyUnit.ENERGY -> s.energyUnit.label.tr() })
            if (unit == QtyUnit.ENERGY && grams != null) Text(trf("= %1\$s g", (num(grams, 0))), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp))
            TargetImpact(m, target)
            MicrosSection(app.superhealth.nutrition.data.Micros.parse(food.microsJson).mapValues { it.value * k })
            if (food.source == app.superhealth.nutrition.data.SwissFoods.SOURCE) Text(trf("Source : %1\$s", app.superhealth.nutrition.data.SwissFoods.CREDIT),
                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary, modifier = Modifier.padding(top = 8.dp))
        }
        // Bouton fixé en bas de la fiche, comme dans MacroFactor
        Column(Modifier.fillMaxWidth().background(Mf.colors.card).padding(horizontal = 20.dp, vertical = 12.dp)) {
            PillButton(addLabel, Modifier.fillMaxWidth(), enabled = grams != null) { onAdd(grams!!) }
            if (onExplode != null) PillButton(tr("Journaliser les ingrédients séparément"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false,
                enabled = grams != null) { onExplode(grams!!) }
        }
      }
    }
}

/** Nutriments détaillés groupés (glucides, lipides, vitamines, minéraux) avec % de l'apport de référence. */
@Composable
fun MicrosSection(values: Map<String, Double>) {
    if (values.isEmpty()) return
    val defs = app.superhealth.nutrition.data.Micros.defs.filter { (values[it.code] ?: 0.0) > 0 }
    if (defs.isEmpty()) return
    Text(tr("Nutriments détaillés"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
    defs.groupBy { it.category }.forEach { (cat, list) ->
        Text(tr(cat), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        list.forEach { d ->
            val v = values.getValue(d.code)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.label.tr(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${num(v, if (v < 10) 2 else 0)} ${d.unit}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                d.reference?.let { Text("  ${(v / it * 100).roundToInt()} %", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary,
                    modifier = Modifier.width(48.dp)) }
            }
        }
    }
}
