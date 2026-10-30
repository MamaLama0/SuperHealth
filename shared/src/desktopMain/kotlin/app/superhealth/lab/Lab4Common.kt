package app.superhealth.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.ui.theme.SansDisplay
import app.superhealth.ui.theme.SansText

/** Texte du tour 4 (Google Sans Flex de l'app par défaut ; [brush] pour un texte en dégradé). */
@Composable
fun X(text: String, size: TextUnit, color: Color = Color.White, weight: FontWeight = FontWeight.Normal, display: Boolean = false,
      modifier: Modifier = Modifier, brush: Brush? = null, family: FontFamily? = null, spacing: TextUnit = 0.sp, lines: Int = Int.MAX_VALUE) =
    Text(text, modifier, maxLines = lines, overflow = TextOverflow.Ellipsis,
        style = if (brush != null) TextStyle(brush = brush, fontFamily = family ?: if (display) SansDisplay else SansText, fontSize = size,
            fontWeight = weight, letterSpacing = spacing, fontFeatureSettings = "tnum")
        else TextStyle(color = color, fontFamily = family ?: if (display) SansDisplay else SansText, fontSize = size, fontWeight = weight,
            letterSpacing = spacing, fontFeatureSettings = "tnum"))

@Composable
fun PixelIcon(path: String, size: Dp) { image(path)?.let { Image(it, null, Modifier.size(size), filterQuality = FilterQuality.None) } }

internal val meals4 = listOf(
    Triple("Petit-déjeuner", "8:10", listOf(Food("Œufs brouillés", "2 œufs", 182, "foods/pack/070.png"), Food("Pain complet", "60 g", 148, "foods/pack/083.png"),
        Food("Banane", "1 moyenne", 105, "foods/pack/017.png"))),
    Triple("Déjeuner", "12:40", listOf(Food("Poulet rôti", "150 g", 285, "foods/pack/076.png"), Food("Riz basmati", "180 g", 230, "foods/pack/139.png"),
        Food("Brocolis", "120 g", 41, "foods/pack/058.png"), Food("Avocat", "½", 120, "foods/pack/036.png"))),
    Triple("Collation", "16:30", listOf(Food("Yaourt grec", "150 g", 146, "foods/gen/i103.png"), Food("Pomme", "1 moyenne", 95, "foods/pack/022.png"))),
)

internal val program4 = listOf(
    Day("Push", "Pecs · Épaules", "front", setOf("chest", "frontDelts", "sideDelts", "triceps"), 0.1f, 0.5f, done = true, next = false),
    Day("Pull", "Dos · Biceps", "back", setOf("lats", "upperBack", "rearDelts", "upperTraps"), 0.1f, 0.5f, done = true, next = false),
    Day("Jambes", "Cuisses · Mollets", "front", setOf("quads", "adductors", "abductors", "calves"), 0.5f, 1f, done = false, next = true),
    Day("Full body", "Tout le corps", "front", setOf("chest", "abs", "quads", "biceps", "frontDelts", "obliques"), 0f, 1f, done = false, next = false),
)

/** Nuit fictive : (phase, minutes) — 1 profond, 2 léger, 3 paradoxal, 4 éveil. */
internal val night4 = listOf(2 to 18, 1 to 42, 2 to 30, 3 to 16, 2 to 34, 1 to 28, 2 to 22, 3 to 24, 4 to 5, 2 to 38, 3 to 30, 2 to 26, 4 to 6, 3 to 34, 2 to 20)
/** Pas par heure (0 h → 23 h), journée jusqu'à 16 h. */
internal val steps4 = listOf(0, 0, 0, 0, 0, 0, 0, 380, 920, 410, 260, 300, 780, 640, 330, 290, 1260, 0, 0, 0, 0, 0, 0, 0)
internal val heart4 = listOf(58, 57, 57, 56, 55, 56, 55, 54, 54, 55, 54, 53, 54, 54)
internal val weight4 = listOf(82.6, 82.4, 82.5, 82.1, 81.9, 82.0, 81.7, 81.6, 81.5, 81.4, 81.3, 81.2)

/** Barre d'onglets flottante (idée de Pixel retenue) + bouton d'action à droite, sous le pouce. */
@Composable
fun FloatingBar(tab: Int, bar: Color, on: Color, onInk: Color, off: Color, fab: Color, fabInk: Color, modifier: Modifier,
                fabShape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp), labelFamily: FontFamily? = null, caps: Boolean = false) {
    Row(modifier.padding(start = 16.dp, end = 16.dp, bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.shadow(16.dp, CircleShape, ambientColor = Color(0x55000000), spotColor = Color(0x55000000)).clip(CircleShape).background(bar).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(Icons.Rounded.Today to "Aujourd'hui", Icons.Rounded.Restaurant to "Nutrition", Icons.AutoMirrored.Rounded.DirectionsRun to "Activités").forEachIndexed { i, (ic, l) ->
                val sel = i == tab
                Row(Modifier.clip(CircleShape).background(if (sel) on else Color.Transparent).padding(horizontal = if (sel) 16.dp else 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(ic, null, Modifier.size(23.dp), tint = if (sel) onInk else off)
                    if (sel) { Spacer(Modifier.width(8.dp)); X(if (caps) l.uppercase() else l, if (caps) 12.sp else 15.sp, onInk, FontWeight.SemiBold, family = labelFamily, spacing = if (caps) 1.sp else 0.sp) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(64.dp).shadow(16.dp, fabShape, ambientColor = fab, spotColor = fab).clip(fabShape).background(fab), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, Modifier.size(32.dp), tint = fabInk)
        }
    }
}

/** Muscles d'un corps (tracés de l'app) avec leur cadre. */
internal class BodyShapes(val side: String) {
    val art = body(side)
    fun contains(x: Float, y: Float, ids: (String) -> Boolean): Boolean =
        art.parts.any { (id, p) -> ids(id) && p.asSkiaPath().contains(x, y) }
}

internal fun Color.mix(o: Color, t: Float) = Color(red + (o.red - red) * t, green + (o.green - green) * t, blue + (o.blue - blue) * t, alpha + (o.alpha - alpha) * t)

// ---- dessin : halo, anneaux, courbes -------------------------------------------------------------------------------------

/** Halo flou (MaskFilter Skia) d'un tracé, plein ou en trait. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.glow(path: Path, color: Color, blur: Float, stroke: Float = 0f) =
    drawIntoCanvas { c ->
        val p = org.jetbrains.skia.Paint().apply {
            this.color = color.toArgb(); isAntiAlias = true
            mode = if (stroke > 0f) org.jetbrains.skia.PaintMode.STROKE else org.jetbrains.skia.PaintMode.FILL
            strokeWidth = stroke; strokeCap = org.jetbrains.skia.PaintStrokeCap.ROUND
            maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(org.jetbrains.skia.FilterBlurMode.NORMAL, blur)
        }
        c.nativeCanvas.drawPath(path.asSkiaPath(), p)
    }

internal fun arcPath(c: Offset, r: Float, start: Float, sweep: Float) =
    Path().apply { arcTo(androidx.compose.ui.geometry.Rect(c, r), start, sweep, true) }

internal fun circlePath(c: Offset, r: Float) = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }

/** Anneau en dégradé qui suit l'arc, avec halo. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.ring(c: Offset, r: Float, w: Float, f: Float, a: Color, b: Color, track: Color, halo: Float = 0.55f) {
    drawCircle(track, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    val sweep = 360f * f
    if (halo > 0f) glow(arcPath(c, r, -90f, sweep), b.copy(alpha = halo), w * 0.8f, w)
    rotate(-90f, c) {
        drawArc(Brush.sweepGradient(0f to a, f to b, minOf(f + 0.06f, 0.97f) to b, 1f to a, center = c), 0f, sweep, false,
            Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

/** Courbe lisse (Catmull-Rom) passant par les points. */
internal fun smooth(pts: List<Offset>, into: Path = Path(), move: Boolean = true): Path = into.apply {
    if (move) moveTo(pts[0].x, pts[0].y) else lineTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = pts[maxOf(i - 1, 0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[minOf(i + 2, pts.size - 1)]
        cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
    }
}

/** Corps de l'app recadré, dessiné par [paint] dans le repère du tracé (échelle [s] fournie pour les épaisseurs). */
@Composable
internal fun BodyCanvas(side: String, from: Float, to: Float, modifier: Modifier,
                        paint: androidx.compose.ui.graphics.drawscope.DrawScope.(art: BodyArt, s: Float, y0: Float, hh: Float) -> Unit) {
    val art = androidx.compose.runtime.remember(side) { body(side) }
    Canvas(modifier) {
        val y0 = art.h * from; val hh = art.h * (to - from)
        val s = minOf(size.width / art.w, size.height / hh)
        val dx = (size.width - art.w * s) / 2; val dy = (size.height - hh * s) / 2
        clipRect { translate(dx, dy - y0 * s) { scale(s, s, pivot = Offset.Zero) { paint(art, s, y0, hh) } } }
    }
}
