package app.superhealth.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
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
 * Studio : kit de design du labo. Un [Design] = une base (fonds, encres) + des teintes par métrique + des réglages
 * (halo, aplats ou dégradés, coins, style de carte). Tous les widgets du catalogue lisent [LocalD] : on change un
 * réglage, tout le catalogue suit. Spécification texte : « base=graphite hues=vif glow=0 flat=0 r=26 card=filled ».
 */

data class Hue(val a: Color, val b: Color) { val mid get() = a.mix(b, 0.5f) }

data class MHues(val forme: Hue, val charge: Hue, val repos: Hue, val effort: Hue, val kcal: Hue, val sleep: Hue, val heart: Hue,
                val steps: Hue, val prot: Hue, val fat: Hue, val carb: Hue, val weight: Hue, val action: Hue)

data class Base(val name: String, val dark: Boolean, val bg: Color, val s1: Color, val s2: Color, val line: Color, val ink: Color, val sub: Color, val dim: Color)

enum class CardS { FILLED, OUTLINED, ELEVATED }

data class Design(val base: Base, val hues: MHues, val glow: Float = 0f, val flat: Boolean = false, val radius: Dp = 26.dp, val card: CardS = CardS.FILLED) {
    val dark get() = base.dark
    fun fill(h: Hue): Brush = if (flat) SolidColor(h.a) else Brush.horizontalGradient(listOf(h.a, h.b))
    fun vfill(h: Hue, top: Float = 0f, bottom: Float = Float.POSITIVE_INFINITY): Brush = if (flat) SolidColor(h.a) else Brush.verticalGradient(listOf(h.b, h.a), top, bottom)
    /** Teinte légère d'une couleur ; une teinte chaude assombrie vire au marron, on garde alors le neutre. */
    fun tint(c: Color, a: Float): Color = if (dark && c.warm()) base.s2 else c.copy(alpha = a)
    fun track(h: Hue) = tint(h.b, if (dark) 0.16f else 0.18f)
}

val GRAPHITE = Base("graphite", true, Color(0xFF111215), Color(0xFF1B1C21), Color(0xFF26272E), Color(0x12FFFFFF), Color(0xFFF3F3F6), Color(0xFF9A9BA4), Color(0xFF45464E))
val NOIR = Base("noir", true, Color(0xFF000000), Color(0xFF0F0F12), Color(0xFF1B1B21), Color(0x14FFFFFF), Color.White, Color(0xFF8E8E96), Color(0xFF3A3A40))
val ARDOISE = Base("ardoise", true, Color(0xFF0D1118), Color(0xFF161C26), Color(0xFF212938), Color(0x14FFFFFF), Color(0xFFEEF2F8), Color(0xFF8D97A8), Color(0xFF3A4352))
val PORCELAINE = Base("porcelaine", false, Color(0xFFF2F3F6), Color.White, Color(0xFFEDEEF2), Color(0x12000000), Color(0xFF15161A), Color(0xFF6C6F78), Color(0xFFCDCFD5))
val BRUME = Base("brume", false, Color(0xFFE8EDF4), Color(0xFFF9FBFD), Color(0xFFE0E6EF), Color(0x12000000), Color(0xFF172033), Color(0xFF66728A), Color(0xFFBCC6D6))
val BASES = listOf(GRAPHITE, NOIR, ARDOISE, PORCELAINE, BRUME)

private fun hu(a: Long, b: Long) = Hue(Color(a), Color(b))
val VIF_DARK = MHues(hu(0xFF3DF5A7, 0xFF26C6FF), hu(0xFF0A84FF, 0xFF64D2FF), hu(0xFF7D5CFF, 0xFFD17BFF), hu(0xFFFF6A3D, 0xFFFF3D8B),
    hu(0xFF2EE6C5, 0xFF20B8FF), hu(0xFF6E6BFF, 0xFFB387FF), hu(0xFFFF375F, 0xFFFF8AAE), hu(0xFFFFD60A, 0xFFFF9F0A),
    hu(0xFFFF5E7E, 0xFFFF9AA8), hu(0xFFFFD60A, 0xFFFFB340), hu(0xFF3DF5A7, 0xFF7CFFCB), hu(0xFF64D2FF, 0xFF0A84FF), hu(0xFF7D5CFF, 0xFFFF3D8B))
val VIF_LIGHT = MHues(hu(0xFF12B886, 0xFF0EA5E9), hu(0xFF2563EB, 0xFF3B9CFF), hu(0xFF7C3AED, 0xFFB65CFF), hu(0xFFFF5A36, 0xFFFF2E7A),
    hu(0xFF0FB5A0, 0xFF1597E5), hu(0xFF5B57F5, 0xFF9A6BFF), hu(0xFFF0285A, 0xFFFF6F96), hu(0xFFF5A300, 0xFFFF7A00),
    hu(0xFFF03E64, 0xFFFF7A90), hu(0xFFE8A200, 0xFFFFB31A), hu(0xFF12B886, 0xFF3DDC97), hu(0xFF1597E5, 0xFF2563EB), hu(0xFF6D3AED, 0xFFE8306E))
val LocalD = staticCompositionLocalOf { Design(GRAPHITE, VIF_DARK) }

/** « base=porcelaine glow=0.3 flat=1 r=20 card=outlined » → Design. */
fun parseDesign(spec: String): Design {
    val kv = spec.split(' ', ',').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    val base = BASES.firstOrNull { it.name == kv["base"] } ?: GRAPHITE
    return Design(base, if (base.dark) VIF_DARK else VIF_LIGHT, kv["glow"]?.toFloat() ?: 0f, kv["flat"] == "1", (kv["r"]?.toInt() ?: 26).dp,
        CardS.entries.firstOrNull { it.name.equals(kv["card"], true) } ?: CardS.FILLED)
}

// ---- primitives ------------------------------------------------------------------------------------------------------------

@Composable
fun Tt(text: String, size: TextUnit, color: Color? = null, weight: FontWeight = FontWeight.Normal, display: Boolean = false,
      modifier: Modifier = Modifier, brush: Brush? = null, lines: Int = Int.MAX_VALUE, align: TextAlign = TextAlign.Unspecified, spacing: TextUnit = 0.sp) {
    val d = LocalD.current
    val fam = if (display) SansDisplay else SansText
    Text(text, modifier, maxLines = lines, overflow = TextOverflow.Ellipsis, style =
        if (brush != null && !d.flat) TextStyle(brush = brush, fontFamily = fam, fontSize = size, fontWeight = weight, textAlign = align, letterSpacing = spacing, fontFeatureSettings = "tnum")
        else TextStyle(color = color ?: d.base.ink, fontFamily = fam, fontSize = size, fontWeight = weight, textAlign = align, letterSpacing = spacing, fontFeatureSettings = "tnum"))
}

@Composable
fun Tile(modifier: Modifier = Modifier, pad: Dp = 16.dp, bg: Brush? = null, content: @Composable ColumnScope.() -> Unit) {
    val d = LocalD.current; val shape = RoundedCornerShape(d.radius)
    val m = when (d.card) {
        CardS.FILLED -> modifier.clip(shape).background(bg ?: SolidColor(d.base.s1)).border(1.dp, d.base.line, shape)
        CardS.OUTLINED -> modifier.clip(shape).background(bg ?: SolidColor(d.base.bg)).border(1.dp, if (d.dark) Color(0x24FFFFFF) else Color(0x22000000), shape)
        CardS.ELEVATED -> modifier.shadow(if (d.dark) 0.dp else 8.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000)).clip(shape).background(bg ?: SolidColor(d.base.s1))
    }
    Column(m.padding(pad), content = content)
}

@Composable
fun Lab(icon: ImageVector?, text: String, hue: Color? = null, modifier: Modifier = Modifier) {
    val d = LocalD.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = hue ?: d.base.sub); Spacer(Modifier.width(6.dp)) }
        Tt(text, 13.sp, d.base.sub, FontWeight.Medium, lines = 1)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, size: TextUnit = 12.sp) =
    Box(modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) { Tt(text, size, fg, FontWeight.SemiBold, lines = 1) }

// ---- dessin ----------------------------------------------------------------------------------------------------------------

/** Anneau : dégradé ou aplat selon le design, halo selon [Design.glow]. */
fun DrawScope.dRing(d: Design, c: Offset, r: Float, w: Float, f: Float, h: Hue, from: Float = -90f, span: Float = 360f, track: Color? = null) {
    drawArc(track ?: d.track(h), from, span, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
    if (f <= 0f) return
    val sweep = span * f
    if (d.glow > 0f) glow(arcPath(c, r, from, sweep), h.b.copy(alpha = 0.6f * d.glow), w * 0.8f, w)
    if (d.flat) drawArc(h.a, from, sweep, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
    else rotate(from, c) {
        val e = sweep / 360f
        drawArc(Brush.sweepGradient(0f to h.a, e to h.b, minOf(e + 0.05f, 0.98f) to h.b, 1f to h.a, center = c), 0f, sweep, false,
            Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Halo d'un point clé (rien si le design n'en veut pas). */
fun DrawScope.dGlowDot(d: Design, c: Offset, r: Float, color: Color) { if (d.glow > 0f) glow(circlePath(c, r), color.copy(alpha = 0.7f * d.glow), r) }
