package app.superhealth.ui.cards

import app.superhealth.platform.*

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.superhealth.cards.CardStore
import app.superhealth.cards.Element
import app.superhealth.ui.tr

/**
 * Pop-up de la mise à jour Dopamine, une seule fois : un éventail de dos de packs (un par biome, le doré au centre),
 * le nom de la mise à jour qui pulse, ce qui change en quatre lignes, et le bouton qui mène droit aux packs offerts.
 */
@Composable
fun DopamineIntro(onOpenPacks: () -> Unit) {
    val ctx = LocalContext.current
    val st by CardStore.state.collectAsState()
    LaunchedEffect(Unit) { CardStore.load(ctx) }
    if (st.dopamineSeen) return
    fun close(open: Boolean) { CardStore.update(ctx) { it.copy(dopamineSeen = true) }; if (open) onOpenPacks() }
    val time by rememberCardTime()
    val t = rememberInfiniteTransition(label = "dopamine")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 180f)) }
    Dialog({ close(false) }, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A0630), Ink, Color(0xFF2A0F00)))).drawBehind {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF2FA0).copy(alpha = 0.35f + 0.2f * pulse), Color.Transparent), Offset(size.width * 0.5f, size.height * 0.3f), size.width * 0.9f),
                size.width * 0.9f, Offset(size.width * 0.5f, size.height * 0.3f))
            drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.25f), Color.Transparent), Offset(size.width * 0.5f, size.height * 0.95f), size.width * 0.8f),
                size.width * 0.8f, Offset(size.width * 0.5f, size.height * 0.95f))
        }.clickable(remember { MutableInteractionSource() }, null) {}.systemBarsPadding().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * 120f }) {
                // Éventail : quatre biomes de part et d'autre, le pack doré au centre, qui flottent.
                Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.BottomCenter) {
                    val fan = listOf(Element.FEU to -2, Element.GLACE to -1, Element.FOUDRE to 1, Element.TERRE to 2)
                    fan.forEach { (e, k) ->
                        CardBackside(Modifier.width(104.dp).graphicsLayer {
                            rotationZ = k * 11f * enter.value; translationX = k * 52.dp.toPx() * enter.value
                            translationY = kotlin.math.abs(k) * 18.dp.toPx() + kotlin.math.sin(time * 1.6f + k) * 5f
                        }, glow = e.mid.let { Color(it.toInt()) }, time = { time }, element = e)
                    }
                    CardBackside(Modifier.width(128.dp).graphicsLayer { translationY = -10.dp.toPx() + kotlin.math.sin(time * 2f) * 7f; scaleX = 1f + 0.03f * pulse; scaleY = 1f + 0.03f * pulse },
                        glow = Gold, time = { time }, gold = true)
                }
                Text(tr("MISE À JOUR"), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 6.sp, modifier = Modifier.padding(top = 22.dp))
                Text("DOPAMINE", fontWeight = FontWeight.Black, fontSize = 54.sp, letterSpacing = 2.sp,
                    style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFFFF4F7B), Color(0xFFFFB238), Gold, Color(0xFFFF8AD8)),
                        start = Offset(-200f + 400f * pulse, 0f), end = Offset(400f + 400f * pulse, 120f)),
                        shadow = Shadow(Color(0xFFFF2FA0).copy(alpha = 0.6f + 0.3f * pulse), Offset.Zero, 28f)))
                Text(tr("Chaque séance te rapproche du prochain pack."), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.07f)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Perk("🎁", tr("10 packs offerts"), tr("dont un pack doré, aux chances triplées"))
                    Perk("✦", tr("180 fonds à collectionner"), tr("3 à 4 cartes par pack, duos et chromatiques très rares"))
                    Perk("⇄", tr("Fusionne et échange"), tr("tes doublons renforcent tes fonds, ou partent chez tes amis"))
                    Perk("⚔", tr("Quêtes et combats"), tr("le Groupe tourne désormais autour de ta carte"))
                }
                GlowButton(tr("Ouvrir mes 10 packs"), Modifier.fillMaxWidth().padding(top = 22.dp), listOf(Color(0xFFFF2FA0), Color(0xFFFFB238)),
                    badge = st.packsAvailable.takeIf { it > 0 }?.toString()) { close(true) }
                Text(tr("Plus tard"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50))
                    .clickable { close(false) }.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun Perk(icon: String, title: String, detail: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF4F7B).copy(alpha = 0.5f), Gold.copy(alpha = 0.4f)))), contentAlignment = Alignment.Center) {
        Text(icon, fontSize = 18.sp, color = Color.White)
    }
    Column(Modifier.padding(start = 12.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(detail, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

/**
 * Cadeau du serveur (packs offerts avec une mise à jour, ou donnés à la main) : un pack doré qui flotte entre deux packs,
 * le nombre de packs et le mot du cadeau, une fois par cadeau. Passe après la pop-up Dopamine.
 */
@Composable
fun GiftIntro(onOpenPacks: () -> Unit) {
    val ctx = LocalContext.current
    val st by CardStore.state.collectAsState()
    val gift = st.gifts.firstOrNull()?.takeIf { st.dopamineSeen } ?: return
    fun close(open: Boolean) { CardStore.giftSeen(ctx, gift.id); if (open) onOpenPacks() }
    val time by rememberCardTime()
    val enter = remember(gift.id) { Animatable(0f) }
    LaunchedEffect(gift.id) { enter.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 200f)) }
    Dialog({ close(false) }, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF07122A), Ink, Color(0xFF231A05)))).drawBehind {
            drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.35f), Color.Transparent), Offset(size.width * 0.5f, size.height * 0.32f), size.width * 0.8f),
                size.width * 0.8f, Offset(size.width * 0.5f, size.height * 0.32f))
        }.clickable(remember { MutableInteractionSource() }, null) {}.systemBarsPadding().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * 100f }) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.BottomCenter) {
                    listOf(Element.GLACE to -1, Element.FEU to 1).forEach { (e, k) ->
                        CardBackside(Modifier.width(100.dp).graphicsLayer {
                            rotationZ = k * 13f * enter.value; translationX = k * 60.dp.toPx() * enter.value
                            translationY = 16.dp.toPx() + kotlin.math.sin(time * 1.5f + k) * 5f
                        }, glow = Color(e.mid.toInt()), time = { time }, element = e)
                    }
                    CardBackside(Modifier.width(124.dp).graphicsLayer { translationY = -8.dp.toPx() + kotlin.math.sin(time * 2f) * 7f }, glow = Gold, time = { time }, gold = true)
                }
                Text(tr("CADEAU"), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 6.sp, modifier = Modifier.padding(top = 22.dp))
                val amount = listOfNotNull(gift.packs.takeIf { it > 0 }?.let { "$it " + tr(if (it > 1) "packs offerts" else "pack offert") },
                    gift.eclats.takeIf { it > 0 }?.let { "$it " + tr("éclats") }).joinToString(" + ")
                Text(amount, fontWeight = FontWeight.Black, fontSize = 34.sp, textAlign = TextAlign.Center,
                    style = TextStyle(brush = Brush.linearGradient(listOf(Gold, Color(0xFFFFB238), Color(0xFFFFF2B0))), shadow = Shadow(Gold.copy(alpha = 0.6f), Offset.Zero, 24f)))
                if (gift.note.isNotBlank()) Text(gift.note, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                GlowButton(tr(if (gift.packs > 0) "Ouvrir mes packs" else "Super"), Modifier.fillMaxWidth().padding(top = 28.dp), listOf(Gold, Color(0xFFFF8A1F)),
                    badge = st.packsAvailable.takeIf { it > 0 }?.toString()) { close(gift.packs > 0) }
                Text(tr("Plus tard"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50))
                    .clickable { close(false) }.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
    }
}
