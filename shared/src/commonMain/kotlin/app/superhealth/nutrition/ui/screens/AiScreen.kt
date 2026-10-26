package app.superhealth.nutrition.ui.screens

import app.superhealth.platform.*

import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import app.superhealth.platform.Context
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.data.AiConfig
import app.superhealth.nutrition.data.AiFood
import app.superhealth.nutrition.data.AiProvider
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.data.Micros
import app.superhealth.nutrition.data.SwissFoods
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.components.AssetImage
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.FoodIcon
import app.superhealth.nutrition.ui.components.MacroLine
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.TextInput
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.num
import app.superhealth.ui.theme.Mf
import app.superhealth.nutrition.ui.toNum
import app.superhealth.nutrition.ui.toKcal
import app.superhealth.nutrition.ui.RecipeDraft
import app.superhealth.nutrition.data.RecipeItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.superhealth.platform.ByteArrayOutputStream
import app.superhealth.platform.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Ligne de l'assiette : élément détecté + quantité retenue (modifiable). */
private data class PlateRow(val item: AiFood.Item, val grams: String, val useMatch: Boolean)

private fun PlateRow.macros(): Macros {
    val g = grams.toNum() ?: 0.0
    val m = item.match
    return if (useMatch && m != null) Macros(m.kcal100 * g / 100, m.protein100 * g / 100, m.fat100 * g / 100, m.carbs100 * g / 100)
    else { val k = if (item.grams > 0) g / item.grams else 0.0; Macros(item.kcal * k, item.protein * k, item.fat * k, item.carbs * k) }
}

/** Réduit une image à 1024 px max (côté long) en JPEG 85 % avant envoi au modèle. */
private fun loadJpeg(jpeg: ByteArray): ByteArray? = runCatching { downscaleJpeg(jpeg, 1024, 85) }.getOrNull()

/**
 * Journalisation par IA (« AI Describe » / photo de MacroFactor) : description et/ou photo → assiette à vérifier → journal.
 */
@Composable
fun AiLogScreen(vm: AppViewModel, onBack: () -> Unit, onSettings: () -> Unit, onAdded: () -> Unit = onBack) {
    val ctx = LocalContext.current
    val cfg by vm.repo.prefs.ai.collectAsState()
    val s by vm.settings.collectAsState()
    val day by vm.selectedDay.collectAsState()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<ByteArray?>(null) }
    var stage by remember { mutableStateOf<AiFood.Stage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var plate by remember { mutableStateOf<List<PlateRow>>(emptyList()) }
    val camera = rememberCamera { jpeg ->
        if (jpeg != null) scope.launch { image = withContext(Dispatchers.IO) { loadJpeg(jpeg) } }
        else Toast.makeText(ctx, tr("Appareil photo indisponible"), Toast.LENGTH_SHORT).show()
    }
    val gallery = rememberImagePicker { jpeg ->
        if (jpeg != null) scope.launch { image = withContext(Dispatchers.IO) { loadJpeg(jpeg) } }
    }
    val busy = stage != null && stage != AiFood.Stage.DONE

    DetailScaffold(tr("Décrire avec l'IA"), onBack, action = { IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, tr("Réglages IA")) } }) {
        if (!cfg.ready) MfCard(onClick = onSettings) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Mf.colors.accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Clé API requise"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("Ajoutez une clé API (OpenRouter ou un fournisseur compatible OpenAI)."), style = MaterialTheme.typography.bodySmall,
                        color = Mf.colors.textSecondary)
                }
            }
        }
        if (plate.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            MfCard {
                Box(Modifier.fillMaxWidth().heightIn(min = 90.dp)) {
                    if (text.isEmpty()) Text(tr("Ex. : « 2 œufs brouillés, une tartine de pain complet beurrée et un café au lait »"),
                        style = MaterialTheme.typography.bodyLarge, color = Mf.colors.textTertiary)
                    BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = Mf.colors.text),
                        cursorBrush = SolidColor(Mf.colors.accent))
                }
                image?.let { bytes ->
                    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    if (bmp != null) Box(Modifier.padding(top = 10.dp)) {
                        Image(bmp, null, Modifier.size(120.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                        Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Mf.colors.card.copy(alpha = 0.9f))
                            .clickable { image = null }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, tr("Retirer"), Modifier.size(16.dp)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(tr("Photo"), strong = false, icon = Icons.Rounded.CameraAlt) { camera() }
                    PillButton(tr("Galerie"), strong = false, icon = Icons.Rounded.PhotoLibrary) {
                        gallery()
                    }
                }
            }
            Text(tr("Astuce : précisez ce qui ne se voit pas (huile, sauce…)."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.height(8.dp))
            PillButton(if (busy) tr("Analyse…") else tr("Analyser"), Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome,
                enabled = cfg.ready && !busy && (text.isNotBlank() || image != null)) {
                error = null
                scope.launch {
                    try {
                        val items = AiFood.analyze(cfg, text, image, candidates = { q ->
                            withContext(Dispatchers.Default) {
                                vm.repo.foods.search(q).take(3) + (if (s.swissDbEnabled) SwissFoods.search(ctx, q, 5).map(SwissFoods::toFood) else emptyList())
                            }
                        }) { stage = it }
                        plate = items.map { PlateRow(it, num(it.grams, 0), it.match != null) }
                    } catch (e: Exception) {
                        error = e.message ?: e.javaClass.simpleName; stage = null
                    }
                }
            }
            if (busy) StageList(stage!!)
            error?.let {
                Text(tr(it), Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mf.colors.accent.copy(alpha = 0.15f)).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (!busy) Box(Modifier.fillMaxWidth().padding(top = 28.dp), contentAlignment = Alignment.Center) {
                AssetImage("misc/placeholder_plate.png", Modifier.size(130.dp))
            }
        } else {
            val total = plate.fold(Macros.ZERO) { acc, r -> acc + r.macros() }
            SectionLabel(tr("Assiette"))
            MfCard { NutrientHeader(total, s) }
            plate.forEachIndexed { i, r ->
                Spacer(Modifier.height(10.dp))
                PlateItem(r, s, onChange = { nr -> plate = plate.toMutableList().also { it[i] = nr } },
                    onRemove = { plate = plate.filterIndexed { j, _ -> j != i } })
            }
            Text(tr("Vérifiez les quantités : l'IA peut se tromper."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(8.dp))
            PillButton(trf("Ajouter %1\$s élément(s) au journal", plate.size), Modifier.fillMaxWidth(), enabled = plate.isNotEmpty()) {
                val rows = plate
                val minute = nowMinute()
                scope.launch {
                    rows.forEach { r ->
                        val g = r.grams.toNum()?.takeIf { it > 0 } ?: return@forEach
                        val m = r.item.match
                        if (r.useMatch && m != null) vm.repo.logFood(day, minute, vm.repo.saveFood(m), g)
                        else {
                            val mm = r.macros(); val k = g / r.item.grams
                            vm.repo.entries.insert(FoodEntryEntity(epochDay = day, meal = 3, minuteOfDay = minute, name = r.item.name, grams = g,
                                kcal = mm.kcal, protein = mm.protein, fat = mm.fat, carbs = mm.carbs, fiber = r.item.fiber * k))
                        }
                    }
                    vm.logNotice.value = trf("%1\$s élément(s) ajouté(s) au journal", rows.size)
                    onAdded()
                }
            }
            PillButton(tr("Recommencer"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false) { plate = emptyList(); stage = null }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StageList(current: AiFood.Stage) {
    Column(Modifier.padding(top = 16.dp)) {
        AiFood.Stage.entries.filter { it != AiFood.Stage.DONE }.forEach { st ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    st.ordinal < current.ordinal -> Icon(Icons.Rounded.CheckCircle, null, tint = Mf.colors.accent, modifier = Modifier.size(20.dp))
                    st == current -> CircularProgressIndicator(Modifier.size(20.dp), color = Mf.colors.accent, strokeWidth = 2.dp)
                    else -> Box(Modifier.size(20.dp).clip(CircleShape).background(Mf.colors.chip))
                }
                Spacer(Modifier.width(12.dp))
                Text(st.label.tr(), style = MaterialTheme.typography.bodyMedium, color = if (st.ordinal <= current.ordinal) Mf.colors.text else Mf.colors.textTertiary)
            }
        }
    }
}

@Composable
private fun PlateItem(r: PlateRow, s: app.superhealth.nutrition.domain.Settings, onChange: (PlateRow) -> Unit, onRemove: () -> Unit) {
    MfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FoodIcon(r.item.name, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${num(r.item.quantity, if (r.item.quantity % 1.0 == 0.0) 0 else 1)} ${r.item.unit}", style = MaterialTheme.typography.bodySmall,
                    color = Mf.colors.textSecondary)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, tr("Retirer"), tint = Mf.colors.textSecondary) }
        }
        MacroLine(r.macros(), { s.energyValue(it) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField(tr("Quantité"), r.grams, { onChange(r.copy(grams = it)) }, Modifier.weight(1f), suffix = "g")
        }
        val m = r.item.match
        if (m != null) ChipChoice(listOf(true, false), r.useMatch, {
            if (it) trf("Base : %1\$s", (m.name.take(28))) else tr("Estimation IA")
        }) { onChange(r.copy(useMatch = it)) }
        else Text(tr("Estimation IA (aucune correspondance fiable dans la base)"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary)
    }
}

private fun presetLabel(id: String) = when (id.substringBefore('/')) {
    "deepseek" -> "DeepSeek"
    "z-ai" -> "GLM"
    "google" -> "Gemini 3.1 Flash"
    else -> id
}

/** Réglages de l'IA : fournisseur, clé, modèle, test. */
@Composable
fun AiSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val cfg by vm.repo.prefs.ai.collectAsState()
    var c by remember { mutableStateOf(cfg) }
    var test by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DetailScaffold(tr("Intelligence artificielle"), onBack) {
        MfCard {
            Text(tr("Fournisseur"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
            ChipChoice(AiProvider.entries, c.provider, { it.label.tr() }) { c = c.copy(provider = it, model = "", baseUrl = "") }
            Text(tr(c.provider.keyHelp), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
            TextInput(tr("Clé API"), c.apiKey, { c = c.copy(apiKey = it.trim()) }, placeholder = tr("Collez votre clé"))
            TextInput(tr("Modèle"), c.model, { c = c.copy(model = it.trim()) }, placeholder = c.provider.defaultModel.ifBlank { tr("nom du modèle") })
            val presets = when (c.provider) {
                AiProvider.OPENROUTER -> listOf("deepseek/deepseek-v4.1-flash", "z-ai/glm-5.3-flash", "google/gemini-3.1-flash-lite")
                AiProvider.CUSTOM -> emptyList()
            }
            if (presets.isNotEmpty()) ChipChoice(presets, c.effectiveModel, { presetLabel(it) }) { c = c.copy(model = it) }
            if (c.provider == AiProvider.CUSTOM) TextInput(tr("URL de base"), c.baseUrl, { c = c.copy(baseUrl = it.trim()) }, placeholder = "https://…/v1")
            Text(tr("Le modèle doit accepter les images pour les photos."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
        }
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Enregistrer"), Modifier.fillMaxWidth()) { vm.repo.prefs.setAi(c); onBack() }
        PillButton(tr("Tester la connexion"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false, enabled = c.ready) {
            test = tr("Test en cours…")
            scope.launch {
                test = runCatching { AiFood.parseAnalyzed(AiFood.chat(c, AiFood.ANALYZE_PROMPT, tr("Description du repas : une pomme"), null)) }
                    .fold({ l -> if (l.isNotEmpty()) trf("OK : « %1\$s », %2\$s kcal", (l[0].name), (l[0].kcal.roundToInt())) else tr("Réponse reçue mais illisible.") },
                        { trf("Échec : %1\$s", it.message) })
            }
        }
        test?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp)) }
        Text(tr("La clé reste sur le téléphone. Seuls le texte et la photo sont envoyés."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(8.dp))
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Import par IA (« Import with AI » et étiquettes nutritionnelles de MacroFactor) :
 * [recipe] = photo et/ou texte d'une recette → brouillon de recette ; sinon photo d'étiquette → nouvel aliment.
 */
@Composable
fun AiImportScreen(vm: AppViewModel, recipe: Boolean, onBack: () -> Unit, onSettings: () -> Unit, onRecipe: () -> Unit) {
    val ctx = LocalContext.current
    val cfg by vm.repo.prefs.ai.collectAsState()
    val s by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf<AiFood.Label?>(null) }
    val camera = rememberCamera { jpeg ->
        if (jpeg != null) scope.launch { image = withContext(Dispatchers.IO) { loadJpeg(jpeg) } }
        else Toast.makeText(ctx, tr("Appareil photo indisponible"), Toast.LENGTH_SHORT).show()
    }
    val gallery = rememberImagePicker { jpeg ->
        if (jpeg != null) scope.launch { image = withContext(Dispatchers.IO) { loadJpeg(jpeg) } }
    }

    DetailScaffold(tr(if (recipe) "Importer une recette" else "Scanner une étiquette"), onBack,
        action = { IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, tr("Réglages IA")) } }) {
        val l = label
        if (l == null) {
            Text(tr(if (recipe) "Photo d'une page, capture d'écran ou texte collé." else "Photographiez le tableau nutritionnel du paquet."),
                style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
            Spacer(Modifier.height(8.dp))
            MfCard {
                Box(Modifier.fillMaxWidth().heightIn(min = if (recipe) 120.dp else 48.dp)) {
                    if (text.isEmpty()) Text(tr(if (recipe) "Collez la recette ici (facultatif avec une photo)" else "Nom du produit (facultatif)"),
                        style = MaterialTheme.typography.bodyLarge, color = Mf.colors.textTertiary)
                    BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = Mf.colors.text),
                        cursorBrush = SolidColor(Mf.colors.accent))
                }
                image?.let { bytes ->
                    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    if (bmp != null) Image(bmp, null, Modifier.padding(top = 10.dp).size(120.dp).clip(RoundedCornerShape(14.dp)).clickable { image = null },
                        contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(tr("Photo"), strong = false, icon = Icons.Rounded.CameraAlt) { camera() }
                    PillButton(tr("Galerie"), strong = false, icon = Icons.Rounded.PhotoLibrary) {
                        gallery()
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PillButton(tr(if (busy) "Analyse…" else "Analyser"), Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome,
                enabled = cfg.ready && !busy && (image != null || (recipe && text.isNotBlank()))) {
                error = null; busy = true
                scope.launch {
                    try {
                        if (recipe) {
                            val r = AiFood.extractRecipe(cfg, text, image)
                            vm.recipeDraft.value = RecipeDraft(name = r.name, servings = num(r.servings, if (r.servings % 1.0 == 0.0) 0 else 1),
                                items = r.items.mapIndexed { i, it ->
                                    RecipeItemEntity(recipeId = 0, position = i, name = it.name, grams = it.grams, kcal = it.kcal,
                                        protein = it.protein, fat = it.fat, carbs = it.carbs, fiber = it.fiber)
                                })
                            onRecipe()
                        } else label = AiFood.extractLabel(cfg, text, image)
                    } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
                    busy = false
                }
            }
            if (!cfg.ready) Text(tr("Clé API requise"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp).clickable(onClick = onSettings))
            error?.let {
                Text(tr(it), Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mf.colors.accent.copy(alpha = 0.15f)).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        } else {
            // Vérification de l'étiquette lue, valeurs par portion.
            var name by remember { mutableStateOf(l.name) }
            var brand by remember { mutableStateOf(l.brand) }
            var serving by remember { mutableStateOf(l.servingName) }
            var grams by remember { mutableStateOf(num(l.servingGrams, 0)) }
            var kcal by remember { mutableStateOf(num(l.kcal, 0)) }
            var p by remember { mutableStateOf(num(l.protein, 1)) }
            var f by remember { mutableStateOf(num(l.fat, 1)) }
            var c by remember { mutableStateOf(num(l.carbs, 1)) }
            Text(tr("Vérifiez les valeurs lues (par portion)."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary,
                modifier = Modifier.padding(4.dp))
            TextInput(tr("Nom"), name, { name = it })
            TextInput(tr("Marque"), brand, { brand = it })
            Row {
                TextInput(tr("Nom de la portion"), serving, { serving = it }, Modifier.weight(1f).padding(end = 6.dp))
                NumberField(tr("Poids portion"), grams, { grams = it }, Modifier.weight(1f).padding(start = 6.dp), suffix = "g")
            }
            NumberField(tr("Énergie"), kcal, { kcal = it }, suffix = s.energyUnit.label)
            Row {
                NumberField(tr("Protéines"), p, { p = it }, Modifier.weight(1f).padding(end = 4.dp), suffix = "g")
                NumberField(tr("Lipides"), f, { f = it }, Modifier.weight(1f).padding(horizontal = 4.dp), suffix = "g")
                NumberField(tr("Glucides"), c, { c = it }, Modifier.weight(1f).padding(start = 4.dp), suffix = "g")
            }
            Spacer(Modifier.height(12.dp))
            val g = grams.toNum()?.takeIf { it > 0 }
            PillButton(tr("Créer l'aliment"), Modifier.fillMaxWidth(), enabled = name.isNotBlank() && g != null && kcal.toNum() != null) {
                val food = l.copy(name = name.trim(), brand = brand.trim(), servingName = serving.trim(), servingGrams = g!!,
                    kcal = s.toKcal(kcal.toNum()!!), protein = p.toNum() ?: 0.0, fat = f.toNum() ?: 0.0, carbs = c.toNum() ?: 0.0).toFood()
                scope.launch {
                    vm.repo.foods.insert(food)
                    Toast.makeText(ctx, tr("Aliment ajouté à votre bibliothèque"), Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
            Spacer(Modifier.height(8.dp))
            PillButton(tr("Recommencer"), Modifier.fillMaxWidth(), strong = false) { label = null }
        }
    }
}
