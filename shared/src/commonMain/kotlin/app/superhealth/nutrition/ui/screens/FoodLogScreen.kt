package app.superhealth.nutrition.ui.screens

import app.superhealth.ui.theme.container
import androidx.compose.runtime.LaunchedEffect
import app.superhealth.platform.*

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
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.superhealth.platform.LocalDate
import app.superhealth.platform.LocalTime

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
    var selection by remember { mutableStateOf(setOf<Long>()) }
    // dernière suppression, annulable quelques secondes
    var undo by remember { mutableStateOf<List<FoodEntryEntity>>(emptyList()) }
    LaunchedEffect(undo) { if (undo.isNotEmpty()) { kotlinx.coroutines.delay(5_000); undo = emptyList() } }
    fun delete(list: List<FoodEntryEntity>) { vm.launch { list.forEach { this.entries.delete(it.id) } }; undo = list }
    val clipboard by vm.clipboard.collectAsState()
    val ctx = LocalContext.current

    val total = entries.fold(Macros.ZERO) { acc, e -> acc + e.macros() }
    val target = a?.targetFor(day)?.macros
    val flag = flags[day]
    val intakeByDay = remember(a) { a?.days?.associate { it.epochDay to (it.intakeKcal ?: 0.0) } ?: emptyMap() }

    Box(Modifier.fillMaxSize().background(Mf.colors.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                    // grand titre du jour (toucher : revenir à aujourd'hui), calendrier et options en boutons ronds
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null) { vm.selectedDay.value = LocalDate.now().toEpochDay() }) {
                            Text(tr("Journal"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
                            Text(dayLabel(day), style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            app.superhealth.nutrition.ui.components.SettingsAvatar { nav.navigate("settings") }
                        }
                    }
                    WeekStrip(day, onSelect = { vm.selectedDay.value = it }) { d ->
                        val t = a?.targetFor(d)?.macros?.kcal
                        val v = if (d == day) total.kcal else intakeByDay[d]
                        if (t == null || v == null || t <= 0) 0f else (v / t).toFloat()
                    }
                    Spacer(Modifier.height(12.dp))
                    MfCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                        MacroSummary(total, target, { s.energyValue(it) })
                    }
                }
            }
            // plugin Planificateur de repas : repas restants pour tomber sur les cibles
            item(key = "mealplan") {
                app.superhealth.ui.meals.MealPlanCard(day, entries, target, { s.energy(it) }, Modifier.padding(horizontal = 14.dp).padding(top = 12.dp))
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
            val groups = entries.groupBy { it.minuteOf() / 60 }.toSortedMap()
            groups.forEach { (hour, list) ->
                item(key = "h${hour}") {
                    HourGroup(hour, list, { s.energyValue(it) }, selection, onAdd = { nav.openSearch(SearchMode.SEARCH, hour * 60) },
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(d) }
                .padding(vertical = 4.dp)) {
                Text(DAY_LETTERS[i], style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Mf.colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Ring(if (d <= today) progress(d) else 0f, CaloriesColor, 40.dp, stroke = 2.5.dp, track = Mf.colors.chip) {
                    Box(Modifier.size(31.dp).clip(CircleShape).background(if (sel) Mf.colors.accent else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text(LocalDate.ofEpochDay(d).dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, fontSize = 15.sp,
                            color = when { sel -> Mf.colors.onStrong; d == today -> Mf.colors.accent; else -> Mf.colors.text })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HourGroup(
    hour: Int, list: List<FoodEntryEntity>, energy: (Double) -> Double, selection: Set<Long>, onAdd: () -> Unit,
    onOpen: (FoodEntryEntity) -> Unit, onLongPress: (FoodEntryEntity) -> Unit,
) {
    val sum = list.fold(Macros.ZERO) { acc, e -> acc + e.macros() }
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 14.dp, top = 14.dp)) {
        Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hourLabel(hour), Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.titleSmall, color = Mf.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(26.dp).clip(CircleShape).background(Mf.colors.card).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, trf("Ajouter à %1\$s h", hour), Modifier.size(16.dp), tint = Mf.colors.textSecondary)
            }
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.End) {
                MacroLine(sum, energy, style = MaterialTheme.typography.labelMedium, color = Mf.colors.text)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).background(Mf.colors.card)) {
                list.forEachIndexed { i, e ->
                    if (i > 0) Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(androidx.compose.ui.unit.Dp.Hairline).background(Mf.colors.divider))
                    val sel = e.id in selection
                    Row(Modifier.fillMaxWidth().background(if (sel) Mf.colors.accent.copy(alpha = 0.14f) else Color.Transparent)
                        .combinedClickable(onClick = { onOpen(e) }, onLongClick = { onLongPress(e) })
                        .padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (sel) Box(Modifier.size(40.dp).clip(CircleShape).background(Mf.colors.accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, tint = Color.White)
                        } else FoodIcon(e.name, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            MacroLine(e.macros(), energy, suffix = e.grams?.let { trf("%1\$s g", (num(it, if (it < 10) 1 else 0))) })
                        }
                    }
                }
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
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(50.dp).shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.14f))
                .clip(CircleShape).background(if (Mf.colors.dark) Mf.colors.elevated else Mf.colors.card).clickable(onClick = onSearch).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Mf.colors.textSecondary)
            Spacer(Modifier.width(10.dp))
            Text(tr("Rechercher un aliment"), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (onAi != null) Icon(Icons.Rounded.AutoAwesome, tr("Décrire avec l'IA"), tint = Mf.colors.accent, modifier = Modifier.clip(CircleShape).clickable(onClick = onAi).padding(6.dp))
            Icon(Icons.Rounded.QrCodeScanner, tr("Scanner"), tint = Mf.colors.accent, modifier = Modifier.clip(CircleShape).clickable(onClick = onScan).padding(6.dp))
        }
    }
}

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

fun nowMinute(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
