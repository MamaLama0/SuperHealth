package app.superhealth.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.superhealth.ui.Lang
import java.text.NumberFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.cards.*
import app.superhealth.league.League
import app.superhealth.league.ProgressRepository
import app.superhealth.nutrition.ui.components.NavBar
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.edge
import app.superhealth.ui.theme.tabBackground
import app.superhealth.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun DailyPackRoute(nav: NavHostController) {
    Column(Modifier.fillMaxSize().tabBackground(Mf.colors.accent).statusBarsPadding().navigationBarsPadding()) {
        NavBar("Paquets", { nav.popBackStack() })
        EquipmentPacks(nav, true, Modifier.weight(1f).fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EquipmentPacks(nav: NavHostController, active: Boolean, modifier: Modifier = Modifier, onDashboard: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SuperPrefs.settings.collectAsState()
    val league by League.state.collectAsState()
    val progress by ProgressRepository.current.collectAsState()
    val inventory by EquipmentStore.state.collectAsState()
    val cards by EquipmentStore.cards.collectAsState()
    val wallet by ArsenalGame.remote.collectAsState()
    var day by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val selected = remember(settings, league.tracked) { ProgressRepository.selectedTracks(settings) }
    val todayProgress = progress?.takeIf { it.summary.throughDay == day }
    val gate = PackProgress.from(day, selected, todayProgress?.validatedToday.orEmpty())
    var opening by remember { mutableStateOf<EquipmentStore.Opening?>(null) }
    var direction by remember { mutableIntStateOf(1) }
    var tearProgress by remember { mutableFloatStateOf(0f) }
    val opened = (inventory.day ?: Long.MIN_VALUE) >= day
    val unlimited = EquipmentStore.unlimitedPacks
    val canOpen = cards.isNotEmpty() && (unlimited || gate.canOpen(inventory.day) || inventory.freePacks > 0 ||
        inventory.rewardPending != null || inventory.remotePendingDay == day || (wallet?.packCredits ?: inventory.packCredits).isNotEmpty())
    var index by rememberSaveable { mutableIntStateOf(0) }
    val slot = EquipmentBoosters.slots[index.mod(EquipmentBoosters.slots.size)]
    val variantDay = day + if (unlimited) inventory.opened else 0
    val gold = if (unlimited) inventory.opened % 10 == 9 else inventory.goldNext
    val skin = packSkin(variantDay, slot)
    val edition = PackEdition.forDraw(gold)
    var sample by rememberSaveable { mutableStateOf<PackEdition?>(null) }
    val displayedEdition = sample ?: edition
    val renderKey = "${slot.key}|$skin|${displayedEdition.key}"
    var rendered by remember { mutableStateOf<String?>(null) }
    val allowed = canOpen && sample == null
    var notice by rememberSaveable { mutableStateOf(false) }
    var quests by rememberSaveable { mutableStateOf(false) }
    var rates by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var issue by remember { mutableStateOf<String?>(null) }
    var committing by remember { mutableStateOf(false) }
    var webError by remember { mutableStateOf<String?>(null) }
    val haptics = app.superhealth.ui.rememberHaptics()

    LaunchedEffect(active, retry, selected) {
        if (!active) return@LaunchedEffect
        var first = true
        while (true) {
            day = LocalDate.now().toEpochDay()
            try {
                EquipmentStore.load(ctx)
                val progress = ProgressRepository.compute(ctx, LocalDate.ofEpochDay(day))
                if (first) {
                    first = false
                    runCatching { ArsenalGame.advance(ctx, ArsenalGame.quests(progress, selected)) }
                }
                issue = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { issue = tr("Les suivis n’ont pas pu être actualisés.") }
            delay(60_000)
        }
    }

    fun step(offset: Int) {
        if (!committing && opening == null) {
            webError = null
            sample = null
            index = (index + offset).mod(EquipmentBoosters.slots.size)
            haptics.tick()
        }
    }
    fun openCurrent(side: Int = 1, progress: Float = 0f) {
        if (!active || !allowed || committing || opening != null) return
        committing = true
        webError = null
        direction = side
        tearProgress = progress
        scope.launch {
            try {
                val draw = EquipmentStore.open(ctx, slot)
                if (draw == null) {
                    webError = tr("Pack indisponible. Vérifie tes suivis du jour.")
                    retry++
                } else {
                    opening = draw
                    runCatching { app.superhealth.ui.widget.CardWidgets.refresh(ctx) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { webError = e.message ?: tr("Ouverture impossible. Réessaie.") }
            finally { committing = false }
        }
    }
    val script = "arena.language(${Lang.english});arena.colors('${Mf.colors.text.css()}','${Mf.colors.textSecondary.css()}');arena.pause(${!active || opening != null});" +
        "arena.select('${slot.key}',${displayedEdition.gold},$allowed,$skin,'${displayedEdition.key}');" +
        (if (committing) "arena.prepareOpen();" else "") +
        (webError?.let { "arena.openingFailed(" + JSONObject.quote(it) + ");" } ?: "") +
        (if (!committing && opening == null && webError == null) "arena.releaseWaiting();" else "")

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            TextButton({ nav.go("cards/duel") }, Modifier.align(Alignment.CenterStart), contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp)); Text(tr("L’arène"))
            }
            TextButton({ nav.go("cards/market") }, Modifier.align(Alignment.CenterEnd), contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Rounded.Storefront, tr("Boutique"), Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                GoldBadge(wallet?.gold ?: inventory.gold)
            }
            Text(tr("ARSENAL"), Modifier.align(Alignment.Center).padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
        }
        if (inventory.releaseGiftGranted && !settings.arsenal40NoticeSeen && !unlimited) {
            TextButton({ notice = true }) {
                Icon(Icons.Rounded.CardGiftcard, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("4.0 · 15 paquets offerts"))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            EquipmentWeb("index.html", script, Modifier.fillMaxSize(), active || rendered != renderKey, ownsGestures = true) { kind, value ->
                if (kind == "rendered") {
                    rendered = value
                    return@EquipmentWeb
                }
                if (!active) return@EquipmentWeb
                when (kind) {
                    "step" -> if (value == "1" || value == "-1") step(value.toInt())
                    "dashboard" -> if (!committing && opening == null) onDashboard?.invoke()
                    "open" -> {
                        val gesture = value.split('|', limit = 3)
                        if (gesture.firstOrNull() == slot.key) {
                            val side = if (gesture.getOrNull(1) == "-1") -1 else 1
                            val progress = gesture.getOrNull(2)?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
                            openCurrent(side, progress)
                        }
                    }
                    "haptic" -> packHaptic(haptics, value)
                }
            }
        }
        Box(Modifier.height(96.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
          if (opening == null) {
            Row(Modifier.widthIn(max = 510.dp).fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PackAction(tr("Quêtes"), Icons.Rounded.Bookmarks, onClick = { quests = true })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr(slot.packTitle),
                        style = MaterialTheme.typography.labelLarge)

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ step(-1) }, Modifier.size(36.dp)) { Icon(Icons.Rounded.ExpandLess, tr("Paquet précédent"), tint = Mf.colors.text) }
                        EquipmentBoosters.slots.forEach { s ->
                            val current = s == slot
                            Box(Modifier.size(if (current) 13.dp else 6.dp, 6.dp)
                                .background(if (current) Mf.colors.accent else Mf.colors.edge, CircleShape))
                        }
                        IconButton({ step(1) }, Modifier.size(36.dp)) { Icon(Icons.Rounded.ExpandMore, tr("Paquet suivant"), tint = Mf.colors.text) }
                    }
                    if (sample != null) TextButton({ sample = null }) { Text(tr("Quitter l’aperçu")) }
                    else if (!unlimited && allowed) TextButton({ openCurrent() }, enabled = !committing,
                        contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(tr(if (committing) "Préparation du tirage…" else "Ouvrir le paquet"), style = MaterialTheme.typography.labelMedium)
                    }
                    else Text(webError ?: issue ?: when {
                        unlimited -> tr("Ouvertures illimitées")
                        inventory.freePacks + wallet?.packCredits.orEmpty().size > 0 ->
                            (inventory.freePacks + wallet?.packCredits.orEmpty().size).toString() + " " + tr("Paquets bonus").lowercase()
                        opened -> tr("Pack du jour ouvert")
                        todayProgress == null -> tr("Lecture des suivis…")
                        gate.ready -> tr("Pack du jour prêt")
                        gate.required == 0 -> tr("Choisis tes suivis")
                        else -> tr("Pack du jour") + " · " + gate.completed + "/" + gate.required
                    }, color = Mf.colors.textSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                }
                PackAction(tr("Profil"), Icons.Rounded.Person) { nav.go("cards/equipment") }
            }
          }
        }
        Spacer(Modifier.height(6.dp))
    }
    opening?.let { draw ->
        FocusOpening(draw, cards, inventory.owned, direction, tearProgress) {
            opening = null
        }
    }
    if (notice) AlertDialog(
        onDismissRequest = { notice = false },
        title = { Text("Vasi c bon j'en ai marre enjoy") },
        text = { Text(tr("Toutes les collections de cartes ont été remises à zéro. Pour me faire pardonner, voici 15 paquets offerts.")) },
        confirmButton = { TextButton({
            SuperPrefs.update { it.copy(arsenal40NoticeSeen = true) }
            notice = false
        }) { Text(tr("Compris")) } },
    )
    if (quests) ModalBottomSheet(onDismissRequest = { quests = false }, containerColor = Mf.colors.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("Quêtes de l’Arsenal"), style = MaterialTheme.typography.headlineSmall)
                GoldBadge(wallet?.gold ?: inventory.gold)
            }
            val targets = todayProgress?.let { ArsenalGame.quests(it, selected) }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                ArsenalQuestContent(targets)
                gate.targets.sortedBy { it.ordinal }.forEach { track ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (track in gate.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                            tint = if (track in gate.done) Mf.colors.accent else Mf.colors.textSecondary)
                        Text(tr(track.label), Modifier.padding(start = 8.dp))
                    }
                }
            }
            TextButton({ quests = false; nav.go("group/score") }) { Text(tr("Mes suivis")) }
            TextButton({ retry++ }) { Text(tr("Actualiser les suivis")) }
            TextButton({ quests = false; rates = true }) { Text(tr("Raretés et finitions")) }
        }
    }
    if (rates) AlertDialog(
        onDismissRequest = { rates = false },
        title = { Text(tr("Raretés et finitions")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            EquipmentRates()
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text(tr("Aperçus 3D"), style = MaterialTheme.typography.titleSmall)
            TextButton({ sample = PackEdition.SOLAR; rates = false }) { Text(tr(PackEdition.SOLAR.title)) }
        } },
        confirmButton = { TextButton({ rates = false }) { Text(tr("Fermer")) } },
    )
}

private fun packHaptic(haptics: app.superhealth.ui.Haptics, value: String) {
    when (value) {
        "tear-start" -> haptics.press()
        "tear-tick" -> haptics.tick()
        "tear-end" -> haptics.release()
        "eject" -> haptics.confirm()
        "card" -> haptics.toggle()
    }
}

@Composable
private fun FocusOpening(draw: EquipmentStore.Opening, cards: Map<String, EquipmentCard>, owned: List<EquipmentPiece>,
    direction: Int, tearProgress: Float, onComplete: () -> Unit) {
    var summary by remember { mutableStateOf(false) }
    val haptics = app.superhealth.ui.rememberHaptics()
    val sorted = remember(draw, cards) { draw.pieces.sortedBy { cards.getValue(it.id).tier } }
    val receipt = remember(draw, cards, owned) {
        val pieces = JSONArray()
        sorted.forEach { piece ->
            val card = cards.getValue(piece.id)
            pieces.put(JSONObject().put("id", piece.id).put("serial", piece.serial).put("effect", piece.finish.key)
                .put("boost", piece.boost(card)).put("fresh", owned.none { it.id == piece.id && it.serial < piece.serial }))
        }
        JSONObject().put("slot", draw.slot.key).put("gold", draw.gold).put("skin", draw.skin)
            .put("edition", draw.edition.key).put("pieces", pieces)
    }
    val script = "arena.language(${Lang.english});arena.colors('#f9efff','#ded2ec');arena.showOpening($receipt,$direction,$tearProgress);"
    Dialog(onDismissRequest = {}, properties = DialogProperties(
        dismissOnBackPress = false, dismissOnClickOutside = false,
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        Column(Modifier.fillMaxSize().background(Color(0xFF131019)).statusBarsPadding().navigationBarsPadding()) {
            if (!summary) Box(Modifier.fillMaxWidth().height(49.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { summary = true }) {
                    Text(tr("Tout révéler"), color = Color(0xFFF1D7AE))
                }
            }
            if (summary) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(tr("Ton butin"), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(tr("Ajouté à ta collection"), style = MaterialTheme.typography.bodySmall, color = Color(0xFFBFB6CC))
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val columns = if (maxWidth >= 600.dp) 3 else 2
                    val rows = (sorted.size + columns - 1) / columns
                    val tileWidth = (maxWidth - 32.dp - 10.dp * (columns - 1)) / columns
                    val tileHeight = ((maxHeight - 24.dp - 10.dp * (rows - 1)) / rows).coerceIn(210.dp, 420.dp)
                    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(sorted, key = { _, piece -> piece.serial }, span = { index, _ ->
                            GridItemSpan(if (sorted.size % columns == 1 && index == sorted.lastIndex) columns else 1)
                        }) { index, piece ->
                            val single = sorted.size % columns == 1 && index == sorted.lastIndex
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                EquipmentGridTile(cards.getValue(piece.id), piece, equipped = false,
                                    modifier = Modifier.then(if (single) Modifier.width(tileWidth)
                                        else Modifier.fillMaxWidth()).height(tileHeight), expandArt = true)
                            }
                        }
                    }
                }
                Button(onClick = onComplete,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0C892), contentColor = Color(0xFF261923))) {
                    Text(tr("Terminer"), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                EquipmentWeb("index.html", script, Modifier.weight(1f).fillMaxWidth(), ownsGestures = true) { kind, value ->
                    when (kind) {
                        "summary" -> summary = true
                        "haptic" -> packHaptic(haptics, value)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EquipmentRatesRoute(nav: NavHostController) {
    Column(Modifier.fillMaxSize().tabBackground(Mf.colors.accent).statusBarsPadding().navigationBarsPadding()) {
        NavBar("Raretés et finitions", { nav.popBackStack() })
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) { EquipmentRates() }
    }
}

@Composable
private fun EquipmentRates() {
    val format = NumberFormat.getPercentInstance(Lang.locale).apply { maximumFractionDigits = 3 }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(tr("Par carte"), Modifier.weight(1.5f), style = MaterialTheme.typography.labelLarge)
            Text(tr("Standard"), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge)
            Text(tr("Doré"), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge)
        }
        EquipmentRarity.entries.forEach { rarity ->
            RateRow(tr(rarity.title), format.format(rarity.weight(false)), format.format(
                rarity.weight(true) / EquipmentRarity.entries.sumOf { it.weight(true) }))
        }
        HorizontalDivider()
        listOf(EquipmentFinish.SHINY, EquipmentFinish.ANIMATED, EquipmentFinish.CHROMA, EquipmentFinish.BLESSED).forEach { finish ->
            RateRow(tr(finish.title), format.format(finish.chance(false)), format.format(finish.chance(true)))
        }
        Text(tr("Certaines cartes maudites offrent un bonus accru et un malus sur une autre statistique."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }
}

@Composable
private fun RateRow(title: String, standard: String, gold: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
        Text(standard, Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
        Text(gold, Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PackAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        FilledTonalIconButton(onClick, Modifier.size(48.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Mf.colors.card, contentColor = Mf.colors.text)) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
    }
}

private fun Color.css() = String.format(Locale.ROOT, "#%06x", toArgb() and 0xFFFFFF)

private val EquipmentSlot.packTitle: String get() = when (this) {
    EquipmentSlot.WEAPON -> "Armes"
    EquipmentSlot.CHEST -> "Armures"
    EquipmentSlot.GUARD -> "Boucliers"
    EquipmentSlot.LEGS -> "Bottes"
}
