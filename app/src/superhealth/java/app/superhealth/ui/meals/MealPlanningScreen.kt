package app.superhealth.ui.meals

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import app.superhealth.meals.MealPlanner
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.energy
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.Lang
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun MealPlanningScreen(vm: AppViewModel, nav: NavHostController) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableLongStateOf(vm.selectedDay.value) }
    var first by rememberSaveable { mutableLongStateOf(selected) }
    val plans by MealPlanner.plans.collectAsState()
    val analytics by vm.analytics.collectAsState()
    val settings by vm.settings.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var copySource by rememberSaveable { mutableStateOf<Long?>(null) }
    var copyTarget by rememberSaveable { mutableLongStateOf(selected) }
    fun chooseDate(initial: Long, onDate: (Long) -> Unit) {
        val date = LocalDate.ofEpochDay(initial)
        android.app.DatePickerDialog(context, { _, year, month, day ->
            onDate(LocalDate.of(year, month + 1, day).toEpochDay())
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    fun copy(from: Long, to: Long, slots: Set<Int>? = null) {
        runCatching { MealPlanner.copyDay(from, to, slots) }.onSuccess { count ->
            message = if (count == 0) tr("Aucun créneau libre : les repas déjà prévus ont été conservés.")
                else trf("%1\$s repas copiés", count)
            if (count > 0) { selected = to; if (to !in first..first + 6) first = to }
        }.onFailure { message = it.message }
    }
    val format = remember { DateTimeFormatter.ofPattern("EEE d MMM", Lang.locale) }
    DetailScaffold(tr("Planning des repas"), { nav.popBackStack() }) {
        Text(tr("Préparez vos repas, puis ajoutez-les au journal quand vous les mangez."))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ first -= 7; selected = first }) { Text(tr("Précédent")) }
            TextButton({ chooseDate(selected) { first = it; selected = it } }) { Text(tr("Choisir une date")) }
            TextButton({ first += 7; selected = first }) { Text(tr("Suivant")) }
        }
        (first..first + 6).forEach { day ->
            val meals = plans[day]?.meals.orEmpty()
            OutlinedButton({ selected = day }, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text((if (day == selected) "• " else "") + LocalDate.ofEpochDay(day).format(format))
                    Text(if (meals.isEmpty()) tr("Aucun repas prévu") else "${meals.size} " + tr("repas") + " · " + settings.energy(meals.sumOf { it.macros.kcal }), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (day == selected) {
                MealPlanCard(day, analytics?.targetFor(day)?.macros, { settings.energy(it) })
                if (meals.isNotEmpty()) {
                    FilledTonalButton({ copy(day, day + 1) }, Modifier.fillMaxWidth()) { Text(tr("Copier vers demain")) }
                    OutlinedButton({ copySource = day; copyTarget = day + 1 }, Modifier.fillMaxWidth()) { Text(tr("Choisir les repas à copier")) }
                }
                if (plans[day - 1]?.meals?.isNotEmpty() == true) TextButton({ copy(day - 1, day) }) {
                    Text(tr("Reprendre les repas de la veille"))
                }
                TextButton({ vm.selectedDay.value = day; nav.popBackStack() }) { Text(tr("Ouvrir ce jour dans le journal")) }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    copySource?.let { from ->
        val meals = plans[from]?.meals.orEmpty()
        val occupied = plans[copyTarget]?.meals.orEmpty().map { it.slot }.toSet()
        var chosen by remember(from) { mutableStateOf(meals.map { it.slot }.toSet()) }
        val available = chosen - occupied
        AlertDialog(
            onDismissRequest = { copySource = null },
            title = { Text(tr("Copier les repas")) },
            text = {
                Column {
                    TextButton({ chooseDate(copyTarget) { copyTarget = it } }) {
                        Text(tr("Destination") + " · " + LocalDate.ofEpochDay(copyTarget).format(format))
                    }
                    meals.forEach { meal ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(meal.slot in available, { checked ->
                                chosen = if (checked) chosen + meal.slot else chosen - meal.slot
                            }, enabled = meal.slot !in occupied)
                            Column {
                                Text(meal.title)
                                if (meal.slot in occupied) Text(tr("Créneau déjà prévu"), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ copy(from, copyTarget, available); copySource = null }, enabled = from != copyTarget && available.isNotEmpty()) {
                Text(tr("Copier"))
            } },
            dismissButton = { TextButton({ copySource = null }) { Text(tr("Annuler")) } },
        )
    }
}
