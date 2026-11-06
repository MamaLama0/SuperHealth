package app.superhealth.ui.cards

import app.superhealth.platform.*
import kotlinx.coroutines.launch

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Man
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Woman
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.cards.*
import app.superhealth.ui.tr


internal val CardShape = RoundedCornerShape(7)
internal fun Long.c() = Color(this.toInt())

/** Couleurs de la plaque de palier (sous la note), gagnée par les statistiques. */
internal fun tierColors(t: Tier): List<Color> = when (t) {
    Tier.BRONZE -> listOf(0xFFF3C69C, 0xFF9C5A2E, 0xFFE8A77A, 0xFF6B3A1C)
    Tier.ARGENT -> listOf(0xFFFFFFFF, 0xFF8E9AA6, 0xFFE3E9EF, 0xFF5C6670)
    Tier.OR -> listOf(0xFFFFF4C2, 0xFFC99A1C, 0xFFFFE07A, 0xFF8A6100)
    Tier.ELITE -> listOf(0xFFE1F6FF, 0xFF2C8BE6, 0xFF8AE0FF, 0xFF1446A8)
    Tier.ICONE -> listOf(0xFFFFF0A8, 0xFFFF5EA8, 0xFF7C5CFF, 0xFF35D6FF)
}.map { it.c() }

/**
 * Charte des raretés, lisible de loin : ◆ acier mat · ◆◆ émail vert et perle · ◆◆◆ saphir, double filet, rivets, reflet qui passe ·
 * ★ épique : prisme tournant, runes qui courent sur le bord, filigranes, étincelles, couronne et aura.
 */
internal fun rarityColors(r: Rarity): List<Color> = when (r) {
    Rarity.COMMUNE -> listOf(0xFFE3E7EC, 0xFF8A939E, 0xFFC7CDD4, 0xFF5D646D)
    Rarity.PEU_COMMUNE -> listOf(0xFFC9FFE4, 0xFF2FAE72, 0xFF8DF0BE, 0xFF146B45)
    Rarity.RARE -> listOf(0xFFD6EEFF, 0xFF2F86E8, 0xFF8FD0FF, 0xFF123F8A)
    Rarity.EPIQUE -> listOf(0xFFFF4F7B, 0xFFFFB238, 0xFFFFF36B, 0xFF5BFF9A, 0xFF4FC3FF, 0xFFB46CFF, 0xFFFF4F7B)
}.map { it.c() }

enum class PhotoMode { DETOUREE, PORTRAIT }

data class CardFace(
    val name: String,
    val stats: AthleteStats?,
    val background: Pull?,
    val photo: ImageBitmap? = null,
    val inForm: Boolean = false,
    val photoMode: PhotoMode = PhotoMode.DETOUREE,
    /** Niveau du fond (doublons), qui fixe son bonus. */
    val level: Int = 1,
) {
    val bonus get() = Bonus.points(stats, background?.card, level, background?.finish ?: Finish.NORMALE)
}

/**
 * Carte Athlète, recto et verso. La largeur fixe toutes les tailles (unité = 1 % de la largeur) pour que la même carte
 * soit nette en vignette dans l'album comme en plein écran. Le cadre suit la rareté du fond, la plaque le palier.
 */
@Composable
fun AthleteCard(face: CardFace, modifier: Modifier = Modifier, interactive: Boolean = true, animate: Boolean = true, flippable: Boolean = interactive) {
    val tiltState = rememberTilt(interactive)
    val tier = face.stats?.tier ?: Tier.BRONZE
    val bg = face.background
    val art = bg?.card ?: Catalog.defaultFor(tier)
    val finish = bg?.finish ?: if (tier >= Tier.ELITE) Finish.BRILLANTE else Finish.NORMALE
    val time by rememberCardTime(animate && (finish != Finish.NORMALE || art.rarity >= Rarity.RARE))
    // La carte se retourne du côté où l'on appuie, et tourne toujours dans ce sens (angle cumulé).
    var angle by remember { mutableFloatStateOf(0f) }
    val flip by animateFloatAsState(angle, spring(dampingRatio = 0.8f, stiffness = 220f), label = "flip")
    val haptics = app.superhealth.ui.rememberHaptics()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.aspectRatio(0.714f)) {
        val u = maxWidth / 100
        Box(Modifier.fillMaxSize().graphicsLayer {
            val t = tiltState.value
            rotationY = flip + if (interactive) t.x * 9f else 0f
            rotationX = if (interactive) -t.y * 7f else 0f
            cameraDistance = 14f * density.density
        }.then(if (flippable) Modifier.pointerInput(Unit) { detectTapGestures { pos -> angle += if (pos.x > size.width / 2) -180f else 180f; haptics.tick() } } else Modifier)) {
            if (facesFront(flip)) Front(face, art, finish, tier, u, { tiltState.value }, { time })
            else Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { Back(face, art, tier, u) }
        }
    }
}

/** Cadre de rareté posé au-dessus du contenu de la carte. */
@Composable
internal fun RarityFrame(rarity: Rarity, u: Dp, time: () -> Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = rarityColors(rarity)
    Box(modifier.fillMaxSize().drawBehind {
        if (rarity == Rarity.EPIQUE) {
            val t = time()
            drawRoundRect(Brush.sweepGradient(colors, center), topLeft = Offset(-u.toPx() * 2, -u.toPx() * 2),
                size = Size(size.width + u.toPx() * 4, size.height + u.toPx() * 4), cornerRadius = CornerRadius(size.width * 0.09f), alpha = 0.35f + 0.15f * sin(t * 3f), blendMode = BlendMode.Plus)
        }
    }) {
        Box(Modifier.fillMaxSize().clip(CardShape).drawWithContent {
            drawContent()
            val t = time()
            val px = u.toPx()
            val w = px * when (rarity) { Rarity.COMMUNE -> 1.5f; Rarity.PEU_COMMUNE -> 1.9f; Rarity.RARE -> 2.4f; Rarity.EPIQUE -> 3.1f }
            val radius = CornerRadius(size.width * 0.07f)
            // Reflet qui traverse la carte, à partir de Rare.
            if (rarity >= Rarity.RARE) {
                val k = ((t % 4.5f) / 1.3f)
                if (k < 1f) {
                    val x = -size.width * 0.6f + k * size.width * 2.2f
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.32f), Color.Transparent), Offset(x, 0f), Offset(x + size.width * 0.35f, size.height * 0.25f)), blendMode = BlendMode.Plus)
                }
            }
            val brush = when (rarity) {
                Rarity.EPIQUE -> Brush.sweepGradient(colors, center)
                Rarity.COMMUNE -> Brush.linearGradient(colors, Offset.Zero, Offset(size.width, size.height))
                else -> { val s = (t * 0.12f) % 1f; Brush.linearGradient(colors, Offset(size.width * (s * 2 - 1), 0f), Offset(size.width * (s * 2), size.height), TileMode.Mirror) }
            }
            drawRoundRect(brush, style = Stroke(w), cornerRadius = radius)
            if (rarity >= Rarity.RARE) drawRoundRect(colors[0].copy(alpha = 0.7f), topLeft = Offset(w * 1.15f, w * 1.15f), size = Size(size.width - w * 2.3f, size.height - w * 2.3f),
                style = Stroke(w * 0.22f), cornerRadius = CornerRadius(size.width * 0.055f))
            else drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(w, w), size = Size(size.width - w * 2, size.height - w * 2), style = Stroke(px * 0.3f), cornerRadius = CornerRadius(size.width * 0.06f))
            // Runes qui courent le long du bord intérieur.
            if (rarity >= Rarity.EPIQUE) drawRoundRect(colors[0].copy(alpha = 0.9f), topLeft = Offset(w * 1.9f, w * 1.9f), size = Size(size.width - w * 3.8f, size.height - w * 3.8f),
                style = Stroke(px * 0.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(px * 1.2f, px * 2.8f), -t * px * 14f)), cornerRadius = CornerRadius(size.width * 0.05f), blendMode = BlendMode.Plus)
            // Rivets ou filigranes aux coins.
            if (rarity >= Rarity.RARE) for ((cx, cy) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)) {
                val c = Offset(if (cx == 0f) w * 2.6f else size.width - w * 2.6f, if (cy == 0f) w * 2.6f else size.height - w * 2.6f)
                if (rarity >= Rarity.EPIQUE) {
                    val sx = if (cx == 0f) 1f else -1f; val sy = if (cy == 0f) 1f else -1f
                    val p = Path().apply { moveTo(c.x - sx * w, c.y + sy * px * 9); quadraticTo(c.x - sx * w, c.y - sy * w, c.x + sx * px * 9, c.y - sy * w) }
                    drawPath(p, colors[2], style = Stroke(px * 0.8f))
                    drawCircle(colors[0], px * 1.3f, c)
                } else {
                    val d = px * (if (rarity == Rarity.EPIQUE) 2.2f else 1.5f)
                    drawPath(Path().apply { moveTo(c.x, c.y - d); lineTo(c.x + d, c.y); lineTo(c.x, c.y + d); lineTo(c.x - d, c.y); close() }, colors[2])
                }
            }
            // Étincelles qui font le tour du cadre.
            if (rarity >= Rarity.EPIQUE) for (i in 0 until 5) {
                val k = (t * 0.12f + i / 5f) % 1f
                val per = 2 * (size.width + size.height)
                val d = k * per
                val pos = when {
                    d < size.width -> Offset(d, w * 0.5f)
                    d < size.width + size.height -> Offset(size.width - w * 0.5f, d - size.width)
                    d < 2 * size.width + size.height -> Offset(size.width - (d - size.width - size.height), size.height - w * 0.5f)
                    else -> Offset(w * 0.5f, size.height - (d - 2 * size.width - size.height))
                }
                with(Vfx) { star(pos, px * (2.6f + 1.2f * sin(t * 7 + i)), Color.White) }
            }
            // Cimier en haut au centre : une gemme par rang.
            val top = Offset(size.width / 2, w * 0.6f)
            when (rarity) {
                Rarity.COMMUNE -> {}
                Rarity.PEU_COMMUNE -> drawCircle(colors[2], px * 1.6f, top)
                Rarity.RARE -> {
                    val d = px * 2.8f
                    drawPath(Path().apply { moveTo(top.x, top.y - d * 0.3f); lineTo(top.x + d, top.y + d * 0.7f); lineTo(top.x, top.y + d * 1.7f); lineTo(top.x - d, top.y + d * 0.7f); close() }, Brush.verticalGradient(listOf(colors[0], colors[1])))
                }
                Rarity.EPIQUE -> {
                    val d = px * 4.6f
                    drawPath(Path().apply { moveTo(top.x - d * 1.5f, top.y + d * 1.6f); lineTo(top.x - d * 1.5f, top.y + d * 0.2f); lineTo(top.x - d * 0.75f, top.y + d * 0.9f); lineTo(top.x, top.y - d * 0.1f)
                        lineTo(top.x + d * 0.75f, top.y + d * 0.9f); lineTo(top.x + d * 1.5f, top.y + d * 0.2f); lineTo(top.x + d * 1.5f, top.y + d * 1.6f); close() }, Brush.verticalGradient(listOf(Color(0xFFFFF7D1), Color(0xFFE0A800))))
                    drawCircle(Brush.sweepGradient(colors, Offset(top.x, top.y + d)), px * 1.4f, Offset(top.x, top.y + d))
                }
            }
        }, content = content)
    }
}

@Composable
private fun Front(face: CardFace, art: CardDef, finish: Finish, tier: Tier, u: Dp, tilt: () -> Offset, time: () -> Float) {
    val stats = face.stats
    val shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 6f)
    fun ts(size: TextUnit, weight: FontWeight = FontWeight.Bold, spacing: TextUnit = TextUnit.Unspecified) =
        TextStyle(color = Color.White, fontSize = size, fontWeight = weight, letterSpacing = spacing, shadow = shadow)
    val density = LocalDensity.current
    val su = with(density) { u.toSp() }
    RarityFrame(art.rarity, u, time) {
        CardArt(art, finish, Modifier.fillMaxSize(), tilt, time, holo = finish != Finish.NORMALE || art.rarity.big)
        Photo(face, u)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 0.62f to Color.Black.copy(alpha = 0.35f), 1f to Color.Black.copy(alpha = 0.78f))))
        // Note sur la plaque du palier.
        Column(Modifier.padding(start = u * 6, top = u * 6).width(u * 30), horizontalAlignment = Alignment.CenterHorizontally) {
            val tc = tierColors(tier)
            Box(Modifier.drawBehind {
                val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height * 0.78f); lineTo(size.width / 2, size.height); lineTo(0f, size.height * 0.78f); close() }
                drawPath(p, Brush.verticalGradient(listOf(tc[1].copy(alpha = 0.85f), tc[3].copy(alpha = 0.9f))))
                drawPath(p, Brush.linearGradient(listOf(tc[0], tc[2], tc[0])), style = Stroke(u.toPx() * 0.7f))
            }.padding(horizontal = u * 2, vertical = u * 1.5f).padding(bottom = u * 4), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stats?.ovr?.toString() ?: "–", style = ts(su * 17, FontWeight.Black, (-1).sp), maxLines = 1)
                    Text(tr(tier.label).uppercase(), style = ts(su * 3.6f, FontWeight.Black, su * 0.5f), modifier = Modifier.offset(y = -u * 1.2f))
                }
            }
            if (!art.neutral) Row(Modifier.padding(top = u * 2), horizontalArrangement = Arrangement.spacedBy(-u * 2)) { art.elements.forEach { ElementChip(it, u) } }
        }
        if (face.inForm) Text(tr("EN FORME") + " ▲", style = ts(su * 3.6f, FontWeight.Black, su * 0.3f), modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 8, end = u * 6)
            .clip(RoundedCornerShape(50)).background(Color(0xFFFF4F7B)).padding(horizontal = u * 2.4f, vertical = u * 0.6f))
        Column(Modifier.fillMaxSize().padding(horizontal = u * 7).padding(bottom = u * 6), verticalArrangement = Arrangement.Bottom) {
            Text("@" + face.name, style = ts(su * 7, FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Box(Modifier.padding(vertical = u * 1.6f).fillMaxWidth().height(u * 0.35f).background(Brush.horizontalGradient(listOf(Color.Transparent, rarityColors(art.rarity)[0].copy(alpha = 0.9f), Color.Transparent))))
            val left = listOf(Stat.PEC, Stat.DOS); val right = listOf(Stat.BRA, Stat.JAM)
            Row(Modifier.fillMaxWidth()) {
                for (col in listOf(left, right)) Column(Modifier.weight(1f).padding(horizontal = u * 2), verticalArrangement = Arrangement.spacedBy(u * 0.2f)) {
                    for (s in col) StatLine(s, stats, face.bonus[s], su, ::ts)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = u * 2.2f), horizontalArrangement = Arrangement.spacedBy(u * 2, Alignment.CenterHorizontally)) {
                for (s in listOf(Stat.REG, Stat.CAR)) {
                    val b = face.bonus[s]
                    val v = stats?.get(s)
                    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).border(u * 0.25f, if (b != null) Element.of(s)!!.mid.c() else Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .padding(horizontal = u * 3, vertical = u * 0.8f), verticalAlignment = Alignment.CenterVertically) {
                        Text(v?.toString() ?: "–", style = ts(su * 5, FontWeight.Black))
                        Text(" " + s.code, style = ts(su * 3.8f, FontWeight.SemiBold))
                    }
                }
            }
            Text(art.rarity.symbol + "  " + (if (art.number > 0) art.code + " · " + art.series.name else "SUPERHEALTH"), style = ts(su * 2.8f, FontWeight.Bold, su * 0.4f).copy(color = rarityColors(art.rarity)[0]),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = u * 1.6f))
        }
    }
}

/** Photo de la carte : détourée (posée devant le fond) ou portrait de profil fondu dans le fond. */
@Composable
private fun BoxScope.Photo(face: CardFace, u: Dp) {
    val photo = face.photo
    if (photo == null) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.64f).padding(top = u * 4), contentAlignment = Alignment.BottomEnd) {
            Icon(if (face.stats?.female == true) Icons.Rounded.Woman else Icons.Rounded.Man, null, Modifier.fillMaxHeight().fillMaxWidth(0.7f), tint = Color.Black.copy(alpha = 0.28f))
        }
    } else if (face.photoMode == PhotoMode.DETOUREE) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.64f).padding(top = u * 4), contentAlignment = Alignment.BottomEnd) {
            Image(photo, null, Modifier.fillMaxHeight().fillMaxWidth(0.78f), contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter)
        }
    } else {
        Image(photo, null, Modifier.align(Alignment.TopEnd).fillMaxWidth(0.76f).fillMaxHeight(0.66f)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.3f to Color.Black), blendMode = BlendMode.DstIn)
            }, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
    }
}

/** Pastille de l'élément du fond (affinité en duel). */
@Composable
internal fun ElementChip(e: Element, u: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(u * 10).clip(CircleShape).background(Brush.radialGradient(listOf(e.mid.c(), e.outer.c()))).border(u * 0.5f, e.core.c().copy(alpha = 0.8f), CircleShape)) {
        Canvas(Modifier.fillMaxSize()) { with(Vfx) { emblem(e, center, size.minDimension * 0.3f, Color.White) } }
    }
}

@Composable
private fun StatLine(s: Stat, stats: AthleteStats?, bonus: Double?, su: TextUnit, ts: (TextUnit, FontWeight, TextUnit) -> TextStyle) {
    val v = stats?.get(s)
    val prov = stats?.provisional?.contains(s.code) == true
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(v?.toString() ?: "–", style = ts(su * 7, FontWeight.Black, TextUnit.Unspecified).let { if (prov) it.copy(color = Color.White.copy(alpha = 0.6f)) else it },
            modifier = Modifier.width(with(LocalDensity.current) { (su * 10).toDp() }))
        Text(s.code, style = ts(su * 4.6f, FontWeight.SemiBold, su * 0.2f).copy(color = Color.White.copy(alpha = 0.85f)))
        if (prov) Text(" ?", style = ts(su * 3.6f, FontWeight.Bold, TextUnit.Unspecified).copy(color = Color(0xFFFFD36B)))
        // Bonus du fond, au dixième : il s'ajoute en duel à la stat entière.
        if (bonus != null) Text(" " + Bonus.format(bonus), style = ts(su * 3.6f, FontWeight.Black, TextUnit.Unspecified).copy(color = Element.of(s)!!.mid.c()))
    }
}

@Composable
private fun Back(face: CardFace, art: CardDef, tier: Tier, u: Dp) {
    val stats = face.stats
    val su = with(LocalDensity.current) { u.toSp() }
    RarityFrame(art.rarity, u, { 0f }) {
        CardArt(art, Finish.NORMALE, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(0xE6080A12)))
        Column(Modifier.fillMaxSize().padding(u * 8), verticalArrangement = Arrangement.spacedBy(u * 2.2f)) {
            Text(tr("Détail par muscle"), color = Color.White, fontSize = su * 6, fontWeight = FontWeight.ExtraBold)
            for (s in Stat.families) {
                val v = stats?.get(s)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(s.label), color = Color.White.copy(alpha = 0.85f), fontSize = su * 4.2f, modifier = Modifier.weight(1f))
                        Text(tr(Element.of(s)!!.label) + (face.bonus[s]?.let { " " + Bonus.format(it) } ?: ""), color = Element.of(s)!!.mid.c(), fontSize = su * 3.4f, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = u * 2))
                        Text(v?.toString() ?: "–", color = Color.White, fontSize = su * 4.6f, fontWeight = FontWeight.Black)
                    }
                    Box(Modifier.padding(top = u * 0.8f).fillMaxWidth().height(u * 1.4f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth(((v ?: 0) / 99f).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Element.of(s)!!.outer.c(), Element.of(s)!!.mid.c()))))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            stats?.mostProfitable?.let {
                Text(tr("Le plus rentable") + " : " + tr(it.mine) + " (${stats[it]})", color = Color(0xFFFFD36B), fontSize = su * 4.2f, fontWeight = FontWeight.Bold)
            }
            if (!art.neutral) Text(art.elements.joinToString(" + ") { tr(it.label) } + " · " + art.rarityText() + " · " + tr("Niv.") + " ${face.level} · " + Bonus.label(art, face.level, face.background?.finish ?: Finish.NORMALE),
                color = art.element.mid.c(), fontSize = su * 3.6f, fontWeight = FontWeight.Bold)
            Text(tr("Touchez pour retourner"), color = Color.White.copy(alpha = 0.45f), fontSize = su * 3.2f, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Carte d'album : fond, cadre de rareté, bandeau avec gemmes, élément et numéro. */
@Composable
fun CollectibleCard(card: CardDef, finish: Finish, modifier: Modifier = Modifier, owned: Boolean = true, live: Boolean = false, tilt: () -> Offset = { Offset.Zero }, level: Int? = null) {
    val shiny = finish != Finish.NORMALE || card.rarity.big
    val time by rememberCardTime(live && (shiny || card.rarity >= Rarity.RARE))
    BoxWithConstraints(modifier.aspectRatio(0.714f)) {
        val u = maxWidth / 100
        val su = with(LocalDensity.current) { u.toSp() }
        val rc = rarityColors(card.rarity)
        if (!owned) {
            Box(Modifier.fillMaxSize().clip(CardShape).background(Brush.verticalGradient(listOf(Color(0xFF1B1E27), Color(0xFF0C0D12)))).border(u * 1.2f, rc[1].copy(alpha = 0.35f), CardShape)) {
                Text("?", color = Color.White.copy(alpha = 0.16f), fontSize = su * 40, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center).offset(y = -u * 8))
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = u * 8), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.rarity.symbol, color = rc[1].copy(alpha = 0.7f), fontSize = su * 9, fontWeight = FontWeight.Black)
                    Text(card.code, color = Color.White.copy(alpha = 0.45f), fontSize = su * 5, fontWeight = FontWeight.SemiBold)
                }
            }
            return@BoxWithConstraints
        }
        RarityFrame(card.rarity, u, { time }) {
            CardArt(card, finish, Modifier.fillMaxSize(), tilt, { time }, holo = shiny)
            Row(Modifier.align(Alignment.TopStart).padding(u * 7), horizontalArrangement = Arrangement.spacedBy(-u * 3)) { card.elements.forEach { ElementChip(it, u * 1.3f) } }
            if (finish != Finish.NORMALE) Text(when (finish) { Finish.CHROMATIQUE -> "✺"; Finish.ANIMEE -> "✦✦"; else -> "✦" }, color = Color.White, fontSize = su * 8, modifier = Modifier.align(Alignment.TopEnd).padding(u * 7))
            // Variante chromatique : liseré arc-en-ciel.
            if (finish == Finish.CHROMATIQUE) Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(Brush.sweepGradient(listOf(Color(0xFFFF4F7B), Color(0xFFFFD36B), Color(0xFF39D98A), Color(0xFF4FA8FF), Color(0xFFB46CFF), Color(0xFFFF4F7B))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.05f), style = Stroke(size.width * 0.018f))
            }
            // Bandeau de rareté : couleur, gemmes et nom, lisibles même en vignette.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f))))
                .padding(horizontal = u * 6).padding(top = u * 10, bottom = u * 7), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(card.name, color = Color.White, fontSize = su * 6.5f, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, lineHeight = su * 7.5f)
                Row(Modifier.padding(top = u * 2).clip(RoundedCornerShape(50)).background(if (card.rarity == Rarity.EPIQUE) Brush.horizontalGradient(rc) else Brush.horizontalGradient(listOf(rc[3], rc[1], rc[3]))).padding(horizontal = u * 4, vertical = u * 1),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(card.rarity.symbol + " " + card.rarityText().uppercase(), color = Color.White, fontSize = su * 4.2f, fontWeight = FontWeight.Black, letterSpacing = su * 0.3f)
                }
                Text(card.code + (level?.let { "  ·  " + "●".repeat(it) + "○".repeat(Levels.MAX - it) } ?: ""), color = Color.White.copy(alpha = 0.6f), fontSize = su * 4.2f,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = u * 1.4f))
            }
        }
    }
}

/**
 * Dos de carte. Sorti d'un booster, il prend le thème du biome : fond à sa couleur, motif de la matière (veines de
 * lave, facettes de glace, éclairs, courbes de niveau) et l'haltère au centre d'un cercle runique. [gold] : pack doré.
 */
/** « Épique », « Commune Duo » (traduit). */
internal fun CardDef.rarityText() = tr(rarity.label) + if (duo) " Duo" else ""

@Composable
fun CardBackside(modifier: Modifier = Modifier, glow: Color? = null, time: () -> Float = { 0f }, element: Element? = null, gold: Boolean = false) {
    BoxWithConstraints(modifier.aspectRatio(0.714f)) {
        val u = maxWidth / 100
        val su = with(LocalDensity.current) { u.toSp() }
        val accent = if (gold) Gold else element?.mid?.c()
        val top = element?.let { androidx.compose.ui.graphics.lerp(it.outer.c(), Color(0xFF0A0C14), 0.55f) } ?: Color(0xFF20263A)
        val edge = glow ?: accent?.copy(alpha = 0.8f) ?: Color(0xFF3A4260)
        Box(Modifier.fillMaxSize().clip(CardShape).background(Brush.linearGradient(listOf(top, Color(0xFF07080D)))).border(u * 1.6f, edge, CardShape)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height; val c = Offset(w / 2, h * 0.46f)
                val line = (accent ?: Color.White).copy(alpha = 0.16f); val sw = w * 0.006f
                when (element) {
                    // Veines de lave qui serpentent.
                    Element.FEU -> for (k in 0 until 7) { val path = Path(); val x0 = w * (k + 0.5f) / 7
                        for (j in 0..12) { val y = h * j / 12f; val x = x0 + w * 0.05f * kotlin.math.sin(j * 1.3f + k * 2.1f); if (j == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                        drawPath(path, line, style = Stroke(sw * (1.5f + k % 3))) }
                    // Facettes de glace : un treillis de losanges.
                    Element.GLACE -> { val step = w / 6f
                        for (i in -8..14) { drawLine(line, Offset(i * step, 0f), Offset(i * step + h * 0.58f, h), sw); drawLine(line, Offset(i * step, 0f), Offset(i * step - h * 0.58f, h), sw) } }
                    // Éclairs en diagonale.
                    Element.FOUDRE -> for (k in 0 until 6) { val path = Path(); val x0 = w * (k - 1) / 4f
                        for (j in 0..8) { val y = h * j / 8f; val x = x0 + y * 0.35f + (if (j % 2 == 0) -1 else 1) * w * 0.035f; if (j == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                        drawPath(path, line, style = Stroke(sw * 1.4f)) }
                    // Courbes de niveau autour du sommet.
                    Element.TERRE -> for (k in 1..9) drawOval(line, Offset(c.x - w * 0.09f * k, c.y - h * 0.05f * k), Size(w * 0.18f * k, h * 0.1f * k), style = Stroke(sw))
                    null -> { val step = w / 7f; for (i in -10..20) drawLine(Color.White.copy(alpha = 0.05f), Offset(i * step, 0f), Offset(i * step - h * 0.6f, h), strokeWidth = step * 0.18f) }
                }
                val halo = glow ?: accent
                if (halo != null) drawCircle(Brush.radialGradient(listOf(halo.copy(alpha = 0.42f + 0.18f * kotlin.math.sin(time() * 4f)), Color.Transparent), c, w * 0.62f), w * 0.62f, c)
                if (accent != null) with(Vfx) { magicCircle(c, w * 0.3f, time() * 0.6f, accent, 1f, alpha = 0.55f) }
                if (gold) for (k in 0 until 2) drawRect(Brush.linearGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.28f), Color.Transparent),
                    Offset(w * (-0.6f + k * 0.8f) + (time() * 0.25f % 1f) * w * 1.6f, 0f), Offset(w * (-0.2f + k * 0.8f) + (time() * 0.25f % 1f) * w * 1.6f, h)), blendMode = BlendMode.Plus)
            }
            Icon(Icons.Rounded.FitnessCenter, null, Modifier.align(Alignment.Center).offset(y = -u * 6).size(u * 30),
                tint = accent?.let { androidx.compose.ui.graphics.lerp(Color.White, it, 0.35f) } ?: Color.White.copy(alpha = 0.75f))
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = u * 11), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SUPERHEALTH", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Black, fontSize = su * 6, letterSpacing = su * 1.2f)
                val sub = if (gold) "PACK DORÉ" else element?.let { tr(it.label).uppercase() }
                if (sub != null) Text(sub, color = (accent ?: Color.White).copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = su * 4.2f, letterSpacing = su * 0.9f, modifier = Modifier.padding(top = u * 1))
            }
        }
    }
}

/** Vibrations riches (moteur haptique du Pixel) pour les révélations ; même réglage que [app.superhealth.ui.Haptics]. */
object Rumble {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)

    /** Joue les effets à 40 ms d'intervalle (comme une composition de vibrations Android). */
    fun play(ctx: Context, vararg primitives: Pair<HapticKind, Float>) {
        if (!app.superhealth.SuperPrefs.settings.value.haptics) return
        scope.launch { primitives.forEachIndexed { i, (p, s) -> if (i > 0) kotlinx.coroutines.delay(40); hapticEffect(p, s) } }
    }
    fun tick(ctx: Context, scale: Float = 0.4f) = play(ctx, HapticKind.TICK to scale)
    fun thud(ctx: Context) = play(ctx, HapticKind.THUD to 1f)
    /** Choc d'un aller-retour pendant le bras de fer d'une stat. */
    fun clash(ctx: Context, scale: Float) = play(ctx, HapticKind.CLICK to scale)
    /** Une stat tranchée : coup sourd puis claquement. */
    fun slam(ctx: Context) = play(ctx, HapticKind.THUD to 1f, HapticKind.CLICK to 0.8f)
    /** Une stat écrasée (5 points d'écart ou plus) : double coup sourd puis claquement. */
    fun impact(ctx: Context) = play(ctx, HapticKind.THUD to 1f, HapticKind.THUD to 0.8f, HapticKind.CLICK to 1f)
    fun rise(ctx: Context) = play(ctx, HapticKind.RISE to 0.7f, HapticKind.RISE to 0.9f)
    fun reveal(ctx: Context) = play(ctx, HapticKind.THUD to 1f, HapticKind.CLICK to 1f, HapticKind.CLICK to 0.6f)
}

/** Carte partagée dans le Fil ou un Cercle : le fond lui-même, vivant, avec son niveau et la note de l'auteur. */
@Composable
fun SharedCardVisual(shared: CardShare.Shared, modifier: Modifier = Modifier) {
    val tilt by rememberTilt()
    Column(modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CollectibleCard(shared.pull.card, shared.pull.finish, Modifier.fillMaxWidth(0.62f), live = true, tilt = { tilt }, level = shared.level)
        shared.ovr?.let { ovr ->
            val tier = Tier.of(ovr)
            Text(tr("Note") + " $ovr · " + tr(tier.label), color = rarityColors(shared.pull.card.rarity)[1], fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
