package app.superhealth.nutrition.ui.screens

import app.superhealth.ui.go
import app.superhealth.ui.theme.container
import androidx.compose.runtime.LaunchedEffect
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.Lang
import app.superhealth.ui.theme.CaloriesColor
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.MoreHoriz
import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.navigation.NavHostController
import app.superhealth.nutrition.data.DayFlagEntity
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.SearchMode
import app.superhealth.nutrition.ui.components.AssetImage
import app.superhealth.nutrition.ui.components.FoodIcon
import app.superhealth.nutrition.ui.components.MacroLine
import app.superhealth.nutrition.ui.components.MacroSummary
import app.superhealth.nutrition.ui.components.Ring
import app.superhealth.nutrition.ui.dayLabel
import app.superhealth.nutrition.ui.energy
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.hourLabel
import app.superhealth.nutrition.ui.macros
import app.superhealth.nutrition.ui.minuteOf
import app.superhealth.nutrition.ui.num
import app.superhealth.nutrition.ui.openSearch
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.dashCardBackground
import java.time.LocalDate
import java.time.LocalTime

private val DAY_LETTERS get() = (1..7).map { app.superhealth.nutrition.ui.dayLetter(it) }

@Composable
fun FoodLogScreen(vm: AppViewModel, nav: NavHostController) {
    val day by vm.selectedDay.collectAsState()
    val entries by vm.dayEntries.collectAsState()
    val a by vm.analytics.collectAsState()
    val s by vm.settings.collectAsState()
    val flags by vm.dayFlags.collectAsState()
    var openEntry by remember { mutableStateOf<FoodEntryEntity?>(null) }
    var menu by remember { mutableStateOf(false) }
    // nutriments du jour : dépliés depuis le résumé (toucher) ou le menu, cachés par défaut
    var nutrients by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<Long>()) }
    // dernière suppression, annulable quelques secondes
    var undo by remember { mutableStateOf<List<FoodEntryEntity>>(emptyList()) }
    LaunchedEffect(undo) { if (undo.isNotEmpty()) { kotlinx.coroutines.delay(5_000); undo = emptyList() } }
    fun delete(list: List<FoodEntryEntity>) { vm.launch { list.forEach { this.entries.delete(it.id) } }; undo = list }
    val clipboard by vm.clipboard.collectAsState()
    val ctx = LocalContext.current

    val total = remember(entries) { entries.fold(Macros.ZERO) { acc, e -> acc + e.macros() } }
    val mealEnds = app.superhealth.SuperPrefs.settings.collectAsState().value.mealEnds
    var mealHours by remember { mutableStateOf(false) }
    val meals = remember(entries, mealEnds) { entries.sortedBy { it.minuteOf() }.groupBy { MealSlot.of(it.minuteOf()) }.toSortedMap() }
    val target = a?.targetFor(day)?.macros
    val flag = flags[day]
    // calories de la semaine affichée, lues dans les repas (les analyses ne couvrent que les jours depuis le début du programme)
    val intakeByDay by vm.weekIntake.collectAsState()

    if (mealHours) MealHoursDialog { mealHours = false }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                    // grand titre du jour (toucher : revenir à aujourd'hui), calendrier et options en boutons ronds
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null) { vm.selectedDay.value = LocalDate.now().toEpochDay() }) {
                            app.superhealth.ui.theme.Overline(LocalDate.ofEpochDay(day).format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", app.superhealth.ui.Lang.locale))
                                .replaceFirstChar { it.uppercase(app.superhealth.ui.Lang.locale) })
                            Text(tr("Journal"), style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            app.superhealth.nutrition.ui.components.CircleAction(Icons.Rounded.CalendarMonth, tr("Calendrier")) {
                                val d = LocalDate.ofEpochDay(day)
                                DatePickerDialog(ctx, { _, y, m, dd -> vm.selectedDay.value = LocalDate.of(y, m + 1, dd).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                            }
                            Box {
                                app.superhealth.nutrition.ui.components.CircleAction(Icons.Rounded.MoreHoriz, tr("Options du jour")) { menu = true }
                                DropdownMenu(menu, { menu = false }, containerColor = Mf.colors.elevated, shape = RoundedCornerShape(14.dp)) {
                                    if (clipboard.isNotEmpty()) DropdownMenuItem({ Text(trf("Coller %1\$s élément(s)", clipboard.size)) }, onClick = {
                                        menu = false; val items = clipboard; vm.launch { paste(items, day) }
                                    })
                                    DropdownMenuItem({ Text(tr("Horaires des repas")) }, onClick = { menu = false; mealHours = true })
                                    DropdownMenuItem({ Text(tr("Nutriments du jour")) }, enabled = entries.isNotEmpty(), onClick = { menu = false; nutrients = !nutrients })
                                    DropdownMenuItem({ Text(tr("Copier la journée")) }, enabled = entries.isNotEmpty(), onClick = { menu = false; vm.clipboard.value = entries })
                                    DropdownMenuItem({ Text(tr("Copier la veille ici")) }, onClick = { menu = false; vm.launch { copyDay(day - 1, day) } })
                                    DropdownMenuItem({ Text(if (flag?.fasted == true) tr("Annuler « jour de jeûne »") else tr("Marquer comme jour de jeûne")) }, onClick = {
                                        menu = false; vm.launch { dayFlags.upsert((flag ?: DayFlagEntity(day)).let { it.copy(fasted = !it.fasted) }) }
                                    })
                                    DropdownMenuItem({ Text(if (flag?.partial == true) tr("Marquer le journal comme complet") else tr("Marquer le journal comme incomplet")) }, onClick = {
                                        menu = false; vm.launch { dayFlags.upsert((flag ?: DayFlagEntity(day)).let { it.copy(partial = !it.partial) }) }
                                    })
                                    DropdownMenuItem({ Text(tr("Aller à aujourd'hui")) }, onClick = { menu = false; vm.selectedDay.value = LocalDate.now().toEpochDay() })
                                }
                            }
                            app.superhealth.nutrition.ui.components.SettingsAvatar { nav.go("settings") }
                        }
                    }
                    WeekStrip(day, onSelect = { vm.selectedDay.value = it }) { d ->
                        val t = a?.targetFor(d)?.macros?.kcal
                        val v = if (d == day) total.kcal else intakeByDay[d]
                        if (t == null || v == null || t <= 0) 0f else (v / t).toFloat()
                    }
                    Spacer(Modifier.height(12.dp))
                    val box = RoundedCornerShape(app.superhealth.ui.theme.CardRadius)
                    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(box).dashCardBackground(app.superhealth.ui.theme.CaloriesColor)
                        .clickable(enabled = entries.isNotEmpty()) { nutrients = !nutrients }.padding(14.dp)) {
                        app.superhealth.nutrition.ui.components.EnergySummary(total, target, { s.energyValue(it) }, tr(s.energyUnit.label))
                    }
                }
            }
            // plugin Planificateur de repas : repas restants pour tomber sur les cibles
            item(key = "mealplan") {
                app.superhealth.ui.meals.MealPlanCard(day, entries, target, { s.energy(it) }, Modifier.padding(horizontal = 14.dp).padding(top = 12.dp))
            }
            // 2.2 : vitamines, minéraux, caféine… du jour
            if (nutrients && entries.isNotEmpty()) item(key = "nutrients") {
                DayNutrients(entries, s, Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), nav) { nav.navigate("nutrients") }
            }
            if (selection.isNotEmpty()) item {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Mf.colors.strong)
                    .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(trf("%1\$s sélectionné(s)", selection.size), color = Mf.colors.onStrong, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.clipboard.value = entries.filter { it.id in selection }; selection = emptySet() }) {
                        Icon(Icons.Rounded.ContentCopy, tr("Copier"), tint = Mf.colors.onStrong)
                    }
                    IconButton(onClick = { val ids = selection; delete(entries.filter { it.id in ids }); selection = emptySet() }) {
                        Icon(Icons.Rounded.Delete, tr("Supprimer"), tint = Mf.colors.onStrong)
                    }
                    IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Rounded.Close, tr("Annuler"), tint = Mf.colors.onStrong) }
                }
            }
            if (clipboard.isNotEmpty() && selection.isEmpty()) item {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Mf.colors.card)
                    .padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp), tint = Mf.colors.textSecondary)
                    Text(trf("  %1\$s élément(s) copié(s)", clipboard.size), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { val items = clipboard; vm.launch { paste(items, day) } }) { Text(tr("Coller ici"), color = Mf.colors.accent) }
                    IconButton(onClick = { vm.clipboard.value = emptyList() }) { Icon(Icons.Rounded.Close, tr("Vider"), Modifier.size(18.dp)) }
                }
            }
            if (flag?.fasted == true || flag?.partial == true) item {
                Text(
                    if (flag.fasted) tr("Jour de jeûne : compté comme 0 kcal dans la dépense.") else tr("Journal incomplet : ce jour est exclu du calcul de la dépense."),
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mf.colors.accent.copy(alpha = 0.15f)).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (entries.isEmpty()) item { EmptyLog(onAdd = { nav.openSearch() }) }
            // repas déduits de l'heure de chaque aliment (petit-déjeuner, déjeuner, collation, dîner), dans l'ordre du jour
            val groups = meals
            groups.forEach { (slot, list) ->
                item(key = "m${slot.ordinal}") {
                    MealGroup(slot, list, { s.energyValue(it) }, tr(s.energyUnit.label), selection,
                        onAdd = { nav.openSearch(SearchMode.SEARCH, list.maxOf { it.minuteOf() }) },
                        onOpen = { if (selection.isEmpty()) openEntry = it else selection = selection.toggle(it.id) },
                        onLongPress = { selection = selection.toggle(it.id) })
                }
            }
        }

        SearchPill(Modifier.align(Alignment.BottomCenter), onSearch = { nav.openSearch() }, onScan = { nav.openSearch(SearchMode.SCAN) },
            onAi = if (s.aiEnabled) ({ nav.navigate("ai") }) else null)
        if (undo.isNotEmpty()) {
            val gone = undo
            Row(Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 74.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(Mf.colors.text).padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (gone.size == 1) tr("Aliment supprimé") else trf("%1\$s aliments supprimés", gone.size), color = Mf.colors.background,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { vm.launch { gone.forEach { this.entries.insert(it) } }; undo = emptyList() }) {
                    Text(tr("Annuler"), color = Mf.colors.accent.container(1.5f), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    openEntry?.let { e ->
        EntrySheet(
            e, target, s, onDismiss = { openEntry = null },
            onDelete = { delete(listOf(e)); openEntry = null },
            onDuplicate = { vm.launch { this.entries.insert(e.copy(id = 0, createdAt = System.currentTimeMillis())) }; openEntry = null },
            onCopy = { vm.clipboard.value = listOf(e); openEntry = null },
            onUpdate = { updated -> vm.launch { this.entries.upsertAll(listOf(updated)) }; openEntry = null },
            onChangeTime = {
                val m = e.minuteOf()
                TimePickerDialog(ctx, { _, h, min -> vm.launch { this.entries.upsertAll(listOf(e.copy(minuteOfDay = h * 60 + min))) } }, m / 60, m % 60, true).show()
                openEntry = null
            },
        )
    }
}

/**
 * Semaine du jour choisi : initiale du jour, numéro entouré de l'anneau des calories mangées ; le jour choisi est plein,
 * aujourd'hui en couleur d'accent. Glisser change de semaine.
 */
@Composable
private fun WeekStrip(day: Long, onSelect: (Long) -> Unit, progress: (Long) -> Float) {
    val today = LocalDate.now().toEpochDay()
    val monday = day - LocalDate.ofEpochDay(day).dayOfWeek.value + 1
    var drag by remember { mutableStateOf(0f) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).pointerInput(day) {
        detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
            if (drag < -60f) onSelect(day + 7) else if (drag > 60f) onSelect(day - 7)
        }) { _, d -> drag += d }
    }, horizontalArrangement = Arrangement.SpaceBetween) {
        (0 until 7).forEach { i ->
            val d = monday + i
            val sel = d == day
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(d) }.padding(vertical = 4.dp)) {
                Text(DAY_LETTERS[i], style = MaterialTheme.typography.labelMedium, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Mf.colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Ring(if (d <= today) progress(d) else 0f, app.superhealth.ui.theme.CaloriesColor, 40.dp, stroke = 3.dp, track = Mf.colors.chip) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (sel) Mf.colors.accent else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text(LocalDate.ofEpochDay(d).dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, fontSize = 15.sp,
                            color = when { sel -> Mf.colors.onStrong; d == today -> Mf.colors.accent; d > today -> Mf.colors.textSecondary; else -> Mf.colors.text })
                    }
                }
            }
        }
    }
}

/** Repas, d'après l'heure : bornes fixes, la couleur de la pastille de l'en-tête. */
internal enum class MealSlot(val label: String, val short: String, val color: () -> Color) {
    BREAKFAST("Petit-déjeuner", "Petit-déj.", { app.superhealth.ui.theme.Base.Yellow }),
    LUNCH("Déjeuner", "Déjeuner", { app.superhealth.ui.theme.Base.Teal }),
    SNACK("Collation", "Collation", { app.superhealth.ui.theme.Base.Purple }),
    DINNER("Dîner", "Dîner", { app.superhealth.ui.theme.Base.Blue });
    companion object {
        /** Repas d'une minute du jour, selon les horaires réglés (Réglages ou menu du Journal). */
        fun of(minute: Int): MealSlot {
            val ends = app.superhealth.SuperPrefs.settings.value.mealEnds
            return entries[ends.indexOfFirst { minute < it }.takeIf { it >= 0 } ?: 3]
        }
    }
}

/** Horaires des repas : la fin de chacun, le dîner allant jusqu'à minuit. Chaque fin reste entre ses voisines. */
@Composable
internal fun MealHoursDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val ends = app.superhealth.SuperPrefs.settings.collectAsState().value.mealEnds
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, containerColor = Mf.colors.card, title = { Text(tr("Horaires des repas")) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = Mf.colors.accent) } },
        text = {
            Column {
                MealSlot.entries.forEachIndexed { i, slot ->
                    val from = if (i == 0) 0 else ends[i - 1]
                    val to = ends.getOrNull(i)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (to != null) Modifier.clickable {
                        android.app.TimePickerDialog(ctx, { _, h, m ->
                            val low = from + 15; val high = (ends.getOrNull(i + 1) ?: 1440) - 15
                            val v = (h * 60 + m).coerceIn(low, high)
                            app.superhealth.SuperPrefs.update { st -> st.copy(mealEnds = st.mealEnds.toMutableList().also { it[i] = v }) }
                        }, to / 60, to % 60, true).show()
                    } else Modifier).padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(slot.color()))
                        Spacer(Modifier.width(10.dp))
                        Text(tr(slot.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("%d:%02d – %s".format(from / 60, from % 60, to?.let { "%d:%02d".format(it / 60, it % 60) } ?: "0:00"), style = MaterialTheme.typography.titleSmall,
                            color = if (to != null) Mf.colors.accent else Mf.colors.textSecondary)
                    }
                }
            }
        })
}

/** Un repas : pastille, nom, heure du premier aliment, total ; aliments avec quantité et macros, kcal à droite. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealGroup(
    slot: MealSlot, list: List<FoodEntryEntity>, energy: (Double) -> Double, unit: String, selection: Set<Long>, onAdd: () -> Unit,
    onOpen: (FoodEntryEntity) -> Unit, onLongPress: (FoodEntryEntity) -> Unit,
) {
    val sum = list.fold(Macros.ZERO) { acc, e -> acc + e.macros() }
    val first = list.minOf { it.minuteOf() }
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius))
        .background(Mf.colors.card).border(1.dp, Mf.colors.line, RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(slot.color()))
            Spacer(Modifier.width(9.dp))
            Text(tr(slot.label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text("%d:%02d".format(first / 60, first % 60), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.weight(1f))
            Text("${energy(sum.kcal).roundToInt()} $unit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Box(Modifier.padding(start = 6.dp).size(30.dp).clip(CircleShape).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, trf("Ajouter à %1\$s", tr(slot.label)), Modifier.size(18.dp), tint = Mf.colors.textSecondary)
            }
        }
        list.forEach { e ->
            val sel = e.id in selection
            Row(Modifier.fillMaxWidth().background(if (sel) Mf.colors.accent.copy(alpha = 0.14f) else Color.Transparent)
                .combinedClickable(onClick = { onOpen(e) }, onLongClick = { onLongPress(e) })
                .padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (sel) Box(Modifier.size(40.dp).clip(CircleShape).background(Mf.colors.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White)
                } else FoodIcon(e.name, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val m = e.macros()
                    // la quantité ; les macros seulement sans quantité (le détail est au toucher)
                    Text(e.grams?.let { trf("%1\$s g", num(it, if (it < 10) 1 else 0)) } ?: trf("%1\$sP %2\$sL %3\$sG", m.protein.roundToInt(), m.fat.roundToInt(), m.carbs.roundToInt()),
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(energy(e.macros().kcal).roundToInt().toString(), style = MaterialTheme.typography.titleSmall, color = Mf.colors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyLog(onAdd: () -> Unit) {
    app.superhealth.nutrition.ui.components.EmptyState(Icons.Rounded.RestaurantMenu, tr("Rien de journalisé"),
        tr("Ajoutez un aliment avec la recherche ou le bouton +."), action = tr("Ajouter un aliment"), onAction = onAdd)
}

@Composable
private fun SearchPill(modifier: Modifier, onSearch: () -> Unit, onScan: () -> Unit, onAi: (() -> Unit)?) {
    // flotte au-dessus de la liste (qui défile dessous et s'efface en bas), toujours à portée de pouce
    Row(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Mf.colors.background.copy(alpha = 0f), Mf.colors.background.copy(alpha = 0.92f))))
        .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(52.dp).shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = if (Mf.colors.dark) 0.6f else 0.16f),
                spotColor = Color.Black.copy(alpha = if (Mf.colors.dark) 0.6f else 0.12f))
                .clip(CircleShape).background(if (Mf.colors.dark) Mf.colors.elevated else Mf.colors.card).border(1.dp, Mf.colors.line, CircleShape)
                .clickable(onClick = onSearch).padding(start = 16.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Mf.colors.textSecondary)
            Spacer(Modifier.width(10.dp))
            Text(tr("Rechercher un aliment"), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (onAi != null) Icon(Icons.Rounded.AutoAwesome, tr("Décrire avec l'IA"), tint = Mf.colors.accent, modifier = Modifier.clip(CircleShape).clickable(onClick = onAi).padding(6.dp))
            Box(Modifier.padding(start = 4.dp).size(40.dp).clip(CircleShape).background(if (Mf.colors.dark) Mf.colors.chip else Mf.colors.elevated).clickable(onClick = onScan),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.QrCodeScanner, tr("Scanner"), tint = Mf.colors.text, modifier = Modifier.size(20.dp)) }
        }
    }
}

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

fun nowMinute(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
