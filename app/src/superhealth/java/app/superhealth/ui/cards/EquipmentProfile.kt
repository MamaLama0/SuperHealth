package app.superhealth.ui.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.cards.*
import app.superhealth.nutrition.ui.components.NavBar
import app.superhealth.ui.ProfilePhoto
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.edge
import app.superhealth.ui.theme.tabBackground
import app.superhealth.ui.tr
import app.superhealth.ui.userName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EquipmentProfile(nav: NavHostController, edit: Boolean = false) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by EquipmentStore.state.collectAsState()
    val cards by EquipmentStore.cards.collectAsState()
    val snap by CardStore.snapshot.collectAsState()
    val savedStats by CardStore.state.collectAsState()
    val settings by SuperPrefs.settings.collectAsState()
    val avatar = DungeonCreature.adventurers.firstOrNull { it.name == settings.arsenalAvatar }
    var portraitPicker by rememberSaveable { mutableStateOf(false) }
    val stats = snap?.now ?: savedStats.lastStats
    val wallet by ArsenalGame.remote.collectAsState()
    val cutout by CardPhoto.bitmap.collectAsState()
    val bonuses = remember(saved, cards) { EquipmentStore.bonuses(saved, cards) }
    val schools = remember(saved, cards) { EquipmentStore.damageMix(saved, cards) }
    val rarity = EquipmentSlot.entries.mapNotNull { slot -> saved.equipped(slot)?.let { cards[it.id]?.tier } }.maxOrNull()
    var focus by rememberSaveable { mutableStateOf<EquipmentSlot?>(null) }
    val focused = focus ?: EquipmentSlot.entries.firstOrNull { saved.equipped(it) != null } ?: EquipmentSlot.WEAPON
    var picker by rememberSaveable { mutableStateOf<EquipmentSlot?>(null) }
    var inspecting by rememberSaveable { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { EquipmentStore.load(ctx) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = tr("L’inventaire est indisponible.") }
    }
    LaunchedEffect(Unit) {
        CardPhoto.load(ctx)
        try { app.superhealth.league.ProgressRepository.compute(ctx); CardStore.refresh(ctx) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) {}
    }
    fun save(close: Boolean, change: suspend () -> Unit) {
        scope.launch {
            try {
                change()
                runCatching { app.superhealth.ui.widget.CardWidgets.refresh(ctx) }
                if (close) { picker = null; inspecting = null }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = tr("Impossible d’enregistrer l’équipement.") }
        }
    }

    Column(Modifier.fillMaxSize().tabBackground(Mf.colors.accent).statusBarsPadding()) {
        NavBar(if (edit) "Équipement" else "Profil", { nav.popBackStack() }) {
            TextButton({ nav.go("cards/market") }, contentPadding = PaddingValues(horizontal = 5.dp)) { GoldBadge(wallet?.gold ?: saved.gold) }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 790.dp).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!edit) ArenaEntry { nav.go("cards/duel") }
                if (!edit) CharacterCard(stats, rarity, bonuses, savedStats.photo, cutout, avatar) { portraitPicker = true }

                if (!edit) Surface(shape = RoundedCornerShape(18.dp), color = Mf.colors.card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Chaque suivi compte"), style = MaterialTheme.typography.titleMedium)
                        Text(tr("Santé, sport, nutrition : tes suivis débloquent les paquets. Tes objectifs construisent tes stats."),
                            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                        TextButton({ nav.go("group/score") }, contentPadding = PaddingValues(0.dp)) { Text(tr("Mes suivis")) }
                    }
                }
                Text(tr("Équipement"), style = MaterialTheme.typography.titleLarge)
                if (schools.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    schools.keys.forEach { DamageChip(it) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val piece = saved.equipped(focused)
                    val card = piece?.let { cards[it.id] }
                    if (piece != null && card != null) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            EquipmentFace(card, piece, Modifier.widthIn(max = 325.dp).fillMaxWidth().aspectRatio(50f / 73f))
                        }
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(.86f).clip(RoundedCornerShape(20.dp)).background(Mf.colors.card)
                            .clickable { picker = focused }, contentAlignment = Alignment.Center) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Style, null, Modifier.size(32.dp), tint = Mf.colors.textSecondary)
                                Text(tr("Carte à équiper"), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EquipmentSlot.entries.forEach { slot ->
                            val current = saved.equipped(slot)
                            val def = current?.let { cards[it.id] }
                            val tint = Color(slot.tint)
                            val frame = RoundedCornerShape(13.dp)
                            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(frame)
                                .background(Brush.horizontalGradient(listOf(Mf.colors.card, androidx.compose.ui.graphics.lerp(Mf.colors.card, tint, .14f), Mf.colors.card)))
                                .border(1.dp, if (focused == slot) tint else tint.copy(alpha = .37f), frame)
                                .clickable { focus = slot; picker = slot; inspecting = null }
                                .padding(start = 7.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                val icon = when (slot) {
                                    EquipmentSlot.WEAPON -> Icons.Rounded.Bolt
                                    EquipmentSlot.CHEST -> Icons.Rounded.Checkroom
                                    EquipmentSlot.GUARD -> Icons.Rounded.Shield
                                    EquipmentSlot.LEGS -> Icons.Rounded.Hiking
                                }
                                val plate = RoundedCornerShape(8.dp)
                                Box(Modifier.size(29.dp).clip(plate).background(tint.copy(alpha = .15f))
                                    .border(1.dp, tint.copy(alpha = .42f), plate), contentAlignment = Alignment.Center) {
                                    Icon(icon, null, Modifier.size(16.dp), tint = tint)
                                }
                                Column(Modifier.weight(1f).padding(start = 6.dp, end = 3.dp)) {
                                    Text(tr(slot.title), style = MaterialTheme.typography.labelLarge)
                                    def?.let { Text(it.title, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                                Icon(if (def == null) Icons.Rounded.Add else Icons.Rounded.Edit, tr(if (def == null) "Équiper" else "Modifier"),
                                    Modifier.size(16.dp), tint = tint)
                            }
                        }
                    }
                }
                TextButton({ nav.go("cards/dungeon") }) { Text(tr("Entrer dans le donjon")) }
                if (savedStats.owned.isNotEmpty()) TextButton({ nav.go("cards/album") }) { Text(tr("Collection de fonds")) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (portraitPicker) ModalBottomSheet(onDismissRequest = { portraitPicker = false }, containerColor = Mf.colors.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Portrait du personnage"), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DungeonCreature.adventurers.forEach { model ->
                    val selected = avatar == model
                    val shape = RoundedCornerShape(15.dp)
                    Column(Modifier.width(93.dp).clip(shape).background(Mf.colors.card)
                        .border(1.dp, if (selected) Color(model.light) else Mf.colors.edge, shape)
                        .clickable {
                            SuperPrefs.update { it.copy(arsenalAvatar = model.name) }
                            portraitPicker = false
                        }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CreatureThumbnail(model, Modifier.size(74.dp))
                        Text(tr(model.nameFr), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
            TextButton({
                CardStore.update(ctx) { it.copy(photo = if (cutout != null) PhotoSource.DETOUREE else PhotoSource.PROFIL) }
                SuperPrefs.update { it.copy(arsenalAvatar = null) }
                portraitPicker = false
            }) { Text(tr("Utiliser ma photo")) }
            TextButton({
                SuperPrefs.update { it.copy(arsenalAvatar = null) }
                portraitPicker = false
                nav.go("cards/cutout")
            }) { Text(tr("Modifier la photo")) }
        }
    }
    picker?.let { slot ->
        val candidates = remember(saved.owned, cards, slot) {
            saved.owned.mapNotNull { p -> cards[p.id]?.takeIf { it.slot == slot }?.let { it to p } }
                .sortedWith(compareByDescending<Pair<EquipmentCard, EquipmentPiece>> { it.first.tier }
                    .thenByDescending { (card, piece) -> piece.boost(card) }.thenBy { it.first.title })
        }
        var filter by rememberSaveable(slot.name) { mutableStateOf<DamageType?>(null) }
        val filtered = candidates.filter { filter == null || filter in it.first.affinities }
        val selected = filtered.firstOrNull { it.second.serial == inspecting }
        val close = { picker = null; inspecting = null }
        Dialog(onDismissRequest = close,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().navigationBarsPadding()) {
                NavBar(slot.title, close)
                Text(tr(slot.attribute.label) + " · " + tr("Rareté décroissante") + " · " + filtered.size,
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Mf.colors.textSecondary)
                if (candidates.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(filter == null, { filter = null }, { Text(tr("Toutes")) })
                    DamageType.entries.filter { type -> candidates.any { type in it.first.affinities } }.forEach { type ->
                        FilterChip(filter == type, { filter = type }, { DamageIcon(type) })
                    }
                }
                if (candidates.isEmpty()) Text(tr("Les cartes de ce paquet apparaîtront ici après ton premier tirage."),
                    Modifier.padding(20.dp))
                LazyVerticalGrid(GridCells.Adaptive(145.dp), Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    gridItems(filtered, key = { it.second.serial }) { (card, piece) ->
                        EquipmentGridTile(card, piece, saved.equipped[slot] == piece.serial) { inspecting = piece.serial }
                    }
                }
                if (saved.equipped[slot] != null) {
                    TextButton({ save(true) { EquipmentStore.equip(ctx, slot, null) } }, Modifier.align(Alignment.CenterHorizontally)) {
                        Text(tr("Retirer la carte"))
                    }
                }
            }
        }
        if (selected != null) {
            val (card, piece) = selected
            AlertDialog(onDismissRequest = { inspecting = null }, title = { Text(card.title) },
                text = {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EquipmentFace(card, piece, Modifier.width(160.dp).aspectRatio(50f / 73f))
                        DamageChips(card.affinities)
                        card.setName?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        Text(equipmentBonus(card, piece), style = MaterialTheme.typography.titleMedium)
                    }
                },
                confirmButton = { Button({ save(true) { EquipmentStore.equip(ctx, slot, piece.serial) } }) { Text(tr("Équiper")) } },
                dismissButton = {
                    if (EquipmentStore.spare(saved, piece) != null) TextButton({
                        save(false) { EquipmentStore.fuse(ctx, piece.serial) }
                    }) { Text(tr("Fusionner")) }
                })
        }
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, text = { Text(message) },
            confirmButton = { TextButton({ error = null }) { Text(tr("Fermer")) } })
    }
}

@Composable
internal fun EquipmentGridTile(card: EquipmentCard, piece: EquipmentPiece, equipped: Boolean,
    modifier: Modifier = Modifier, expandArt: Boolean = false, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val group = app.superhealth.modules.Modules.installed.collectAsState().value
        .firstOrNull { it.kind == app.superhealth.modules.Modules.LEAGUE && it.enabled }
    val art by produceState<ImageBitmap?>(null, card.id, group?.version) {
        value = withContext(Dispatchers.IO) {
            runCatching { (app.superhealth.modules.Modules.arsenalAsset("arsenal-thumbs/${card.id}.webp")?.inputStream()
                ?: context.assets.open("arsenal-thumbs/${card.id}.webp")).use(BitmapFactory::decodeStream)?.asImageBitmap() }
                .getOrNull()
        }
    }
    val rarity = EquipmentRarity.entries[card.tier]
    val dark = expandArt || Mf.colors.dark
    val tint = if (dark) Color(rarity.tint) else androidx.compose.ui.graphics.lerp(Color(rarity.tint), Color.Black, .52f)
    val shape = RoundedCornerShape(17.dp)
    val action = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(modifier.fillMaxWidth().clip(shape).background(if (expandArt) Color(0xFF1C222B) else Mf.colors.card)
        .border(if (equipped || card.tier >= 3) 2.dp else 1.dp, tint.copy(alpha = if (equipped || card.tier >= 3) 1f else .65f), shape)
        .then(action).padding(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().then(if (expandArt) Modifier.weight(1f) else Modifier.aspectRatio(1.19f)).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF171622)), contentAlignment = Alignment.Center) {
            art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            if (equipped) Icon(Icons.Rounded.CheckCircle, tr("Équipée"), Modifier.align(Alignment.TopEnd).padding(4.dp), tint = tint)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(tr(rarity.title), Modifier.weight(1f), color = tint, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            card.affinities.forEach { DamageIcon(it, Modifier.size(18.dp)) }
        }
        Text(card.title, Modifier.heightIn(min = 40.dp), color = if (dark) Color.White else Mf.colors.text, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(equipmentBonus(card, piece), color = if (dark) Color(0xFF9DDFB9) else Color(0xFF246542), style = MaterialTheme.typography.labelMedium)
        if (card.penalty != null) Text(tr("Effet : Malédiction"), color = Color(0xFFE9A5B7), style = MaterialTheme.typography.labelSmall)
        if (piece.finish != EquipmentFinish.NORMAL) Text(tr(if (piece.finish == EquipmentFinish.BLESSED) "Effet : Bénédiction" else piece.finish.title), color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ArenaEntry(onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF922D41), contentColor = Color.White)) {
        Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(tr("Combattre un ami"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Icon(Icons.Rounded.ArrowForward, null, Modifier.size(20.dp))
    }
}

@Composable
private fun CharacterCard(stats: AthleteStats?, rarity: Int?, bonuses: Map<Stat, Int>, photo: PhotoSource,
    cutout: androidx.compose.ui.graphics.ImageBitmap?, avatar: DungeonCreature?, edit: () -> Unit) {
    val tint = rarity?.let { Color(EquipmentRarity.entries[it].tint) } ?: avatar?.let { Color(it.light) } ?: Mf.colors.accent
    val shape = RoundedCornerShape(26.dp)
    val portrait = when (photo) {
        PhotoSource.DETOUREE -> cutout ?: ProfilePhoto.bitmap.value
        PhotoSource.AUCUNE -> null
        else -> ProfilePhoto.bitmap.value
    }
    val detached = photo == PhotoSource.DETOUREE && cutout != null
    val statLabelHeight = with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() * 2 }
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF202938), Color(0xFF111820))))
        .border(1.dp, tint.copy(alpha = .45f), shape)) {
        Box(Modifier.fillMaxWidth().height(210.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width * .60f, size.height * .52f)
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = .22f), Color.Transparent),
                    center = center, radius = size.width * .52f), size.width * .52f, center)
                drawCircle(tint.copy(alpha = .18f), size.height * .39f, center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawCircle(tint.copy(alpha = .08f), size.height * .46f, center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(.74f).fillMaxHeight().padding(top = 12.dp),
                contentAlignment = Alignment.Center) {
                when {
                    avatar != null -> CreatureStage(avatar, null, Modifier.fillMaxSize())
                    portrait != null -> Image(portrait, tr("Photo du personnage"), Modifier.fillMaxSize()
                        .padding(if (detached) 0.dp else 18.dp).clip(RoundedCornerShape(20.dp)),
                        contentScale = if (detached) ContentScale.Fit else ContentScale.Crop)
                    else -> Icon(Icons.Rounded.Person, tr("Profil"), Modifier.size(110.dp), tint = tint)
                }
            }
            Column(Modifier.align(Alignment.TopStart).padding(20.dp)) {
                Text(tr("PERSONNAGE"), color = Color(0xFFB6C3D2), style = MaterialTheme.typography.labelSmall)
                Text("D20", style = MaterialTheme.typography.displayLarge,
                    color = tint, fontWeight = FontWeight.Black)
                Text(tr("Tes suivis, ton aventure"), style = MaterialTheme.typography.titleSmall, color = Color.White)
            }
            FilledTonalIconButton(edit, Modifier.align(Alignment.TopEnd).padding(8.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF2B3544), contentColor = Color.White)) {
                Icon(Icons.Rounded.Edit, tr("Portrait du personnage"), Modifier.size(18.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(userName() ?: tr("Mon personnage"), color = Color.White, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(rarity?.let { tr(EquipmentRarity.entries[it].title) } ?: avatar?.let { tr(it.nameFr) } ?: tr("PERSONNAGE"),
                    style = MaterialTheme.typography.labelMedium, color = tint)
            }
            val progress by app.superhealth.league.ProgressRepository.current.collectAsState()
            val settings by app.superhealth.SuperPrefs.settings.collectAsState()
            val baseStats = progress?.let { CharacterStats.from(it, app.superhealth.league.ProgressRepository.selectedTracks(settings), stats) } ?: CharacterStats()
            val equipment by EquipmentStore.state.collectAsState()
            val catalogue by EquipmentStore.cards.collectAsState()
            val character = baseStats.equipped(EquipmentStore.characterBonuses(equipment, catalogue))
            var rules by remember { mutableStateOf(false) }
            var forceDetails by rememberSaveable { mutableStateOf(false) }
            CharacterAttributes(character) { forceDetails = true }
            if (forceDetails) AlertDialog(
                onDismissRequest = { forceDetails = false },
                title = { Text(tr("Force par groupe musculaire")) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat.families.forEach { stat ->
                        val score = stats?.get(stat)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tr(stat.label))
                            Text(score?.let { "$it / 100" } ?: tr("À mesurer"), fontWeight = FontWeight.SemiBold)
                        }
                        score?.let {
                            LinearProgressIndicator(progress = { it.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                } },
                confirmButton = { TextButton({ forceDetails = false }) { Text(tr("Fermer")) } },
            )
            Text("${character.days}/28 " + tr("jours suivis") + " · ${equipment.equipped.size}/4 " + tr("cartes équipées"),
                color = Color(0xFFCBD4DF), style = MaterialTheme.typography.labelLarge)
            if (!stats?.provisional.isNullOrEmpty() || stats?.training?.provisional == true || progress == null) Text(tr("Statistiques provisoires · elles se précisent avec vos suivis"),
                color = tint, style = MaterialTheme.typography.bodySmall)
            TextButton({ rules = true }) { Text(tr("Comment progressent mes stats ?"), color = tint) }
            if (rules) CharacterRules(progress, app.superhealth.league.ProgressRepository.selectedTracks(settings), stats) { rules = false }

        }
    }
}

@Composable
private fun EquipmentFace(card: EquipmentCard, piece: EquipmentPiece, modifier: Modifier) {
    val script = "showCard(" + JSONObject.quote(card.id) + "," + JSONObject.quote(piece.finish.key) + "," + piece.boost(card) + ");"
    EquipmentWeb("card.html", script, modifier)
}

private fun signedBoost(points: Int): String {
    val formatter = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 2 }
    return (if (points > 0) "+" else "") + formatter.format(points / 10_000.0)
}

@Composable
private fun equipmentBonus(card: EquipmentCard, piece: EquipmentPiece): String {
    val base = signedBoost(piece.boost(card)) + " " + tr(card.attribute.label)
    return card.penaltyAttribute?.let { base + " · " + signedBoost(-card.penaltyPoints) + " " + tr(it.label) } ?: base
}
