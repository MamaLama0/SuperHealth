package app.superhealth.ui.adventure

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.SuperPrefs
import app.superhealth.achievements.AchievementData
import app.superhealth.adventure.Adventure
import app.superhealth.adventure.AdventureArt
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dashboard.Tile
import app.superhealth.ui.dashboard.cardClick
import app.superhealth.ui.dashboard.square
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.relativeDay
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Couleurs du donjon : brique sombre de la planche, rouge des PV, or du butin. */
internal object Dungeon {
    val Night = Color(0xFF241A26)
    val Hp = Color(0xFFE5574F)
    val HpTrack = Color(0xFF4A2F38)
    val Gold = Color(0xFFF2A93B)
    val Ink = Color(0xFF3B2232)
    val Paper = Color(0xFFF4E6CF)
}

private var lastBattle: Adventure.Battle? = null
private var lastLevel: Int = 1

@Composable
private fun battleState(vm: SuperViewModel): Pair<Adventure.Battle?, Int> {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val b by produceState(lastBattle, refresh) {
        value = withContext(Dispatchers.Default) { runCatching { Adventure.battle(force = refresh > 0 && lastBattle != null) }.getOrNull() }?.also { lastBattle = it } ?: lastBattle
    }
    val lvl by produceState(lastLevel, refresh) {
        value = withContext(Dispatchers.Default) { runCatching { AchievementData.summary(ctx).level.first }.getOrNull() }?.also { lastLevel = it } ?: lastLevel
    }
    return b to lvl
}

/** Dessine la tuile [index] de la planche dans le carré [topLeft]/[side], agrandie sans lissage ; [flip] la retourne. */
internal fun DrawScope.sprite(sheet: ImageBitmap, w: Adventure.World, index: Int, topLeft: Offset, side: Float, flip: Boolean = false,
                              filter: ColorFilter? = null, alpha: Float = 1f) {
    val t = w.tile
    val col = index % w.columns; val row = index / w.columns
    val s = side.roundToInt()
    val draw: DrawScope.() -> Unit = {
        drawImage(sheet, srcOffset = IntOffset(col * t, row * t), srcSize = IntSize(t, t),
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()), dstSize = IntSize(s, s),
            filterQuality = FilterQuality.None, colorFilter = filter, alpha = alpha)
    }
    if (flip) withTransform({ scale(-1f, 1f, Offset(topLeft.x + side / 2, topLeft.y + side / 2)) }, draw) else draw()
}

/** Côté entier (multiple de la tuile) le plus grand qui tient dans [max]. */
private fun crisp(w: Adventure.World, max: Float): Float = max((max / w.tile).toInt(), 1) * w.tile.toFloat()

/** Sprite seul, net, centré dans son cadre. [dim] : silhouette (boss pas encore rencontré). */
@Composable
internal fun Sprite(index: Int, modifier: Modifier, dim: Boolean = false, flip: Boolean = false) {
    val sheet = remember { AdventureArt.sheet() } ?: return
    val w = remember { Adventure.world() } ?: return
    Canvas(modifier) {
        val side = crisp(w, size.minDimension)
        sprite(sheet, w, index, Offset((size.width - side) / 2, (size.height - side) / 2), side, flip,
            filter = if (dim) ColorFilter.tint(Color(0xFF15101A), BlendMode.SrcIn) else null, alpha = if (dim) 0.55f else 1f)
    }
}

/**
 * Scène de combat : mur et sol du donjon, le héros à gauche, le boss (plus grand) à droite avec sa barre de PV.
 * [hp] : PV affichés (animés à l'ouverture), [shake] 0..1 : le boss encaisse, [hitText] : dégâts qui montent puis s'effacent.
 */
@Composable
internal fun BattleScene(b: Adventure.Battle, level: Int, modifier: Modifier, hp: Float = b.hp.toFloat(), shake: Float = 0f, hitText: String? = null,
                         hitProgress: Float = 1f, defeated: Boolean = false) {
    val sheet = remember { AdventureArt.sheet() }
    val chosen = SuperPrefs.settings.collectAsState().value.adventureHero
    val hero = Adventure.hero(b.world, level, chosen)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Dungeon.Night)
            if (sheet == null) return@Canvas
            val w = b.world
            // deux rangées de mur, puis le sol jusqu'en bas, en tuiles entières
            val tile = crisp(w, size.height / 4.5f)
            val cols = (size.width / tile).toInt() + 1
            val rows = (size.height / tile).toInt() + 1
            for (r in 0 until rows) for (c in 0 until cols) {
                val idx = if (r < 2) w.wall[(c + r) % w.wall.size] else w.floor[((c * 7 + r * 3) % w.floor.size)]
                sprite(sheet, w, idx, Offset(c * tile, r * tile), tile)
            }
            // ombre douce sous les combattants
            val ground = tile * 3.6f
            val heroSide = crisp(w, tile * 1.5f)
            val bossSide = crisp(w, tile * 2.4f)
            drawOval(Color.Black.copy(alpha = 0.25f), Offset(size.width * 0.22f - heroSide * 0.4f, ground - heroSide * 0.08f), Size(heroSide * 0.8f, heroSide * 0.18f))
            drawOval(Color.Black.copy(alpha = 0.25f), Offset(size.width * 0.72f - bossSide * 0.4f, ground - bossSide * 0.08f), Size(bossSide * 0.8f, bossSide * 0.18f))
            hero?.let { sprite(sheet, w, it.sprite, Offset(size.width * 0.22f - heroSide / 2, ground - heroSide), heroSide) }
            val dx = (sin(shake * Math.PI * 6) * tile * 0.12f * (1 - shake)).toFloat()
            if (!defeated) sprite(sheet, w, b.boss.sprite, Offset(size.width * 0.72f - bossSide / 2 + dx, ground - bossSide), bossSide, flip = true)
            else sprite(sheet, w, b.boss.sprite, Offset(size.width * 0.72f - bossSide / 2, ground - bossSide), bossSide, flip = true, alpha = 0.25f)
            // barre de PV au-dessus du boss, pixel près
            val barW = bossSide * 1.2f; val barH = max(tile * 0.22f, 6f)
            val bar = Offset(size.width * 0.72f - barW / 2, ground - bossSide - barH * 2.2f)
            drawRoundRect(Dungeon.Ink, bar - Offset(3f, 3f), Size(barW + 6f, barH + 6f), CornerRadius(4f))
            drawRoundRect(Dungeon.HpTrack, bar, Size(barW, barH), CornerRadius(2f))
            val f = (hp / b.maxHp).coerceIn(0f, 1f)
            if (f > 0) drawRoundRect(Dungeon.Hp, bar, Size(barW * f, barH), CornerRadius(2f))
        }
        // dégâts : le chiffre monte depuis le boss et s'efface, une fois
        if (hitText != null && hitProgress < 1f) Text(hitText, color = Dungeon.Gold, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 22.sp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp, bottom = (24 + 56 * hitProgress).dp)
                .graphicsLayer { alpha = (1.6f - 1.6f * hitProgress).coerceIn(0f, 1f) }, maxLines = 1)
    }
}

/** Ce qu'il reste au boss, en kilos soulevés ou en kilomètres courus. */
internal fun leftText(b: Adventure.Battle): String =
    trf("%1\$s ou %2\$s", if (b.leftKg >= 1000) trf("%1\$s t", formatNumber(b.leftKg / 1000, 1)) else trf("%1\$s kg", formatNumber(b.leftKg)),
        trf("%1\$s km", formatNumber(b.leftKm, 1)))

/**
 * Carte « Boss du moment » : petit (le boss dans son anneau de PV), moyen (boss, nom, PV), bandeau (une ligne), grand
 * (la scène de combat et ce qu'il reste). Toucher : l'écran de l'aventure.
 */
@Composable
fun BossCard(c: DashCard, size: CardSize, vm: SuperViewModel, modifier: Modifier, nav: NavHostController) {
    val (b, level) = battleState(vm)
    val open: () -> Unit = { nav.go("adventure") }
    val box = modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(Mf.colors.card).cardClick(open)
    if (b == null) {
        Box(box.then(if (size == CardSize.S) Modifier.square() else Modifier).padding(14.dp), contentAlignment = Alignment.Center) {
            Text(tr("Aventure"), style = MaterialTheme.typography.titleSmall, color = Mf.colors.textSecondary)
        }
        return
    }
    when (size) {
        CardSize.S -> Box(box.square().padding(8.dp), contentAlignment = Alignment.Center) {
            HpRing(b.fraction, Modifier.fillMaxSize())
            Sprite(b.boss.sprite, Modifier.fillMaxSize(0.62f), flip = true)
        }
        CardSize.M -> Column(box.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Dungeon.Night), contentAlignment = Alignment.Center) {
                    Sprite(b.boss.sprite, Modifier.size(48.dp), flip = true)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.boss.name.text, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(trf("%1\$s PV", formatNumber(b.hp.toDouble())), style = MaterialTheme.typography.labelMedium, color = Dungeon.Hp.onContainer())
                }
            }
            Spacer(Modifier.height(10.dp))
            HpBar(b.fraction)
        }
        CardSize.W -> Row(box.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Dungeon.Night), contentAlignment = Alignment.Center) { Sprite(b.boss.sprite, Modifier.size(32.dp), flip = true) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(b.boss.name.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                HpBar(b.fraction)
            }
        }
        CardSize.L -> Tile(c, tr("Boss du moment"), modifier, tinted = false, trailing = trf("Niv. %1\$s", level), onClick = open) {
            BattleScene(b, level, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(b.boss.name.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(trf("Reste %1\$s", leftText(b)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                }
                Text(trf("%1\$s PV", formatNumber(b.hp.toDouble())), style = MaterialTheme.typography.labelLarge, color = Dungeon.Hp.onContainer())
            }
            Spacer(Modifier.height(8.dp))
            HpBar(b.fraction)
        }
    }
}

@Composable
internal fun HpBar(f: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Dungeon.Hp.container())) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(Dungeon.Hp))
    }
}

@Composable
private fun HpRing(f: Float, modifier: Modifier) {
    val track = Dungeon.Hp.container()
    Canvas(modifier) {
        val d = size.minDimension; val st = d * 0.08f
        val tl = Offset((size.width - d) / 2 + st / 2, (size.height - d) / 2 + st / 2)
        drawArc(track, 0f, 360f, false, tl, Size(d - st, d - st), style = Stroke(st))
        drawArc(Dungeon.Hp, -90f, 360f * f.coerceIn(0f, 1f), false, tl, Size(d - st, d - st), style = Stroke(st, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

/** Boîte de dialogue pixel : portrait, nom, texte qui s'écrit une fois ; toucher : la suite. */
@Composable
private fun DialogueBox(scene: Adventure.Scene, line: Int, onNext: () -> Unit) {
    val text = scene.lines.getOrNull(line)?.text.orEmpty()
    var shown by remember(scene, line) { mutableIntStateOf(0) }
    LaunchedEffect(scene, line) { while (shown < text.length) { kotlinx.coroutines.delay(18); shown += 1 } }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Dungeon.Paper).border(3.dp, Dungeon.Ink, RoundedCornerShape(10.dp))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (shown < text.length) shown = text.length else onNext() }
        .padding(10.dp), verticalAlignment = Alignment.Top) {
        scene.sprite?.let { Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Dungeon.Night), contentAlignment = Alignment.Center) { Sprite(it, Modifier.size(40.dp)) } }
        if (scene.sprite != null) Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            scene.speaker?.let { Text(it.text, color = Dungeon.Hp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Text(text.take(shown), color = Dungeon.Ink, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 19.sp,
                modifier = Modifier.height(78.dp))
            Text(if (line < scene.lines.lastIndex) "▸" else "■", color = Dungeon.Ink, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.End))
        }
    }
}

/**
 * Écran de l'aventure : le combat (les nouveaux dégâts s'animent une fois), les scènes du récit à lire, la carte du donjon,
 * le butin, les derniers coups, le héros et le journal de quête.
 */
@Composable
fun AdventureScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val (b, level) = battleState(vm)
    DetailScaffold(tr("Aventure"), onBack = { nav.popBackStack() }) {
        if (b == null) {
            Text(if (Adventure.world() == null) tr("Contenu du module introuvable : réinstallez-le dans Réglages > Modules.") else tr("Préparation du donjon…"),
                color = Mf.colors.textSecondary)
            return@DetailScaffold
        }
        // nouveaux dégâts depuis la dernière visite : la barre descend et le boss encaisse, une seule fois
        val seen = remember { Adventure.seenDamage(ctx) }
        val fresh = (b.total - seen.coerceAtLeast(0)).coerceAtLeast(0)
        val startHp = if (fresh > 0) (b.hp + fresh).coerceAtMost(b.maxHp).toFloat() else b.hp.toFloat()
        val hp = remember { Animatable(startHp) }
        val shake = remember { Animatable(1f) }
        val rise = remember { Animatable(if (fresh > 0) 0f else 1f) }
        LaunchedEffect(b.total) {
            if (fresh > 0) {
                launch { rise.animateTo(1f, tween(1600, easing = LinearEasing)) }
                launch { shake.snapTo(0f); shake.animateTo(1f, tween(700)) }
                hp.animateTo(b.hp.toFloat(), tween(1200, delayMillis = 250, easing = FastOutSlowInEasing))
            } else hp.snapTo(b.hp.toFloat())
            Adventure.markDamage(ctx, b.total)
        }
        val pending = remember(b, level) { Adventure.pending(ctx, b, level) }
        var sceneIdx by remember(pending) { mutableIntStateOf(0) }
        var line by remember(pending) { mutableIntStateOf(0) }
        val scene = pending.getOrNull(sceneIdx)

        BattleScene(b, level, Modifier.fillMaxWidth().aspectRatio(1.55f).clip(RoundedCornerShape(CardRadius)), hp = hp.value, shake = shake.value,
            hitText = if (fresh > 0) "-${formatNumber(fresh.toDouble())}" else null, hitProgress = rise.value)
        Spacer(Modifier.height(10.dp))
        if (scene != null) {
            DialogueBox(scene, line) {
                if (line < scene.lines.lastIndex) line++ else { Adventure.markScene(ctx, scene.at); sceneIdx++; line = 0 }
            }
            Spacer(Modifier.height(10.dp))
        }
        MfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.boss.name.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (b.round > 0) trf("Tour %1\$s · boss %2\$s sur %3\$s", b.round + 1, b.index + 1, b.world.bosses.size)
                        else trf("Boss %1\$s sur %2\$s", b.index + 1, b.world.bosses.size), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
                Text(trf("%1\$s PV", formatNumber(hp.value.toDouble())), style = MaterialTheme.typography.titleMedium, color = Dungeon.Hp.onContainer())
            }
            Spacer(Modifier.height(10.dp))
            HpBar(hp.value / b.maxHp)
            Spacer(Modifier.height(8.dp))
            Text(trf("Pour le vaincre : %1\$s.", leftText(b)), style = MaterialTheme.typography.bodyMedium)
            Text(trf("1 kg soulevé = %1\$s dégât · 1 km couru = %2\$s dégâts", formatNumber(b.world.kgDamage), formatNumber(b.world.kmDamage)),
                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary, modifier = Modifier.padding(top = 4.dp))
        }

        SectionLabel(tr("Le donjon"))
        MfCard {
            b.world.bosses.chunked(5).forEachIndexed { r, row ->
                if (r > 0) Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { k, boss ->
                        val i = r * 5 + k
                        val done = b.round > 0 || i < b.index
                        val current = i == b.index
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (current) Dungeon.Hp.container() else Dungeon.Night.copy(alpha = if (done) 0.9f else 0.55f))
                                .border(if (current) 2.dp else 0.dp, if (current) Dungeon.Hp else Color.Transparent, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center) {
                                Sprite(boss.sprite, Modifier.fillMaxSize(0.75f), dim = !done && !current, flip = true)
                                if (done) Text("✓", color = Dungeon.Gold, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp), fontSize = 12.sp)
                            }
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        val loot = b.defeated.filter { it.round == 0 }.mapNotNull { d -> d.boss.loot?.let { it to d } }
        if (loot.isNotEmpty()) {
            SectionLabel(tr("Butin"))
            MfCard {
                loot.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        row.forEach { (item, d) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Dungeon.Gold.container()), contentAlignment = Alignment.Center) {
                                    Sprite(item, Modifier.size(40.dp))
                                }
                                Text(d.boss.lootName?.text ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        SectionLabel(tr("Derniers coups"))
        MfCard {
            if (b.hits.isEmpty()) Text(tr("Aucun coup porté pour l'instant : une séance de musculation ou une course suffit."),
                style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
            b.hits.take(8).forEach { h ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Mf.colors.chip), contentAlignment = Alignment.Center) {
                        Icon(if (h.km > 0) Icons.AutoMirrored.Rounded.DirectionsRun else Icons.Rounded.FitnessCenter, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(relativeDay(h.day) + " · " + (if (h.km > 0) trf("%1\$s km", formatNumber(h.km, 1)) else trf("%1\$s kg soulevés", formatNumber(h.kg))),
                            style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                    }
                    Text("-${formatNumber(h.damage.toDouble())}", style = MaterialTheme.typography.titleSmall, color = Dungeon.Hp.onContainer(), fontWeight = FontWeight.Bold)
                }
            }
        }

        SectionLabel(tr("Héros"))
        HeroPicker(b.world, level)

        val journal = remember(b, level, sceneIdx) { Adventure.journal(ctx, b, level) }
        if (journal.isNotEmpty()) {
            SectionLabel(tr("Journal de quête"))
            MfCard {
                journal.forEachIndexed { i, sc ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    sc.speaker?.let { Text(it.text, style = MaterialTheme.typography.labelLarge, color = Dungeon.Hp.onContainer()) }
                    sc.lines.forEach { l -> Text(l.text, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Succès et voyage"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.EmojiEvents) { nav.go("achievements") }
        Spacer(Modifier.height(24.dp))
    }
}

/** Héros : l'apparence suit le niveau ; celles déjà débloquées peuvent être choisies. */
@Composable
private fun HeroPicker(w: Adventure.World, level: Int) {
    val sh by SuperPrefs.settings.collectAsState()
    val current = Adventure.hero(w, level, sh.adventureHero)
    MfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Dungeon.Night), contentAlignment = Alignment.Center) {
                current?.let { Sprite(it.sprite, Modifier.size(48.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(current?.name?.text ?: "", style = MaterialTheme.typography.titleMedium)
                Text(trf("Niveau %1\$s", level), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            w.heroes.forEach { h ->
                val open = h.level <= level
                val on = current?.sprite == h.sprite
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (on) Dungeon.Gold.container() else Mf.colors.chip)
                        .then(if (open) Modifier.clickable { SuperPrefs.update { it.copy(adventureHero = if (h.sprite == Adventure.hero(w, level)?.sprite) -1 else h.sprite) } } else Modifier),
                        contentAlignment = Alignment.Center) { Sprite(h.sprite, Modifier.fillMaxSize(0.8f), dim = !open) }
                    Text(if (open) "" else trf("Niv. %1\$s", h.level), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary, maxLines = 1)
                }
            }
        }
    }
}

/** Raccourci depuis l'écran des succès vers le donjon. */
@Composable
fun AdventureTeaser(vm: SuperViewModel, nav: NavHostController) {
    val (b, level) = battleState(vm)
    b ?: return
    MfCard(onClick = { nav.go("adventure") }, padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BattleScene(b, level, Modifier.width(120.dp).height(76.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.boss.name.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                HpBar(b.fraction)
                Text(trf("Reste %1\$s", leftText(b)), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp), maxLines = 1)
            }
        }
    }
}
