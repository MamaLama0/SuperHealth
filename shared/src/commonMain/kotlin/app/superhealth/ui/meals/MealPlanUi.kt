package app.superhealth.ui.meals

import app.superhealth.platform.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.backup.Secrets
import app.superhealth.meals.MealPlanner
import app.superhealth.nutrition.data.FoodEntity
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.platform.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Teinte du plugin (comme sa fiche dans Réglages > Plugins). */
val MealsHue = Color(0xFFF2994A)

/** Photos de recettes déjà chargées : la carte ne clignote pas quand le journal défile. */
private val photos = HashMap<String, Bitmap>()

/**
 * Carte « Repas prévus », en haut du journal quand le plugin est actif : propose les repas qui restent pour finir la journée
 * sur les cibles, chacun s'ajoute au journal d'un geste.
 */
@Composable
fun MealPlanCard(day: Long, entries: List<FoodEntryEntity>, target: Macros?, energy: (Double) -> String, modifier: Modifier = Modifier) {
    val installed by app.superhealth.modules.Modules.installed.collectAsState()
    if (installed.none { it.kind == app.superhealth.modules.Modules.MEALS && it.enabled }) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val plans by MealPlanner.plans.collectAsState()
    val plan = plans[day]
    var busy by remember(day) { mutableStateOf(false) }
    var error by remember(day) { mutableStateOf<String?>(null) }
    var open by remember(day) { mutableStateOf<Int?>(null) }
    var compose by remember(day) { mutableStateOf(false) }
    var note by remember(day) { mutableStateOf<String?>(null) }
    /** Repas en cours de remplacement (« Autre idée »). */
    var swapping by remember(day) { mutableStateOf<Int?>(null) }
    fun propose() {
        if (target == null) return
        busy = true; error = null
        scope.launch {
            runCatching { MealPlanner.generate(ctx, day, target, entries) }.onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Mf.colors.card).animateContentSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(MealsHue.container()), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.RestaurantMenu, null, Modifier.size(18.dp), tint = MealsHue)
            }
            Spacer(Modifier.width(10.dp))
            Text(tr("Repas prévus"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MealsHue)
                plan != null -> {
                    IconButton(onClick = { propose() }) { Icon(Icons.Rounded.Refresh, tr("Autres repas"), tint = Mf.colors.textSecondary) }
                    IconButton(onClick = { MealPlanner.clear(day) }) { Icon(Icons.Rounded.Close, tr("Retirer"), tint = Mf.colors.textSecondary) }
                }
            }
        }
        if (plan == null) {
            Text(tr("Des repas pour finir la journée pile sur vos cibles."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
            PillButton(if (busy) tr("Préparation…") else tr("Proposer mes repas"), Modifier.fillMaxWidth(), enabled = !busy && target != null,
                icon = Icons.Rounded.AutoAwesome) { propose() }
            Spacer(Modifier.height(8.dp))
            PillButton(tr("Avec mes aliments"), Modifier.fillMaxWidth(), strong = false, enabled = !busy && target != null,
                icon = Icons.Rounded.Kitchen) { compose = true }
        } else {
            // ce que le plan apporte face à ce qui reste à manger
            target?.let { t ->
                val planned = plan.meals.filter { !it.logged }.sumOf { it.macros.kcal }
                val left = MealPlanner.left(t, entries).kcal
                if (planned > 0) Text(trf("Prévu %1\$s · reste %2\$s", energy(planned), energy(left)), style = MaterialTheme.typography.bodySmall,
                    color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            val now = { LocalTime.now().let { it.hour * 60 + it.minute } }
            plan.meals.forEachIndexed { i, m ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Mf.colors.divider))
                MealRow(m, open == i, energy, swapping = swapping == m.slot, onToggle = { open = if (open == i) null else i },
                    onPortion = { f -> MealPlanner.setPortion(day, m.slot, f) },
                    onSwap = if (target != null && !m.logged && swapping == null) ({
                        swapping = m.slot; error = null
                        scope.launch {
                            runCatching { MealPlanner.regenerate(ctx, day, m.slot, target, entries) }.onFailure { error = it.message ?: it.javaClass.simpleName }
                            swapping = null
                        }
                    }) else null,
                    onUnlog = if (m.logged && m.entryIds.isNotEmpty()) ({ scope.launch { MealPlanner.unlog(day, m.slot) } }) else null) {
                    scope.launch { MealPlanner.log(day, m, now()) }
                }
            }
            val todo = plan.meals.filter { !it.logged }
            if (todo.size > 1) PillButton(tr("Tout ajouter au journal"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false, icon = Icons.Rounded.Add) {
                scope.launch { todo.forEach { MealPlanner.log(day, it, now()) } }
            }
            Text(tr("Composer avec mes aliments"), style = MaterialTheme.typography.labelLarge, color = MealsHue.onContainer(),
                modifier = Modifier.padding(top = 6.dp).clip(CircleShape).clickable(enabled = target != null) { compose = true }.padding(vertical = 6.dp))
            if (plan.provider == "spoonacular") Text(tr("Recettes : spoonacular"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary,
                modifier = Modifier.padding(top = 6.dp).clickable { openUrl("https://spoonacular.com/food-api") })
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp)) }
        error?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = app.superhealth.ui.theme.Base.Red, modifier = Modifier.padding(top = 8.dp)) }
    }
    if (compose && target != null) ComposeSheet(entries, target, energy, onDismiss = { compose = false }) { slot, foods, goal ->
        val (meal, r) = MealPlanner.compose(day, slot, foods, goal)
        open = MealPlanner.plans.value[day]?.meals?.indexOf(meal)?.takeIf { it >= 0 }
        note = if (r.reachable) null else tr("Ces aliments ne suffisent pas pour la cible : portions au plus près.")
        error = null
        compose = false
    }
}

/** Créneau proposé par défaut : le prochain repas encore vide d'après l'heure, sinon le prochain d'après l'heure. */
private fun defaultSlot(slots: List<MealPlanner.Slot>, entries: List<FoodEntryEntity>): Int {
    val now = LocalTime.now().let { it.hour * 60 + it.minute }
    val used = entries.map { it.meal }.toSet()
    val order = slots.indices.sortedBy { slots[it].minute }
    // un repas reste « à venir » jusqu'à 90 min après son heure
    val ahead = order.filter { slots[it].minute + 90 >= now }
    return ahead.firstOrNull { it !in used } ?: ahead.firstOrNull() ?: order.lastOrNull { it !in used } ?: order.last()
}

/**
 * Composer un repas : on choisit le moment de la journée puis des aliments (bibliothèque, base suisse si activée) ;
 * les grammes sont calculés sur l'appareil pour que le repas tombe sur sa part des calories restantes.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ComposeSheet(entries: List<FoodEntryEntity>, target: Macros, energy: (Double) -> String, onDismiss: () -> Unit,
                         onDone: (Int, List<FoodEntity>, Macros) -> Unit) {
    val ctx = LocalContext.current
    val slots = MealPlanner.content()?.slots.orEmpty()
    if (slots.isEmpty()) { onDismiss(); return }
    var slot by remember { mutableStateOf(defaultSlot(slots, entries)) }
    var query by remember { mutableStateOf("") }
    val chosen = remember { androidx.compose.runtime.mutableStateListOf<FoodEntity>() }
    val recent by app.superhealth.nutrition.NutriApp.repository.foods.recent(30).collectAsState(emptyList())
    val swiss = app.superhealth.nutrition.NutriApp.repository.prefs.settings.collectAsState().value.swissDbEnabled
    val found by produceState<List<FoodEntity>?>(null, query) {
        val q = query.trim()
        if (q.length < 2) { value = null; return@produceState }
        kotlinx.coroutines.delay(250)
        value = withContext(Dispatchers.IO) {
            app.superhealth.nutrition.NutriApp.repository.foods.search(q).take(15) +
                (if (swiss) app.superhealth.nutrition.data.SwissFoods.search(ctx, q, 15).map(app.superhealth.nutrition.data.SwissFoods::toFood) else emptyList())
        }
    }
    val goal = MealPlanner.slotGoal(target, entries, slot)
    fun key(f: FoodEntity) = if (f.id != 0L) "id${f.id}" else "n" + f.name + f.brand
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 18.dp).navigationBarsPadding().imePadding()) {
            Text(tr("Composer un repas"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            app.superhealth.nutrition.ui.components.ChipChoice(slots.indices.toList(), slot, { slots[it].name.text }) { slot = it }
            goal?.let { g ->
                Text(trf("Cible : %1\$s", energy(g.kcal)) + " · " + trf("P %1\$s · L %2\$s · G %3\$s", formatNumber(g.protein), formatNumber(g.fat), formatNumber(g.carbs)),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            }
            if (chosen.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chosen.forEach { f ->
                        Row(Modifier.clip(CircleShape).background(MealsHue.container()).clickable { chosen.remove(f) }.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(f.name, style = MaterialTheme.typography.labelLarge, color = MealsHue.onContainer(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp))
                            Icon(Icons.Rounded.Close, tr("Retirer"), Modifier.padding(start = 4.dp).size(16.dp), tint = MealsHue.onContainer())
                        }
                    }
                }
            }
            app.superhealth.nutrition.ui.components.TextInput("", query, { query = it }, placeholder = tr("Chercher un aliment"))
            val list = (found ?: recent).filter { f -> chosen.none { key(it) == key(f) } }.distinctBy { key(it) }.take(25)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
                if (found == null && list.isNotEmpty()) item { Text(tr("Récents"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 6.dp)) }
                if (found != null && list.isEmpty()) item { Text(tr("Aucun aliment trouvé."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 10.dp)) }
                items(list.size) { i ->
                    val f = list[i]
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = chosen.size < 8) { chosen.add(f); query = "" }
                        .padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        app.superhealth.nutrition.ui.components.FoodIcon(f.name, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(energy(f.kcal100) + " · " + tr("100 g"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                        }
                        Icon(Icons.Rounded.Add, tr("Ajouter"), tint = MealsHue)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Calculer les portions"), Modifier.fillMaxWidth(), enabled = chosen.isNotEmpty() && goal != null && goal.kcal > 0) {
                onDone(slot, chosen.toList(), goal!!)
            }
        }
    }
}

@Composable
private fun MealRow(m: MealPlanner.Meal, open: Boolean, energy: (Double) -> String, swapping: Boolean, onToggle: () -> Unit,
                    onPortion: (Double) -> Unit, onSwap: (() -> Unit)?, onUnlog: (() -> Unit)?, onLog: () -> Unit) {
    val ctx = LocalContext.current
    val mm = m.macros
    val slot = MealPlanner.content()?.slots?.getOrNull(m.slot)?.name?.text ?: ""
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(slot, m.minutes?.let { trf("%1\$s min", it) }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MealsHue.onContainer())
                Text(if (swapping) tr("Nouvelle idée…") else m.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    maxLines = if (open) 3 else 1, overflow = TextOverflow.Ellipsis, color = if (swapping) Mf.colors.textTertiary else Mf.colors.text)
                Text(energy(mm.kcal) + " · " + trf("P %1\$s · L %2\$s · G %3\$s", formatNumber(mm.protein), formatNumber(mm.fat), formatNumber(mm.carbs)),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            // ajouté : toucher la coche retire le repas du journal
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (m.logged) MealsHue else MealsHue.container())
                .clickable(enabled = !swapping && (!m.logged || onUnlog != null), onClick = if (m.logged) onUnlog ?: {} else onLog), contentAlignment = Alignment.Center) {
                Icon(if (m.logged) Icons.Rounded.Check else Icons.Rounded.Add, if (m.logged) tr("Retirer du journal") else tr("Ajouter au journal"),
                    Modifier.size(20.dp), tint = if (m.logged) Color.White else MealsHue)
            }
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 8.dp)) {
                m.image?.let { url -> RecipePhoto(url) }
                m.servings?.let { Text(trf("%1\$s portion(s)", formatNumber(it * m.portion, 2)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp)) }
                m.ingredients.forEach { i ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(MealsHue))
                        Spacer(Modifier.width(8.dp))
                        val g = (i.grams * m.portion / 5).roundToInt() * 5.0
                        Text((if (i.grams > 0) trf("%1\$s g ", formatNumber(g)) else "") + i.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!m.logged) {
                    // portion : plus ou moins faim que prévu
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(0.5, 0.75, 1.0, 1.25, 1.5).forEach { f ->
                            val sel = kotlin.math.abs(m.portion - f) < 0.01
                            Text(if (f == 1.0) "×1" else "×" + formatNumber(f, 2), style = MaterialTheme.typography.labelLarge,
                                color = if (sel) Color.White else MealsHue.onContainer(),
                                modifier = Modifier.clip(CircleShape).background(if (sel) MealsHue else MealsHue.container()).clickable { onPortion(f) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    onSwap?.let { f ->
                        Row(Modifier.padding(top = 6.dp).clip(CircleShape).clickable(onClick = f).padding(vertical = 6.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp), tint = MealsHue.onContainer())
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Autre idée pour ce repas"), style = MaterialTheme.typography.labelLarge, color = MealsHue.onContainer())
                        }
                    }
                }
                m.url?.let { url ->
                    Text(tr("Voir la recette"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.accent,
                        modifier = Modifier.padding(top = 6.dp).clickable { openUrl(url) })
                }
            }
        }
    }
}

@Composable
private fun RecipePhoto(url: String) {
    val bmp by produceState(photos[url], url) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching {
                if (!app.superhealth.nutrition.data.Net.canSendSecret(url)) return@runCatching null
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 10000; readTimeout = 15000
                    try { if (responseCode == 200) inputStream.use { BitmapFactory.decodeStream(it) } else null } finally { disconnect() }
                }
            }.getOrNull()?.also { photos[url] = it }
        }
    }
    bmp?.let {
        Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(150.dp).padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
    }
}

/** Réglages du plugin (fiche dans Réglages > Plugins) : fournisseur, clé Spoonacular, aliments à éviter. */
@Composable
fun MealPlannerOptions(nav: NavHostController) {
    val ctx = LocalContext.current
    val sh by SuperPrefs.settings.collectAsState()
    var key by remember { mutableStateOf(Secrets.get(ctx, MealPlanner.SPOONACULAR_KEY) ?: "") }
    var exclude by remember { mutableStateOf(sh.mealsExclude) }
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MealsHue, unfocusedBorderColor = Mf.colors.divider)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowDivider()
        Text(if (key.isBlank()) tr("Repas composés par votre IA à partir de la base d'aliments.") else tr("Vraies recettes Spoonacular (photo, temps, lien)."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        OutlinedTextField(key, { key = it.trim(); Secrets.put(ctx, MealPlanner.SPOONACULAR_KEY, key) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(tr("Clé Spoonacular (gratuite, facultative)")) }, visualTransformation = PasswordVisualTransformation(), colors = colors)
        Text(tr("Créer une clé gratuite"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.accent,
            modifier = Modifier.padding(horizontal = 4.dp).clickable { openUrl("https://spoonacular.com/food-api/console#Signup") })
        OutlinedTextField(exclude, { exclude = it.take(200); SuperPrefs.update { s -> s.copy(mealsExclude = exclude) } }, Modifier.fillMaxWidth(),
            label = { Text(tr("Aliments à éviter")) }, placeholder = { Text(tr("ex. champignons, porc"), color = Mf.colors.textTertiary) }, colors = colors)
        if (key.isBlank()) SettingRow(tr("Fournisseur IA"), subtitle = tr("Clé et modèle, partagés avec la nutrition")) { nav.go("aisettings") }
    }
}
