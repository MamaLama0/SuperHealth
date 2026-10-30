package app.superhealth.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.ui.theme.SansDisplay
import app.superhealth.ui.theme.SansText

/*
 * Socle des styles complets : un [Style] décide de la FORME (coins, construction des cartes, épaisseur et bouts des traits,
 * barres, pastilles d'icônes, densité, typo, casse des étiquettes) autant que des couleurs. Chaque carte publie sa
 * [Surf] (encres, piste, fond secondaire, « sur couleur ») : un widget posé sur une carte pleine couleur bascule seul en
 * blanc, un style monochrome passe les données à l'encre.
 */

enum class Corner { ROUND, LEAF, SQUARE }
enum class CardM { FILLED, OUTLINED, ELEVATED, TINTED, SOLID }
enum class BarS { PILL, SQUARE, SEGMENT }
enum class IconBox { NONE, CIRCLE, COOKIE, SQUIRCLE }
enum class NavS { PILL, BAR, DOCK, TOPTABS, SEGMENTS, NOTER }

data class Style(
    val name: String, val title: String, val pitch: String,
    val base: Base, val hues: MHues,
    val card: CardM = CardM.FILLED, val corner: Corner = Corner.ROUND, val radius: Dp = 26.dp,
    val stroke: Float = 1f, val butt: Boolean = false, val bar: BarS = BarS.PILL,
    val text: FontFamily = SansText, val display: FontFamily = SansDisplay, val numW: FontWeight = FontWeight.SemiBold, val caps: Boolean = false,
    val icon: IconBox = IconBox.NONE, val pad: Dp = 16.dp, val gap: Dp = 12.dp,
    val gradient: Boolean = true, val area: Boolean = true, val mono: Boolean = false, val nav: NavS = NavS.PILL,
) {
    val dark get() = base.dark
    val cap get() = if (butt) StrokeCap.Butt else StrokeCap.Round
    fun shape(scale: Float = 1f): Shape = when (corner) {
        Corner.ROUND, Corner.SQUARE -> RoundedCornerShape(radius * scale)
        Corner.LEAF -> RoundedCornerShape(topStart = radius * scale, topEnd = radius * scale * 0.22f, bottomEnd = radius * scale, bottomStart = radius * scale * 0.22f)
    }
    fun tint(c: Color, a: Float): Color = if (dark && c.warm()) base.s2 else c.copy(alpha = a)
}

/** Ce que voit un widget à l'intérieur de sa carte. */
data class Surf(val ink: Color, val sub: Color, val s2: Color, val dim: Color, val on: Boolean)

val LocalS = staticCompositionLocalOf<Style> { error("style") }
val LocalSurf = staticCompositionLocalOf { Surf(Color.White, Color.Gray, Color.DarkGray, Color.DarkGray, false) }
@Composable fun st() = LocalS.current
@Composable fun sf() = LocalSurf.current

@Composable
fun ProvideStyle(s: Style, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalS provides s, LocalD provides Design(s.base, s.hues, 0f, !s.gradient, s.radius),
        LocalSurf provides Surf(s.base.ink, s.base.sub, s.base.s2, s.base.dim, false), content = content)

// ---- couleurs des données ---------------------------------------------------------------------------------------------------

/** Teinte d'une donnée selon la surface : blanc (ou encre) sur carte pleine, encre en monochrome. */
@Composable
fun data(h: Hue, accent: Boolean = false): Hue {
    val s = st(); val f = sf()
    return when { f.on -> Hue(f.ink, f.ink.copy(alpha = 0.78f)); s.mono && !accent -> Hue(s.base.ink, s.base.ink); else -> h }
}

/** Séries (macros, phases…) : leurs couleurs, ou des opacités d'une seule encre. */
@Composable
fun series(list: List<Hue>): List<Hue> {
    val s = st(); val f = sf()
    if (!f.on && !s.mono) return list
    val ink = if (f.on) f.ink else s.base.ink
    return list.indices.map { i -> val a = listOf(1f, 0.62f, 0.4f, 0.22f)[i % 4]; Hue(ink.copy(alpha = a), ink.copy(alpha = a)) }
}

@Composable
fun track(h: Hue): Color {
    val s = st(); val f = sf()
    return when { f.on -> f.ink.copy(alpha = 0.22f); s.mono -> f.s2; else -> s.tint(h.b, if (s.dark) 0.16f else 0.16f) }
}

@Composable fun brush(h: Hue): Brush = if (st().gradient && h.a != h.b) Brush.horizontalGradient(listOf(h.a, h.b)) else SolidColor(h.a)

// ---- texte ------------------------------------------------------------------------------------------------------------------

enum class Role { TEXT, DISPLAY, NUM, LABEL }

@Composable
fun Tx(text: String, size: TextUnit, role: Role = Role.TEXT, color: Color? = null, weight: FontWeight? = null, modifier: Modifier = Modifier,
       lines: Int = 1, align: TextAlign = TextAlign.Unspecified) {
    val s = st(); val f = sf()
    val label = role == Role.LABEL
    val fam = when (role) { Role.TEXT, Role.LABEL -> s.text; else -> s.display }
    val w = weight ?: when (role) { Role.NUM -> s.numW; Role.DISPLAY -> FontWeight.SemiBold; Role.LABEL -> if (s.caps) FontWeight.SemiBold else FontWeight.Medium; Role.TEXT -> FontWeight.Normal }
    val sz = if (label && s.caps) (size.value * 0.82f).sp else size
    Text(if (label && s.caps) text.uppercase() else text, modifier, maxLines = lines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color ?: if (label) f.sub else f.ink, fontFamily = fam, fontSize = sz, fontWeight = w, textAlign = align,
            letterSpacing = if (label && s.caps) 1.3.sp else if (role == Role.NUM && s.numW >= FontWeight.Bold) (-0.5).sp else 0.sp, fontFeatureSettings = "tnum"))
}

// ---- cartes -----------------------------------------------------------------------------------------------------------------

@Composable
fun Card5(hue: Hue?, modifier: Modifier = Modifier, pad: Dp? = null, content: @Composable ColumnScope.() -> Unit) {
    val s = st(); val b = s.base; val shape = s.shape(); val p = pad ?: s.pad
    val solid = s.card == CardM.SOLID && hue != null
    val inkOnHue = hue?.let { if (it.a.mix(it.b, 0.5f).luminance() > 0.62f) Color(0xFF0C1A15) else Color.White } ?: b.ink
    val (m, surf) = when {
        solid -> modifier.clip(shape).background(if (s.gradient) Brush.linearGradient(listOf(hue!!.a, hue.b)) else SolidColor(hue!!.a)) to
            Surf(inkOnHue, inkOnHue.copy(alpha = 0.72f), inkOnHue.copy(alpha = 0.16f), inkOnHue.copy(alpha = 0.3f), true)
        s.card == CardM.TINTED && hue != null -> {
            val warmDark = s.dark && hue.a.warm()
            val bg = when { warmDark -> b.s1; s.dark -> hue.a.mix(b.s1, 0.86f); else -> hue.a.mix(Color.White, 0.87f) }
            val s2 = when { warmDark -> b.s2; s.dark -> hue.a.mix(b.s1, 0.74f); else -> Color.White.copy(alpha = 0.7f) }
            modifier.clip(shape).background(bg) to Surf(b.ink, b.sub, s2, b.dim, false)
        }
        s.card == CardM.OUTLINED -> modifier.clip(shape).background(b.s1).border(1.dp, if (s.dark) Color(0x26FFFFFF) else Color(0x1F000000), shape) to Surf(b.ink, b.sub, b.s2, b.dim, false)
        s.card == CardM.ELEVATED -> modifier.shadow(if (s.dark) 0.dp else 10.dp, shape, ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000)).clip(shape).background(b.s1) to
            Surf(b.ink, b.sub, b.s2, b.dim, false)
        else -> modifier.clip(shape).background(b.s1).border(1.dp, b.line, shape) to Surf(b.ink, b.sub, b.s2, b.dim, false)
    }
    CompositionLocalProvider(LocalSurf provides surf) { Column(m.padding(p), content = content) }
}

/** Pastille d'icône selon le style (aucune, rond, biscuit, squircle). */
@Composable
fun IconIn(icon: ImageVector, hue: Hue, size: Dp = 36.dp) {
    val s = st(); val f = sf(); val h = data(hue)
    when (s.icon) {
        IconBox.NONE -> Icon(icon, null, Modifier.size(size * 0.5f), tint = h.a)
        else -> {
            val shape: Shape = when (s.icon) { IconBox.COOKIE -> cookie(8, 0.07f); IconBox.SQUIRCLE -> RoundedCornerShape(size * 0.32f); else -> CircleShape }
            val bg = if (f.on) f.ink.copy(alpha = 0.2f) else if (s.mono) f.s2 else if (!s.dark) Color.White.takeIf { s.card == CardM.TINTED } ?: s.tint(h.a, 0.14f) else s.tint(h.a, 0.18f)
            Box(Modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(size * 0.55f), tint = if (s.dark && h.a.warm() && !f.on) h.a else h.a)
            }
        }
    }
}

@Composable
fun Label5(icon: ImageVector?, text: String, hue: Hue, modifier: Modifier = Modifier) {
    val s = st()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            if (s.icon == IconBox.NONE) { Icon(icon, null, Modifier.size(16.dp), tint = data(hue).a); Spacer(Modifier.width(6.dp)) }
            else { IconIn(icon, hue, 28.dp); Spacer(Modifier.width(8.dp)) }
        }
        Tx(text, 13.sp, Role.LABEL)
    }
}

/** Barre horizontale selon le style : pilule, angles vifs, ou segments. */
@Composable
fun HBar(f: Float, h: Hue, modifier: Modifier, height: Dp = 8.dp) {
    val s = st(); val hd = data(h); val tr = track(h); val br = brush(hd)
    when (s.bar) {
        BarS.SEGMENT -> Row(modifier.height(height)) {
            val n = 16; val lit = (f * n).let { kotlin.math.round(it).toInt() }
            repeat(n) { i -> Box(Modifier.weight(1f).fillMaxHeight().padding(end = if (i < n - 1) 2.dp else 0.dp).clip(RoundedCornerShape(1.dp)).background(if (i < lit) SolidColor(hd.a) else SolidColor(tr))) }
        }
        else -> {
            val sh = if (s.bar == BarS.PILL) CircleShape else RoundedCornerShape(2.dp)
            Box(modifier.height(height).clip(sh).background(tr)) { Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(sh).background(br)) }
        }
    }
}

/** Anneau ou arc selon le style (bouts ronds ou coupés, épaisseur, dégradé). */
fun DrawScope.arc5(s: Style, c: Offset, r: Float, w: Float, f: Float, h: Hue, tr: Color, from: Float = -90f, span: Float = 360f) {
    val cap = s.cap; val tl = Offset(c.x - r, c.y - r); val sz = Size(2 * r, 2 * r)
    drawArc(tr, from, span, false, tl, sz, style = Stroke(w, cap = cap))
    if (f <= 0f) return
    val sweep = span * f
    if (!s.gradient || h.a == h.b) drawArc(h.a, from, sweep, false, tl, sz, style = Stroke(w, cap = cap))
    else rotate(from, c) {
        val e = sweep / 360f
        drawArc(Brush.sweepGradient(0f to h.a, e to h.b, minOf(e + 0.05f, 0.98f) to h.b, 1f to h.a, center = c), 0f, sweep, false, tl, sz, style = Stroke(w, cap = cap))
    }
}
