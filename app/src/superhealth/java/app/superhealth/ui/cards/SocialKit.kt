package app.superhealth.ui.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.cards.AthleteStats
import app.superhealth.cards.Stat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Couleurs des deux camps, partout où deux cartes se comparent : moi en rouge, l'autre en bleu. */
internal val SideMine = Color(0xFFFF4F5E)
internal val SideTheirs = Color(0xFF6E7BFF)

/** Panneau des écrans de cartes : verre sombre, liseré fin, reflet teinté en haut à gauche quand [tint] est donné. */
@Composable
internal fun Panel(modifier: Modifier = Modifier, tint: Color? = null, padding: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.clip(shape)
        .background(Brush.linearGradient(listOf((tint ?: Color.White).copy(alpha = if (tint != null) 0.20f else 0.08f), Color.White.copy(alpha = 0.035f)), Offset.Zero, Offset(700f, 700f)))
        .background(InkCard.copy(alpha = 0.72f))
        .border(1.dp, Brush.verticalGradient(listOf((tint ?: Color.White).copy(alpha = if (tint != null) 0.45f else 0.14f), Color.White.copy(alpha = 0.04f))), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding), content = content)
}

/** Anneau de progression autour d'un contenu (icône, chiffre). */
@Composable
internal fun Ring(progress: Float, color: Color, size: Dp, stroke: Dp = 4.dp, content: @Composable BoxScope.() -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) { anim.animateTo(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing)) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx(); val inset = Offset(w / 2, w / 2); val s = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            drawArc(Color.White.copy(alpha = 0.10f), 0f, 360f, false, inset, s, style = Stroke(w))
            if (anim.value > 0f) drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color, color.copy(alpha = 0.55f))), -90f, 360f * anim.value, false, inset, s, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Pastille ronde teintée portant une icône. */
@Composable
internal fun Medallion(icon: ImageVector, color: Color, size: Dp = 38.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(color.copy(alpha = 0.34f), color.copy(alpha = 0.10f)))), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = color)
    }
}

/** Petite étiquette en capitales. */
@Composable
internal fun Tag(text: String, color: Color, modifier: Modifier = Modifier) = Text(text.uppercase(), color = color, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1,
    modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))

/**
 * Toile des six stats : la mienne en plein, celle de [other] en contour par-dessus. L'échelle commence à 40 pour que les
 * écarts se voient.
 */
@Composable
internal fun StatRadar(mine: AthleteStats, modifier: Modifier = Modifier, other: AthleteStats? = null, color: Color = Color(0xFF8AE0FF), otherColor: Color = Color(0xFFFF8A80),
                       valueSize: androidx.compose.ui.unit.TextUnit = 15.sp, codeSize: androidx.compose.ui.unit.TextUnit = 10.sp, labelGap: Dp = 26.dp, bonus: Map<Stat, Double> = emptyMap()) {
    val measurer = rememberTextMeasurer()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(mine, other) { anim.snapTo(0f); anim.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    val stats = Stat.entries
    val value = TextStyle(color = Color.White, fontSize = valueSize, fontWeight = FontWeight.Black)
    val code = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = codeSize, fontWeight = FontWeight.Bold)
    Canvas(modifier.aspectRatio(1.25f)) {
        val c = center; val r = size.height * 0.30f; val n = stats.size
        fun dir(i: Int): Offset { val a = -PI / 2 + i * 2 * PI / n; return Offset(cos(a).toFloat(), sin(a).toFloat()) }
        fun norm(v: Int?) = if (v == null) 0.06f else ((v - 40) / 59f).coerceIn(0.10f, 1f)
        fun poly(k: (Int) -> Float) = Path().apply { for (i in 0 until n) { val p = c + dir(i) * (r * k(i)); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
        for (ring in 1..4) drawPath(poly { ring / 4f }, Color.White.copy(alpha = if (ring == 4) 0.20f else 0.07f), style = Stroke(1.dp.toPx()))
        for (i in 0 until n) drawLine(Color.White.copy(alpha = 0.07f), c, c + dir(i) * r, 1.dp.toPx())
        val shape = poly { norm(mine[stats[it]]) * anim.value }
        drawPath(shape, Brush.radialGradient(listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.55f)), c, r))
        drawPath(shape, color, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
        for (i in 0 until n) drawCircle(Color.White, 3.dp.toPx(), c + dir(i) * (r * norm(mine[stats[i]]) * anim.value))
        if (other != null) {
            val theirs = poly { norm(other[stats[it]]) * anim.value }
            drawPath(theirs, otherColor.copy(alpha = 0.14f))
            drawPath(theirs, otherColor, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
        }
        for (i in 0 until n) {
            val p = c + dir(i) * (r + labelGap.toPx())
            val v = measurer.measure(mine[stats[i]]?.toString() ?: "–", value); val l = measurer.measure(stats[i].code, if (bonus[stats[i]] != null) code.copy(color = Color(app.superhealth.cards.Element.of(stats[i])!!.mid.toInt())) else code)
            val h = v.size.height + l.size.height
            drawText(v, topLeft = Offset(p.x - v.size.width / 2f, p.y - h / 2f))
            drawText(l, topLeft = Offset(p.x - l.size.width / 2f, p.y - h / 2f + v.size.height * 0.88f))
        }
    }
}

/** Deux barres dos à dos partant du centre : la mienne à gauche, celle de l'autre à droite. */
@Composable
internal fun MirrorBar(a: Int?, b: Int?, label: String, mine: Color, theirs: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(a, b) { anim.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val aw = (a ?: 0) > (b ?: 0); val bw = (b ?: 0) > (a ?: 0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(a?.toString() ?: "–", color = if (aw) mine else Color.White.copy(alpha = if (bw) 0.55f else 0.9f), fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Canvas(Modifier.padding(top = 3.dp).fillMaxWidth().height(7.dp)) {
                val mid = size.width / 2; val gap = 2.dp.toPx(); val rad = androidx.compose.ui.geometry.CornerRadius(size.height)
                fun len(v: Int?) = (mid - gap) * (((v ?: 40) - 40) / 59f).coerceIn(0.06f, 1f) * anim.value
                drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = rad)
                drawRoundRect(mine.copy(alpha = if (bw) 0.45f else 1f), Offset(mid - gap - len(a), 0f), androidx.compose.ui.geometry.Size(len(a), size.height), rad)
                drawRoundRect(theirs.copy(alpha = if (aw) 0.45f else 1f), Offset(mid + gap, 0f), androidx.compose.ui.geometry.Size(len(b), size.height), rad)
            }
        }
        Text(b?.toString() ?: "–", color = if (bw) theirs else Color.White.copy(alpha = if (aw) 0.55f else 0.9f), fontWeight = FontWeight.Black, fontSize = 17.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(32.dp))
    }
}

/** Bouton secondaire des écrans de cartes : un simple contour, à côté d'un [GlowButton]. */
@Composable
internal fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(54.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f)).border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(50))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}
