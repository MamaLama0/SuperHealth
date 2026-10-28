package app.superhealth.nutrition.ui.screens

import app.superhealth.platform.*

import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.data.FoodEntity
import app.superhealth.nutrition.data.Micros
import app.superhealth.nutrition.data.RecipeItemEntity
import app.superhealth.nutrition.data.Repository
import app.superhealth.nutrition.ui.RecipeDraft
import app.superhealth.nutrition.ui.num
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import app.superhealth.nutrition.data.OpenFoodFacts
import app.superhealth.nutrition.data.SwissFoods
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.domain.Settings
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.SearchMode
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.FoodIcon
import app.superhealth.nutrition.ui.components.MacroLine
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.TextInput
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.ui.theme.FatColor
import app.superhealth.ui.theme.Mf
import app.superhealth.nutrition.ui.toKcal
import app.superhealth.nutrition.ui.toNum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SearchTab(val label: String) { ALL(tr("Tout")), FAVORITES(tr("Favoris")), MINE(tr("Mes aliments")), RECIPES(tr("Recettes")), QUICK(tr("Ajout rapide")), CREATE(tr("Créer")) }

@Composable
fun FoodSearchScreen(vm: AppViewModel, mode: Int, minuteArg: Int, onDone: () -> Unit, onNav: (String) -> Unit = {}) {
    val ingredient = mode == SearchMode.INGREDIENT
    val s by vm.settings.collectAsState()
    val day by vm.selectedDay.collectAsState()
    val a by vm.analytics.collectAsState()
    val target = a?.targetFor(day)?.macros
    val minute = if (minuteArg >= 0) minuteArg else nowMinute()
    var tab by remember { mutableStateOf(when (mode) {
        SearchMode.QUICK -> SearchTab.QUICK; SearchMode.CREATE -> SearchTab.CREATE; SearchMode.RECIPES -> SearchTab.RECIPES; else -> SearchTab.ALL
    }) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<FoodEntity?>(null) }
    var createBarcode by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    /** Ajouts faits depuis l'ouverture : l'écran reste ouvert pour enchaîner les aliments (comme MacroFactor). */
    var added by remember { mutableStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    /** Entrées du dernier ajout, pour « Annuler ». */
    var lastIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    /** Sélection multiple (appui long sur un aliment) : tout ajouter d'un coup. */
    val selection = remember { FoodSelection() }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val pendingNotice by vm.logNotice.collectAsState()
    LaunchedEffect(pendingNotice) { pendingNotice?.let { notice = it; lastIds = emptyList(); added++; vm.logNotice.value = null } }
    /** Après un ajout : confirmation, recherche vidée et prête pour l'aliment suivant. */
    fun afterAdd(text: String, ids: List<Long> = emptyList()) {
        added++; notice = text; lastIds = ids; message = null; query = ""
        if (tab != SearchTab.ALL) tab = SearchTab.ALL
        runCatching { focus.requestFocus() }
    }

    val scanner = rememberBarcodeScanner { result ->
        val code = result ?: return@rememberBarcodeScanner
        scope.launch {
            val local = vm.repo.foods.byBarcode(code)
            when {
                local != null -> selected = local
                s.openFoodFactsEnabled -> {
                    message = trf("Recherche du code %1\$s sur Open Food Facts…", code)
                    val off = OpenFoodFacts.byBarcode(code)
                    message = null
                    if (off != null) selected = off else { createBarcode = code; tab = SearchTab.CREATE; message = tr("Produit inconnu : créez-le (code pré-rempli).") }
                }
                else -> { createBarcode = code; tab = SearchTab.CREATE; message = tr("Produit inconnu localement : créez-le.") }
            }
        }
    }
    fun scan() = scanner()

    LaunchedEffect(Unit) {
        if (mode == SearchMode.SCAN) scan()
        else if (mode == SearchMode.SEARCH || mode == SearchMode.INGREDIENT) runCatching { focus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour")) }
            Row(
                Modifier.weight(1f).height(44.dp).clip(CircleShape).background(Mf.colors.chip).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Mf.colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(tr("Rechercher un aliment"), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    BasicTextField(
                        query, { query = it; if (tab != SearchTab.ALL && it.isNotEmpty()) tab = SearchTab.ALL },
                        Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Mf.colors.text), cursorBrush = SolidColor(Mf.colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {}),
                    )
                }
                if (query.isNotEmpty()) Icon(Icons.Rounded.Close, tr("Effacer"), Modifier.size(20.dp).clickable { query = "" }, tint = Mf.colors.textSecondary)
            }
            if (s.aiEnabled && !ingredient) IconButton(onClick = { onNav("ai") }) { Icon(Icons.Rounded.AutoAwesome, tr("Décrire avec l'IA"), tint = Mf.colors.accent) }
            IconButton(onClick = { scan() }) { Icon(Icons.Rounded.QrCodeScanner, tr("Scanner"), tint = Mf.colors.accent) }
        }
        notice?.let { n ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(app.superhealth.ui.theme.HealthHues.Activity.copy(alpha = 0.16f)).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = app.superhealth.ui.theme.HealthHues.Activity)
                Spacer(Modifier.width(8.dp))
                Text(n, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (lastIds.isNotEmpty()) androidx.compose.material3.TextButton(onClick = {
                    val ids = lastIds; lastIds = emptyList()
                    scope.launch { ids.forEach { vm.repo.entries.delete(it) }; added = (added - 1).coerceAtLeast(0); notice = tr("Ajout annulé") }
                }) { Text(tr("Annuler"), color = Mf.colors.accent) }
                PillButton(if (added > 1) trf("Fini (%1\$s)", added) else tr("Fini"), strong = true, onClick = onDone)
            }
        }
        ChipChoice(SearchTab.entries.filter { !ingredient || it != SearchTab.QUICK }, tab, { it.label.tr() },
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { tab = it }
        if (!ingredient && !selection.active && notice == null && query.isEmpty() && tab == SearchTab.ALL)
            Text(tr("Astuce : appui long sur un aliment pour en ajouter plusieurs d'un coup."), Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
        if (ingredient) Text(trf("Choisissez un ingrédient pour « %1\$s »", (vm.recipeDraft.collectAsState().value?.name?.ifBlank { tr("nouvelle recette") } ?: "")),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        message?.let {
            Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Mf.colors.accent.copy(alpha = 0.15f)).padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (!ingredient && selection.active) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(trf("%1\$s aliment(s) sélectionné(s) · dernière quantité utilisée", selection.items.size), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { selection.items.clear() }) { Text(tr("Effacer"), color = Mf.colors.text) }
            PillButton(trf("Ajouter %1\$s", selection.items.size), strong = true) {
                val foods = selection.items.values.toList(); selection.items.clear()
                scope.launch {
                    val ids = foods.map { f ->
                        val saved = if (f.id == 0L) vm.repo.saveFood(f) else f
                        val g = (if (saved.id != 0L) vm.repo.entries.lastGrams(saved.id) else null) ?: vm.repo.entries.lastGramsByName(saved.name)
                            ?: saved.servingGrams
                        vm.repo.logFood(day, minute, saved, g)
                    }
                    afterAdd(trf("%1\$s aliments ajoutés au journal", ids.size), ids)
                }
            }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalFoodSelection provides if (ingredient) null else selection) {
        when (tab) {
            SearchTab.ALL -> SearchResults(vm, s, query, onPick = { selected = it })
            SearchTab.FAVORITES -> LibraryList(vm, s, favoritesOnly = true, onPick = { selected = it })
            SearchTab.MINE -> LibraryList(vm, s, favoritesOnly = false, onPick = { selected = it })
            SearchTab.RECIPES -> RecipeList(vm, s, onPick = { selected = it }, onNew = { vm.recipeDraft.value = RecipeDraft(); onNav("recipe") },
                onAi = if (s.aiEnabled) ({ onNav("aiimport/recipe") }) else null)
            SearchTab.QUICK -> QuickAdd(s) { name, m ->
                scope.launch {
                    val id = vm.repo.quickAdd(day, minute, name, m)
                    afterAdd(trf("%1\$s ajouté au journal", name.ifBlank { tr("Ajout rapide") }), listOf(id))
                }
            }
            SearchTab.CREATE -> CreateFood(s, createBarcode, onAi = if (s.aiEnabled) ({ onNav("aiimport/label") }) else null) { food ->
                scope.launch { selected = vm.repo.saveFood(food); createBarcode = null; message = null }
            }
        }
        }
    }

    selected?.let { food ->
        val isRecipe = food.source == Repository.RECIPE
        FoodSheet(
            food, target, s, onDismiss = { selected = null },
            onToggleFavorite = if (food.id != 0L) ({ vm.launch { foods.setFavorite(food.id, !food.favorite) } }) else null,
            addLabel = if (ingredient) tr("Ajouter à la recette") else tr("Ajouter au journal"),
            onExplode = if (isRecipe && !ingredient) { g ->
                scope.launch { val ids = vm.repo.logRecipeIngredients(day, minute, food, g); selected = null; afterAdd(trf("%1\$s ajouté au journal", food.name), ids) }
            } else null,
            onEdit = if (isRecipe && !ingredient) {
                {
                    scope.launch {
                        val items = vm.repo.recipes.itemsFor(food.id)
                        val total = food.servingGrams * (food.recipeServings ?: 1.0)
                        vm.recipeDraft.value = RecipeDraft(food.id, food.name, num(food.recipeServings ?: 1.0, 0),
                            if (kotlin.math.abs(total - items.sumOf { it.grams }) > 0.5) num(total, 0) else "", items, food.favorite)
                        selected = null
                        onNav("recipe")
                    }
                }
            } else null,
            lastGrams = if (ingredient) null else ({ if (food.id != 0L) vm.repo.entries.lastGrams(food.id) else vm.repo.entries.lastGramsByName(food.name) }),
        ) { grams ->
            scope.launch {
                val saved = if (food.id == 0L) vm.repo.saveFood(food) else food
                if (ingredient) {
                    val f = grams / 100
                    val item = RecipeItemEntity(recipeId = 0, position = 0, foodId = saved.id, name = saved.name, grams = grams,
                        kcal = saved.kcal100 * f, protein = saved.protein100 * f, fat = saved.fat100 * f, carbs = saved.carbs100 * f,
                        fiber = saved.fiber100 * f, microsJson = Micros.scale(saved.microsJson, f))
                    vm.recipeDraft.value = (vm.recipeDraft.value ?: RecipeDraft()).let { it.copy(items = it.items + item) }
                    selected = null
                    onDone()
                } else {
                    val id = vm.repo.logFood(day, minute, saved, grams)
                    selected = null
                    afterAdd(trf("%1\$s ajouté au journal (%2\$s g)", saved.name, num(grams, 0)), listOf(id))
                }
            }
        }
    }
}

@Composable
private fun RecipeList(vm: AppViewModel, s: Settings, onPick: (FoodEntity) -> Unit, onNew: () -> Unit, onAi: (() -> Unit)?) {
    val all by remember { vm.repo.foods.recent(1000) }.collectAsState(initial = emptyList())
    val list = all.filter { it.source == Repository.RECIPE }.sortedBy { it.name.lowercase() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().clickable(onClick = onNew).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Mf.colors.strong), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = Mf.colors.onStrong)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(tr("Nouvelle recette"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(tr("Combinez des aliments ; les valeurs sont calculées par portion."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
            }
        }
        if (onAi != null) item {
            Row(Modifier.fillMaxWidth().clickable(onClick = onAi).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Mf.colors.chip), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Mf.colors.text)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(tr("Importer avec l'IA"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(tr("À partir d'une photo, d'un texte, ou des deux"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
            }
        }
        if (list.isEmpty()) item {
            Text(tr("Aucune recette pour l'instant."),
                Modifier.padding(16.dp), color = Mf.colors.textSecondary)
        }
        items(list, key = { it.id }) { FoodRow(it, s) { onPick(it) } }
    }
}

/** Aliments cochés par appui long dans la recherche. */
private class FoodSelection {
    val items = androidx.compose.runtime.mutableStateMapOf<String, FoodEntity>()
    val active get() = items.isNotEmpty()
    fun toggle(f: FoodEntity) { val k = f.selectionKey(); if (items.remove(k) == null) items[k] = f }
}

private fun FoodEntity.selectionKey() = if (id != 0L) "id$id" else "n$name|$brand"

private val LocalFoodSelection = androidx.compose.runtime.staticCompositionLocalOf<FoodSelection?> { null }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FoodRow(food: FoodEntity, s: Settings, onClick: () -> Unit) {
    val sel = LocalFoodSelection.current
    val checked = sel != null && sel.items.containsKey(food.selectionKey())
    Row(Modifier.fillMaxWidth().background(if (checked) Mf.colors.accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
        .combinedClickable(onClick = { if (sel?.active == true) sel.toggle(food) else onClick() }, onLongClick = sel?.let { { it.toggle(food) } })
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        FoodIcon(food.name, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (food.favorite) Icon(Icons.Rounded.Star, null, Modifier.padding(start = 4.dp).size(14.dp), tint = FatColor)
            }
            val per = if (food.servingGrams != 100.0) food.servingGrams else 100.0
            val k = per / 100
            MacroLine(Macros(food.kcal100 * k, food.protein100 * k, food.fat100 * k, food.carbs100 * k), { s.energyValue(it) },
                suffix = listOf(if (per != 100.0) trf("%1\$s (%2\$s g)", food.servingName, (per.toInt())) else tr("100 g"), food.brand).filter { it.isNotBlank() }.joinToString(" · "))
        }
        if (checked) Icon(Icons.Rounded.CheckCircle, null, Modifier.padding(start = 8.dp), tint = Mf.colors.accent)
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun SearchResults(vm: AppViewModel, s: Settings, query: String, onPick: (FoodEntity) -> Unit) {
    var local by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var remote by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var swiss by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val recent by remember { vm.repo.foods.recent(30) }.collectAsState(initial = emptyList())

    LaunchedEffect(query) {
        val q = query.trim()
        local = if (q.isEmpty()) emptyList() else vm.repo.foods.search(q)
        swiss = if (q.length < 2 || !s.swissDbEnabled) emptyList() else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            SwissFoods.search(ctx, q).map(SwissFoods::toFood)
        }
        remote = emptyList(); searched = false
        if (q.length >= 3 && s.openFoodFactsEnabled) {
            delay(550)
            loading = true
            remote = OpenFoodFacts.search(q)
            loading = false; searched = true
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        val shown = if (query.isBlank()) recent else local
        if (shown.isNotEmpty()) item { ListHeader(if (query.isBlank()) tr("Récents et fréquents") else tr("Ma bibliothèque")) }
        items(shown, key = { "l${it.id}" }) { FoodRow(it, s) { onPick(it) } }
        if (query.isBlank() && recent.isEmpty()) item {
            Text(tr("Tapez le nom d'un aliment."),
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        }
        if (swiss.isNotEmpty()) item { ListHeader(tr("Base suisse des valeurs nutritives")) }
        items(swiss, key = { "s" + it.name }) { FoodRow(it, s) { onPick(it) } }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Mf.colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp)) } }
        if (remote.isNotEmpty()) item { ListHeader(tr("Open Food Facts")) }
        items(remote) { FoodRow(it, s) { onPick(it) } }
        if (searched && remote.isEmpty() && local.isEmpty() && swiss.isEmpty()) item {
            Text(tr("Aucun résultat (ou pas de réseau). Essayez « Créer » ou « Ajout rapide »."), Modifier.padding(16.dp), color = Mf.colors.textSecondary)
        }
        if (!s.openFoodFactsEnabled && query.isNotBlank()) item {
            Text(tr("Open Food Facts est désactivé (Plus → Réglages)."), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
}

@Composable
private fun LibraryList(vm: AppViewModel, s: Settings, favoritesOnly: Boolean, onPick: (FoodEntity) -> Unit) {
    val all by remember { vm.repo.foods.recent(1000) }.collectAsState(initial = emptyList())
    val list = if (favoritesOnly) all.filter { it.favorite } else all.filter { it.source != Repository.RECIPE }.sortedBy { it.name.lowercase() }
    LazyColumn(Modifier.fillMaxSize()) {
        if (list.isEmpty()) item {
            Text(if (favoritesOnly) tr("Aucun favori. Ouvrez un aliment et touchez l'étoile.") else tr("Votre bibliothèque est vide."),
                Modifier.padding(16.dp), color = Mf.colors.textSecondary)
        }
        items(list, key = { it.id }) { FoodRow(it, s) { onPick(it) } }
    }
}

@Composable
private fun QuickAdd(s: Settings, onAdd: (String, Macros) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextInput(tr("Libellé"), name, { name = it }, placeholder = tr("Ajout rapide"))
        NumberField(tr("Énergie"), kcal, { kcal = it }, suffix = s.energyUnit.label.tr())
        Row {
            NumberField(tr("Protéines"), p, { p = it }, Modifier.weight(1f).padding(end = 6.dp), suffix = "g")
            NumberField(tr("Lipides"), f, { f = it }, Modifier.weight(1f).padding(horizontal = 3.dp), suffix = "g")
            NumberField(tr("Glucides"), c, { c = it }, Modifier.weight(1f).padding(start = 6.dp), suffix = "g")
        }
        Text(tr("Énergie vide = calculée depuis les macros (4/9/4)."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Ajouter"), Modifier.fillMaxWidth()) {
            val pv = p.toNum() ?: 0.0; val fv = f.toNum() ?: 0.0; val cv = c.toNum() ?: 0.0
            val k = kcal.toNum()?.let { s.toKcal(it) } ?: (pv * 4 + fv * 9 + cv * 4)
            if (k > 0) onAdd(name.ifBlank { tr("Ajout rapide") }, Macros(k, pv, fv, cv))
        }
    }
}

@Composable
private fun CreateFood(s: Settings, barcode: String?, onAi: (() -> Unit)?, onCreate: (FoodEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var code by remember(barcode) { mutableStateOf(barcode ?: "") }
    var servingName by remember { mutableStateOf("portion") }
    var servingG by remember { mutableStateOf("100") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf("") }
    var sat by remember { mutableStateOf("") }
    var sodium by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(0) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (onAi != null) {
            PillButton(tr("Scanner une étiquette (IA)"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.AutoAwesome, onClick = onAi)
            Spacer(Modifier.height(12.dp))
        }
        Text(tr("Valeurs pour 100 g (ou 100 mL)"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(4.dp))
        TextInput(tr("Nom"), name, { name = it }, placeholder = tr("Obligatoire"))
        TextInput(tr("Marque"), brand, { brand = it })
        TextInput(tr("Code-barres"), code, { code = it })
        NumberField(tr("Énergie"), kcal, { kcal = it }, suffix = s.energyUnit.label.tr())
        Row {
            NumberField(tr("Protéines"), p, { p = it }, Modifier.weight(1f).padding(end = 6.dp), suffix = "g")
            NumberField(tr("Lipides"), f, { f = it }, Modifier.weight(1f).padding(horizontal = 3.dp), suffix = "g")
            NumberField(tr("Glucides"), c, { c = it }, Modifier.weight(1f).padding(start = 6.dp), suffix = "g")
        }
        if (step == 0) Text(tr("+ Détails (fibres, sucres, sodium, portion)"), Modifier.padding(vertical = 10.dp, horizontal = 4.dp).clickable { step = 1 },
            color = Mf.colors.accent, style = MaterialTheme.typography.labelLarge)
        else {
            Row {
                NumberField(tr("Saturés"), sat, { sat = it }, Modifier.weight(1f).padding(end = 6.dp), suffix = "g")
                NumberField(tr("Sucres"), sugar, { sugar = it }, Modifier.weight(1f).padding(start = 6.dp), suffix = "g")
            }
            Row {
                NumberField(tr("Fibres"), fiber, { fiber = it }, Modifier.weight(1f).padding(end = 6.dp), suffix = "g")
                NumberField(tr("Sodium"), sodium, { sodium = it }, Modifier.weight(1f).padding(start = 6.dp), suffix = "mg")
            }
            Row {
                TextInput(tr("Nom de la portion"), servingName, { servingName = it }, Modifier.weight(1f).padding(end = 6.dp))
                NumberField(tr("Poids portion"), servingG, { servingG = it }, Modifier.weight(1f).padding(start = 6.dp), suffix = "g")
            }
        }
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Enregistrer"), Modifier.fillMaxWidth(), enabled = name.isNotBlank() && kcal.toNum() != null) {
            onCreate(FoodEntity(
                name = name.trim(), brand = brand.trim(), barcode = code.trim().ifBlank { null },
                servingName = servingName.ifBlank { "portion" }, servingGrams = servingG.toNum()?.takeIf { it > 0 } ?: 100.0,
                kcal100 = s.toKcal(kcal.toNum()!!), protein100 = p.toNum() ?: 0.0, fat100 = f.toNum() ?: 0.0,
                carbs100 = c.toNum() ?: 0.0, fiber100 = fiber.toNum() ?: 0.0, sugars100 = sugar.toNum() ?: 0.0,
                satFat100 = sat.toNum() ?: 0.0, sodiumMg100 = sodium.toNum() ?: 0.0,
            ))
        }
    }
}
