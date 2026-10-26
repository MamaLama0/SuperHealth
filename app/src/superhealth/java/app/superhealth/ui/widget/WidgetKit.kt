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
import androidx.glance.action.clickable
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
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
    val text = ColorProvider(Color(0xFF000000), Color(0xFFFFFFFF))
    val sub = ColorProvider(Color(0xFF8A8A8E), Color(0xFF98989F))
    val track = ColorProvider(Color(0xFFE6E6EA), Color(0xFF3A3A3C))
    val strong = ColorProvider(Color(0xFF111111), Color(0xFFF2F2F2))
    val onStrong = ColorProvider(Color(0xFFFFFFFF), Color(0xFF111111))
    fun c(color: Color) = ColorProvider(color, color)
    /** Fond pastel d'une couleur (comme les pastilles de l'app), clair et sombre. */
    fun tint(color: Color) = ColorProvider(
        androidx.compose.ui.graphics.lerp(Color.White, color, 0.17f), androidx.compose.ui.graphics.lerp(Color(0xFF1C1C1E), color, 0.27f))

    const val RADIUS = 24

    /** Les quatre tailles, comme les cartes du tableau de bord : petit 1×1, moyen 2×2, bandeau 4×1, grand 4×2. */
    val ONE = androidx.compose.ui.unit.DpSize(57.dp, 57.dp)
    val MEDIUM = androidx.compose.ui.unit.DpSize(130.dp, 130.dp)
    val STRIP = androidx.compose.ui.unit.DpSize(270.dp, 57.dp)
    val LARGE = androidx.compose.ui.unit.DpSize(270.dp, 130.dp)
    val ALL = setOf(ONE, MEDIUM, STRIP, LARGE)

    /** Petit, moyen et bandeau : symbole et valeur, peu ou pas de texte ; grand : la version détaillée. */
    enum class Size { SMALL, MEDIUM, STRIP, LARGE }

    fun size(s: androidx.compose.ui.unit.DpSize): Size = when {
        s.height < 100.dp -> if (s.width >= 250.dp) Size.STRIP else Size.SMALL
        s.width < 120.dp -> Size.SMALL
        s.width < 250.dp -> Size.MEDIUM
        else -> Size.LARGE
    }

    /** Valeur dans la couleur de sa mesure, lisible sur le fond pastel (comme onContainer dans l'app). */
    fun on(color: Color) = ColorProvider(androidx.compose.ui.graphics.lerp(color, Color.Black, 0.18f), androidx.compose.ui.graphics.lerp(color, Color.White, 0.12f))
    /** Pastille ronde posée sur un fond pastel. */
    val chipOn = ColorProvider(Color(0xD9FFFFFF), Color(0xD91C1C1E))

    /** Variante de cartes du labo de style, en clair et en sombre (comme le tableau de bord). */
    private fun style(dark: Boolean) = app.superhealth.SuperPrefs.settings.value.let { if (dark) it.styleCardsDark else it.styleCardsLight }
    /** Part de la couleur dans le fond du widget ; « Plein » reste lisible en « Vif » (texte du widget non inversé). */
    private fun tintOf(dark: Boolean) = when (style(dark)) {
        "SOFT" -> if (dark) 0.20f else 0.13f
        "GRADIENT" -> if (dark) 0.32f else 0.24f
        "STRONG", "FULL" -> if (dark) 0.44f else 0.34f
        else -> 0f
    }
    private val night = Color(0xFF1C1C1E)

    /** Fond d'un widget de la famille [hue], selon la variante choisie. */
    fun cardBg(hue: Color?): androidx.glance.unit.ColorProvider = if (hue == null) bg else ColorProvider(
        androidx.compose.ui.graphics.lerp(Color.White, hue, tintOf(false)), androidx.compose.ui.graphics.lerp(night, hue, tintOf(true)))

    /** Pastille : pleine pour « Pastille pleine », blanche translucide sur fond teinté, pastel sinon. */
    fun chipBg(color: Color): androidx.glance.unit.ColorProvider {
        fun one(dark: Boolean): Color = when {
            style(dark) == "VIVID" -> color
            tintOf(dark) > 0f -> if (dark) Color(0xD91C1C1E) else Color(0xD9FFFFFF)
            else -> androidx.compose.ui.graphics.lerp(if (dark) night else Color.White, color, if (dark) 0.27f else 0.17f)
        }
        return ColorProvider(one(false), one(true))
    }
    fun chipInk(color: Color): androidx.glance.unit.ColorProvider = ColorProvider(
        if (style(false) == "VIVID") Color.White else androidx.compose.ui.graphics.lerp(color, Color.Black, 0.18f),
        if (style(true) == "VIVID") Color.White else androidx.compose.ui.graphics.lerp(color, Color.White, 0.12f))

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
     * le symbole de chaque mesure est posé au départ de son anneau. [free] : posés sans cadre sur le fond d'écran, ils
     * gagnent en contraste (piste sombre et opaque de la couleur, liseré sombre sous chaque anneau, dégradé sans ombre)
     * pour rester lisibles sur un fond clair comme sur un fond chargé.
     */
    fun rings(rings: List<Rings.Ring>, px: Int, ctx: Context? = null, free: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val n = rings.size.coerceAtLeast(1)
        // sans cadre : le liseré sombre déborde de l'anneau extérieur, sa marge est réservée pour qu'il ne soit pas coupé
        val margin = if (free) px * 0.30f / n * 0.12f * 0.8f + 1f else 0f
        val stroke = (px - 2 * margin) * 0.30f / n
        val gap = stroke * 0.12f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke + gap * 1.6f; strokeCap = Paint.Cap.ROUND
            color = android.graphics.Color.argb(150, 0, 0, 0) }
        rings.forEachIndexed { i, r ->
            val inset = margin + stroke / 2 + i * (stroke + gap)
            val box = RectF(inset, inset, px - inset, px - inset)
            val col = if (free) vivid(r.kind.color) else r.kind.color
            c.save(); c.rotate(-90f, px / 2f, px / 2f)
            paint.shader = null
            if (free) {
                // piste : la couleur assombrie, presque opaque, cerclée de noir pour se détacher du fond d'écran
                c.drawArc(box, 0f, 360f, false, halo)
                paint.color = Rings.shade(col, 0.32f).copy(alpha = 0.92f).toArgb()
            } else paint.color = col.copy(alpha = 0.22f).toArgb()
            c.drawArc(box, 0f, 360f, false, paint)
            val p = r.progress
            if (p > 0.001f) {
                val start = if (free) androidx.compose.ui.graphics.lerp(col, Color.White, 0.18f) else Rings.shade(col)
                paint.shader = SweepGradient(px / 2f, px / 2f, intArrayOf(start.toArgb(), col.toArgb(), col.toArgb()), floatArrayOf(0f, 0.5f, 1f))
                c.drawArc(box, 0f, p.coerceAtMost(1f) * 360f, false, paint)
                if (p > 1f) { paint.shader = null; paint.color = col.toArgb(); c.drawArc(box, 0f, (p - 1f) * 360f, false, paint) }
            }
            c.restore()
            if (ctx != null) ContextCompat.getDrawable(ctx, icon(r.kind))?.mutate()?.let { d ->
                val s = stroke * 0.8f
                val cy = inset
                d.setTint(android.graphics.Color.argb(if (free) 235 else 217, 0, 0, 0))
                d.setBounds((px / 2f - s / 2).toInt(), (cy - s / 2).toInt(), (px / 2f + s / 2).toInt(), (cy + s / 2).toInt())
                d.draw(c)
            }
        }
        return bmp
    }

    /** Couleur d'anneau poussée vers la saturation et la luminosité maximales (sur fond d'écran). */
    fun vivid(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[1] = (hsv[1] * 1.12f).coerceAtMost(1f); hsv[2] = 1f
        return Color(android.graphics.Color.HSVToColor(hsv))
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
        // même tracé lisse et fidèle que les graphiques de l'app (charte commune)
        val line = android.graphics.Path()
        app.superhealth.ui.charts.monotone(FloatArray(pts.size) { x(pts[it].first) }, FloatArray(pts.size) { y(pts[it].second) },
            { a, b -> line.moveTo(a, b) }, { a, b -> line.lineTo(a, b) }, { a, b, c2, d, e, f -> line.cubicTo(a, b, c2, d, e, f) })
        val area = android.graphics.Path(line).apply { lineTo(x(pts.last().first), h.toFloat()); lineTo(x(pts.first().first), h.toFloat()); close() }
        c.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), color.copy(alpha = app.superhealth.ui.charts.ChartStyle.AREA_ALPHA).toArgb(), color.copy(alpha = 0f).toArgb(),
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
            paint.color = (if (goal != null && v!! >= goal) color else color.copy(alpha = app.superhealth.ui.charts.ChartStyle.BELOW_GOAL_ALPHA)).toArgb()
            c.drawRoundRect(RectF(x, h - bh, x + bw, h.toFloat()), bw / 2, bw / 2, paint)
        }
        return bmp
    }

    /** Pixels pour [dp] (les images sont rendues à la densité de l'écran). */
    fun px(ctx: Context, dp: Dp): Int = (dp.value * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}

/**
 * Fond d'un widget, comme les cartes du tableau de bord : carte blanche (sombre la nuit), la couleur de la famille
 * ([hue]) ne teinte que le symbole et la valeur. Toucher : [action].
 */
@Composable
internal fun WidgetCard(action: Action, hue: Color?, padding: Dp = 12.dp, horizontal: Alignment.Horizontal = Alignment.Start,
                        content: @Composable androidx.glance.layout.ColumnScope.() -> Unit) {
    androidx.glance.layout.Column(GlanceModifier.fillMaxSize().background(WK.cardBg(hue)).cornerRadius(WK.RADIUS.dp)
        .padding(padding).clickable(action), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = horizontal, content = content)
}

/** Pastille ronde teintée, symbole dans la couleur, comme dans l'app ([onTint] gardé pour les appels existants). */
@Composable
internal fun WidgetChip(icon: Int, color: Color, size: Dp, onTint: Boolean = true) {
    Box(GlanceModifier.size(size).cornerRadius(size / 2).background(WK.chipBg(color)), contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), null, GlanceModifier.size(size * 0.56f), colorFilter = ColorFilter.tint(WK.chipInk(color)))
    }
}

/** Grande valeur et son unité, dans la couleur de la mesure. */
@Composable
internal fun WidgetValue(value: String, unit: String?, color: Color, size: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = TextStyle(color = WK.on(color), fontSize = size.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        unit?.let { Text(" $it", style = TextStyle(color = WK.sub, fontSize = (size * 0.5f).coerceAtLeast(10f).sp), maxLines = 1) }
    }
}

/** En-tête de widget : pastille colorée et titre. */
@Composable
internal fun WidgetHeader(icon: Int, color: Color, title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // comme le titre des cartes : symbole et titre dans la couleur de la mesure
        Image(ImageProvider(icon), null, GlanceModifier.size(15.dp), colorFilter = ColorFilter.tint(WK.on(color)))
        Spacer(GlanceModifier.width(5.dp))
        Text(title, style = TextStyle(color = WK.on(color), fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1,
            modifier = GlanceModifier.defaultWeight())
        trailing?.let { Text(it, style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1) }
    }
}
