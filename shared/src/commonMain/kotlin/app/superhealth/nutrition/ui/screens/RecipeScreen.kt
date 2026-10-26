package app.superhealth.nutrition.ui.screens

import app.superhealth.platform.*

import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.data.FoodEntity
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.RecipeDraft
import app.superhealth.nutrition.ui.SearchMode
import app.superhealth.nutrition.ui.components.FoodIcon
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MacroLine
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.TextInput
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.num
import app.superhealth.ui.theme.Mf
import app.superhealth.nutrition.ui.toNum
import kotlinx.coroutines.launch

/**
 * Éditeur de recette (RecipeWizardService de MacroFactor) : nom, portions, poids final facultatif (cuisson),
 * ingrédients ajoutés depuis la recherche. Les valeurs par portion sont recalculées en direct.
 */
@Composable
fun RecipeEditScreen(vm: AppViewModel, onBack: () -> Unit, onAddIngredient: (String) -> Unit) {
    val draftState by vm.recipeDraft.collectAsState()
    val s by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val d = draftState ?: RecipeDraft()
    fun update(f: (RecipeDraft) -> RecipeDraft) { vm.recipeDraft.value = f(vm.recipeDraft.value ?: RecipeDraft()) }
    val servings = d.servings.toNum()?.takeIf { it > 0 } ?: 1.0
    val total = d.items.fold(Macros.ZERO) { acc, it -> acc + Macros(it.kcal, it.protein, it.fat, it.carbs) }
    val perServing = Macros(total.kcal / servings, total.protein / servings, total.fat / servings, total.carbs / servings)
    val rawGrams = d.items.sumOf { it.grams }

    DetailScaffold(if (d.id == 0L) tr("Nouvelle recette") else tr("Modifier la recette"), onBack = { vm.recipeDraft.value = null; onBack() }) {
        MfCard {
            TextInput(tr("Nom"), d.name, { v -> update { it.copy(name = v) } }, placeholder = tr("Ex. : Porridge du matin"))
            Row {
                NumberField(tr("Portions"), d.servings, { v -> update { it.copy(servings = v) } }, Modifier.weight(1f).padding(end = 6.dp), suffix = tr("×"))
                NumberField(tr("Poids final (facultatif)"), d.totalGrams, { v -> update { it.copy(totalGrams = v) } }, Modifier.weight(1f).padding(start = 6.dp), suffix = "g")
            }
            Text(trf("Poids cuit, si connu. Sinon : somme des ingrédients (%1\$s g).", (num(rawGrams, 0))),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
        }
        SectionLabel(tr("Par portion"))
        MfCard { NutrientHeader(perServing, s) }
        SectionLabel(trf("Ingrédients (%1\$s)", d.items.size))
        GroupCard {
            d.items.forEachIndexed { i, it ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FoodIcon(it.name, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(it.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MacroLine(Macros(it.kcal, it.protein, it.fat, it.carbs), { v -> s.energyValue(v) }, suffix = trf("%1\$s g", (num(it.grams, 0))))
                    }
                    IconButton(onClick = { update { r -> r.copy(items = r.items.filterIndexed { j, _ -> j != i }) } }) {
                        Icon(Icons.Rounded.Close, tr("Retirer"), tint = Mf.colors.textSecondary)
                    }
                }
            }
            if (d.items.isNotEmpty()) RowDivider()
            Row(Modifier.fillMaxWidth().clickable { onAddIngredient("search/${SearchMode.INGREDIENT}/-1") }.padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Mf.colors.chip), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null) }
                Spacer(Modifier.width(12.dp))
                Text(tr("Ajouter un ingrédient"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        PillButton(tr("Enregistrer la recette"), Modifier.fillMaxWidth(), enabled = d.name.isNotBlank() && d.items.isNotEmpty()) {
            scope.launch {
                vm.repo.saveRecipe(
                    FoodEntity(id = d.id, name = d.name.trim(), favorite = d.favorite, kcal100 = 0.0, protein100 = 0.0, fat100 = 0.0, carbs100 = 0.0),
                    d.items, servings, d.totalGrams.toNum(),
                )
                vm.recipeDraft.value = null
                onBack()
            }
        }
        if (d.id != 0L) PillButton(tr("Supprimer la recette"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false, icon = Icons.Rounded.Delete) {
            scope.launch { vm.repo.deleteRecipe(d.id); vm.recipeDraft.value = null; onBack() }
        }
        Spacer(Modifier.height(24.dp))
    }
}
