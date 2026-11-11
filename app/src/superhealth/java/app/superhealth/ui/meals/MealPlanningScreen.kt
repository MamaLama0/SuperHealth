package app.superhealth.ui.meals

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.meals.MealPlanner
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.energy
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.nutrition.ui.screens.MealHoursDialog
import app.superhealth.nutrition.ui.screens.MealSlot
import app.superhealth.ui.Lang
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun MealPlanningScreen(vm: AppViewModel, nav: NavHostController, addOnOpen: Boolean = false) {
    val context = LocalContext.current
    val today = LocalDate.now().toEpochDay()
    var selected by rememberSaveable { mutableLongStateOf(vm.selectedDay.value) }
    val plans by MealPlanner.plans.collectAsState()
    val settings by vm.settings.collectAsState()
    val preferences by SuperPrefs.settings.collectAsState()
    val meals = plans[selected]?.meals.orEmpty()
    val editable = selected >= today
    val free = MealPlanner.slotOrder.firstOrNull { slot -> meals.none { it.slot == slot } }
    var composer by rememberSaveable(selected) { mutableStateOf<Int?>(null) }
    var startAdding by rememberSaveable { mutableStateOf(addOnOpen) }
    LaunchedEffect(startAdding) {
        if (startAdding) {
            startAdding = false
            if (editable) composer = free
        }
    }
    var copyTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var hours by rememberSaveable { mutableStateOf(false) }
    var message by remember(selected) { mutableStateOf<String?>(null) }
    val format = DateTimeFormatter.ofPattern("EEE d MMM", Lang.locale)
    val time = DateTimeFormatter.ofPattern("HH:mm", Lang.locale)
    val bounds = remember(selected) { mutableStateMapOf<Int, Rect>() }
    var dragged by remember(selected) { mutableStateOf<Int?>(null) }
    var pointer by remember(selected) { mutableStateOf(Offset.Zero) }
    var shift by remember(selected) { mutableStateOf(Offset.Zero) }
    val dropTarget = dragged?.let { bounds.entries.firstOrNull { (_, box) -> box.contains(pointer) }?.key }

    fun change(action: () -> Unit) {
        message = null
        runCatching(action)
            .onFailure { message = it.message ?: tr("Impossible d’enregistrer le repas.") }
    }
    fun chooseDate(initial: Long, onDate: (Long) -> Unit) {
        val date = LocalDate.ofEpochDay(initial)
        DatePickerDialog(context, { _, year, month, day ->
            onDate(LocalDate.of(year, month + 1, day).toEpochDay())
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    fun copy(from: Long, to: Long, slots: Set<Int>? = null) = change {
        val count = MealPlanner.copyDay(from, to, slots)
        if (count > 0) selected = to
        message = if (count == 0) tr("Aucun créneau libre : les repas déjà prévus ont été conservés.")
            else trf("%1\$s repas copiés", count)
    }

    DetailScaffold(tr("Planning des repas"), { nav.popBackStack() }, action = {
        IconButton({ composer = free }, enabled = editable && free != null) {
            Icon(Icons.Rounded.Add, tr("Planifier le repas"))
        }
    }, contentSpacing = 8.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ selected-- }) { Text(tr("Précédent")) }
            TextButton({ chooseDate(selected) { selected = it } }) { Text(LocalDate.ofEpochDay(selected).format(format)) }
            TextButton({ selected++ }) { Text(tr("Suivant")) }
        }
        MealPlanner.slotOrder.forEachIndexed { index, slot ->
            val meal = meals.firstOrNull { it.slot == slot }
            val window = MealPlanner.window(slot, preferences.mealEnds)
            val end = window.last + 1
            val label = tr(MealSlot.entries[index].label)
            val range = LocalTime.ofSecondOfDay(window.first * 60L).format(time) + " – " +
                (if (end == 1440) "24:00" else LocalTime.ofSecondOfDay(end * 60L).format(time))
            val shape = RoundedCornerShape(16.dp)
            val moving = dragged == slot
            Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).zIndex(if (moving) 1f else 0f)
                .onGloballyPositioned { bounds[slot] = it.boundsInRoot() }
                .background(if (dropTarget == slot && !moving) Mf.colors.accent.copy(alpha = .20f) else Mf.colors.card, shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.widthIn(min = 94.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Text(range, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                }
                if (meal == null) {
                    Spacer(Modifier.weight(1f))
                    if (editable) IconButton({ composer = slot }) { Icon(Icons.Rounded.Add, tr("Planifier le repas")) }
                } else {
                    Row(Modifier.weight(1f).background(Mf.colors.elevated, shape).padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).graphicsLayer {
                            if (moving) { translationX = shift.x; translationY = shift.y }
                        }.background(Mf.colors.elevated, shape).padding(vertical = 6.dp)) {
                            Text(meal.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text(settings.energy(meal.macros.kcal), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                        }
                        if (editable && !meal.logged) {
                            var origin by remember { mutableStateOf(Offset.Zero) }
                            Box(Modifier.size(36.dp).onGloballyPositioned { origin = it.positionInRoot() }
                                .pointerInput(selected, slot) {
                                    detectDragGestures(
                                        onDragStart = { local ->
                                            pointer = origin + local
                                            shift = Offset.Zero
                                            dragged = slot
                                        },
                                        onDrag = { event, delta ->
                                            event.consume()
                                            pointer += delta
                                            shift += delta
                                        },
                                        onDragEnd = {
                                            val destination = bounds.entries.firstOrNull { (_, box) -> box.contains(pointer) }?.key
                                            if (destination != null) change { MealPlanner.move(selected, slot, destination) }
                                            dragged = null
                                        },
                                        onDragCancel = { dragged = null },
                                    )
                                }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.DragHandle, tr("Glisser le repas"), Modifier.size(20.dp))
                            }
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton({ menu = true }, Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.MoreVert, tr("Options du repas"), Modifier.size(20.dp))
                                }
                                DropdownMenu(menu, { menu = false }) {
                                    MealPlanner.slotOrder.forEachIndexed { destinationIndex, destination ->
                                        if (destination != slot) DropdownMenuItem({
                                            Text(trf("Déplacer vers %1\$s", tr(MealSlot.entries[destinationIndex].label)))
                                        }, onClick = {
                                            menu = false
                                            change { MealPlanner.move(selected, slot, destination) }
                                        }, enabled = meals.none { it.slot == destination && it.logged })
                                    }
                                    DropdownMenuItem({ Text(tr("Retirer le repas")) }, onClick = {
                                        menu = false
                                        change { MealPlanner.remove(selected, slot) }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
        if (meals.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ copy(selected, selected + 1) }, enabled = selected + 1 >= today) { Text(tr("Copier vers demain")) }
                TextButton({ copyTarget = maxOf(selected + 1, today) }) { Text(tr("Choisir les repas à copier")) }
            }
        }
        if (editable && plans[selected - 1]?.meals?.isNotEmpty() == true) {
            TextButton({ copy(selected - 1, selected) }) { Text(tr("Reprendre les repas de la veille")) }
        }
        TextButton({ hours = true }) { Text(tr("Horaires des repas")) }
        TextButton({ vm.selectedDay.value = selected; nav.popBackStack() }) { Text(tr("Ouvrir ce jour dans le journal")) }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }

    composer?.let { slot ->
        ComposeSheet(selected, emptyList(), null, { settings.energy(it) }, onDismiss = { composer = null },
            onManual = { chosen, foods ->
                change {
                    MealPlanner.planFoods(selected, chosen, foods)
                    composer = null
                }
            }, initialSlot = slot)
    }
    if (hours) MealHoursDialog { hours = false }
    copyTarget?.let { to ->
        val occupied = plans[to]?.meals.orEmpty().map { it.slot }.toSet()
        var chosen by remember(selected) { mutableStateOf(meals.map { it.slot }.toSet()) }
        val available = chosen - occupied
        AlertDialog(
            onDismissRequest = { copyTarget = null },
            title = { Text(tr("Copier les repas")) },
            text = {
                Column {
                    TextButton({ chooseDate(to) { copyTarget = it } }) {
                        Text(tr("Destination") + " · " + LocalDate.ofEpochDay(to).format(format))
                    }
                    meals.forEach { meal ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
            confirmButton = { TextButton({ copy(selected, to, available); copyTarget = null },
                enabled = selected != to && to >= today && available.isNotEmpty()) { Text(tr("Copier")) } },
            dismissButton = { TextButton({ copyTarget = null }) { Text(tr("Annuler")) } },
        )
    }
}
