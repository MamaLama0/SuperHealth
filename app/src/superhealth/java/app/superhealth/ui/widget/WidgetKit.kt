package app.superhealth.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.superhealth.MainActivity
import app.superhealth.ui.dashboard.Rings
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Socle commun des widgets SuperHealth : couleurs claires / sombres reprises de l'app, ouverture d'un écran, en-tête à
 * pastille, et dessins en image (anneaux, courbes, barres) puisque Glance ne trace ni arcs ni courbes.
 */
internal object WK {
    val bg = ColorProvider(Color(0xFFFFFFFF), Color(0xFF1C1C1E))
    val chip = ColorProvider(Color(0xFFF1F1F3), Color(0xFF2C2C2E))
    val text = ColorProvider(Color(0xFF111111), Color(0xFFF2F2F2))
    val sub = ColorProvider(Color(0xFF6E6E73), Color(0xFFA1A1A6))
    val track = ColorProvider(Color(0xFFE6E6EA), Color(0xFF3A3A3C))
    val strong = ColorProvider(Color(0xFF111111), Color(0xFFF2F2F2))
    val onStrong = ColorProvider(Color(0xFFFFFFFF), Color(0xFF111111))
    fun c(color: Color) = ColorProvider(color, color)
    /** Fond pastel d'une couleur (comme les pastilles de l'app), clair et sombre. */
    fun tint(color: Color) = ColorProvider(
        androidx.compose.ui.graphics.lerp(Color.White, color, 0.17f), androidx.compose.ui.graphics.lerp(Color(0xFF1C1C1E), color, 0.27f))

    const val RADIUS = 24

    /** Paliers de taille communs (en cases du lanceur) : 1×1, 2×1, 4×1, 2×2, 4×2, 4×4. */
    val ONE = androidx.compose.ui.unit.DpSize(57.dp, 57.dp)
    val TWO_BY_ONE = androidx.compose.ui.unit.DpSize(130.dp, 57.dp)
    val FOUR_BY_ONE = androidx.compose.ui.unit.DpSize(270.dp, 57.dp)
    val TWO_BY_TWO = androidx.compose.ui.unit.DpSize(130.dp, 130.dp)
    val FOUR_BY_TWO = androidx.compose.ui.unit.DpSize(270.dp, 130.dp)
    val FOUR_BY_FOUR = androidx.compose.ui.unit.DpSize(270.dp, 270.dp)
    val ALL = setOf(ONE, TWO_BY_ONE, FOUR_BY_ONE, TWO_BY_TWO, FOUR_BY_TWO, FOUR_BY_FOUR)

    /** Forme du widget, d'après sa taille courante. */
    enum class Shape { TINY, STRIP, WIDE_STRIP, SQUARE, WIDE, BIG }

    fun shape(size: androidx.compose.ui.unit.DpSize): Shape = when {
        size.height < 100.dp -> when { size.width >= 250.dp -> Shape.WIDE_STRIP; size.width >= 120.dp -> Shape.STRIP; else -> Shape.TINY }
        size.width < 100.dp -> Shape.TINY
        size.width < 250.dp -> Shape.SQUARE
        size.height >= 240.dp -> Shape.BIG
        else -> Shape.WIDE
    }

    /** Ouvre l'app sur [route] (filtrée par Routes.external). */
    fun open(ctx: Context, route: String): Action =
        actionStartActivity(Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse("superhealth://open/$route"))
            .putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))

    /** Icône de chaque anneau, la même que dans l'app. */
    fun icon(k: Rings.Kind): Int = when (k) {
        Rings.Kind.STEPS -> R.drawable.ic_w_walk
        Rings.Kind.ENERGY -> R.drawable.ic_w_bolt
        Rings.Kind.KCAL -> R.drawable.ic_w_food
        Rings.Kind.PROTEIN -> R.drawable.ic_w_egg
        Rings.Kind.WATER -> R.drawable.ic_w_drop
        Rings.Kind.SLEEP -> R.drawable.ic_w_moon
        Rings.Kind.CARDIO -> R.drawable.ic_w_heart
    }

    /**
     * Anneaux emboîtés en image carrée de [px] pixels, même géométrie que la carte du tableau de bord ; avec [ctx],
     * le symbole de chaque mesure est posé au départ de son anneau.
     */
    fun rings(rings: List<Rings.Ring>, px: Int, ctx: Context? = null): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val n = rings.size.coerceAtLeast(1)
        val stroke = px * 0.30f / n
        val gap = stroke * 0.12f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        rings.forEachIndexed { i, r ->
            val inset = stroke / 2 + i * (stroke + gap)
            val box = RectF(inset, inset, px - inset, px - inset)
            val col = r.kind.color
            c.save(); c.rotate(-90f, px / 2f, px / 2f)
            paint.shader = null; paint.color = col.copy(alpha = 0.22f).toArgb()
            c.drawArc(box, 0f, 360f, false, paint)
            val p = r.progress
            if (p > 0.001f) {
                paint.shader = SweepGradient(px / 2f, px / 2f, intArrayOf(Rings.shade(col).toArgb(), col.toArgb(), col.toArgb()), floatArrayOf(0f, 0.5f, 1f))
                c.drawArc(box, 0f, p.coerceAtMost(1f) * 360f, false, paint)
                if (p > 1f) { paint.shader = null; paint.color = col.toArgb(); c.drawArc(box, 0f, (p - 1f) * 360f, false, paint) }
            }
            c.restore()
            if (ctx != null) ContextCompat.getDrawable(ctx, icon(r.kind))?.mutate()?.let { d ->
                val s = stroke * 0.8f
                val cy = inset
                d.setTint(android.graphics.Color.argb(217, 0, 0, 0))
                d.setBounds((px / 2f - s / 2).toInt(), (cy - s / 2).toInt(), (px / 2f + s / 2).toInt(), (cy + s / 2).toInt())
                d.draw(c)
            }
        }
        return bmp
    }

    /** Un anneau seul ([fraction] plafonnée à un tour), en image carrée. */
    fun ring(fraction: Float, color: Color, px: Int, strokeFrac: Float = 0.12f): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val stroke = px * strokeFrac
        val box = RectF(stroke / 2, stroke / 2, px - stroke / 2, px - stroke / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        paint.color = color.copy(alpha = 0.22f).toArgb()
        c.drawArc(box, 0f, 360f, false, paint)
        if (fraction > 0.001f) { paint.color = color.toArgb(); c.drawArc(box, -90f, 360f * fraction.coerceAtMost(1f), false, paint) }
        return bmp
    }

    /** Courbe et aire dessous ; null : jour sans valeur (la courbe passe par-dessus). */
    fun spark(values: List<Double?>, color: Color, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val pts = values.withIndex().mapNotNull { (i, v) -> v?.let { i to it } }
        if (pts.size < 2) return bmp
        val c = android.graphics.Canvas(bmp)
        val lo = pts.minOf { it.second }; val hi = pts.maxOf { it.second }
        val pad = h * 0.1f
        fun x(i: Int) = w * i / (values.size - 1).coerceAtLeast(1).toFloat()
        fun y(v: Double) = pad + (h - 2 * pad) * (1 - if (hi > lo) ((v - lo) / (hi - lo)).toFloat() else 0.5f)
        val line = android.graphics.Path(); pts.forEachIndexed { k, (i, v) -> if (k == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v)) }
        val area = android.graphics.Path(line).apply { lineTo(x(pts.last().first), h.toFloat()); lineTo(x(pts.first().first), h.toFloat()); close() }
        c.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), color.copy(alpha = 0.35f).toArgb(), color.copy(alpha = 0f).toArgb(),
                android.graphics.Shader.TileMode.CLAMP)
        })
        c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = h * 0.06f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color.toArgb()
        })
        c.drawCircle(x(pts.last().first), y(pts.last().second), h * 0.07f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() })
        return bmp
    }

    /** Barres arrondies par jour ; atteint [goal] : couleur pleine, sinon atténuée. */
    fun bars(values: List<Double?>, goal: Double?, color: Color, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        if (values.isEmpty()) return bmp
        val c = android.graphics.Canvas(bmp)
        val max = maxOf(values.filterNotNull().maxOrNull() ?: 0.0, goal ?: 0.0).takeIf { it > 0 } ?: return bmp
        val slot = w / values.size.toFloat(); val bw = slot * 0.56f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - bw) / 2
            paint.color = color.copy(alpha = 0.16f).toArgb()
            c.drawRoundRect(RectF(x, 0f, x + bw, h.toFloat()), bw / 2, bw / 2, paint)
            val bh = ((v ?: 0.0) / max * h).toFloat().coerceAtLeast(if (v != null && v > 0) bw else 0f)
            if (bh <= 0f) return@forEachIndexed
            paint.color = (if (goal != null && v!! >= goal) color else color.copy(alpha = 0.6f)).toArgb()
            c.drawRoundRect(RectF(x, h - bh, x + bw, h.toFloat()), bw / 2, bw / 2, paint)
        }
        return bmp
    }

    /** Pixels pour [dp] (les images sont rendues à la densité de l'écran). */
    fun px(ctx: Context, dp: Dp): Int = (dp.value * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}

/** En-tête de widget : pastille colorée et titre. */
@Composable
internal fun WidgetHeader(icon: Int, color: Color, title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(22.dp).cornerRadius(11.dp).background(WK.tint(color)), contentAlignment = Alignment.Center) {
            Image(ImageProvider(icon), null, GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(WK.c(color)))
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(title, style = TextStyle(color = WK.text, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1,
            modifier = GlanceModifier.defaultWeight())
        trailing?.let { Text(it, style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1) }
    }
}
