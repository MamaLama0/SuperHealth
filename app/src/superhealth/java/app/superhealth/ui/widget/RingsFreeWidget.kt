package app.superhealth.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import app.superhealth.ui.dashboard.Rings
import app.superhealth.ui.tr

/**
 * Anneaux sans cadre, posés sur le fond d'écran comme sur une Apple Watch. Tout est dessiné en une image à la taille exacte
 * du widget, nette à toutes les tailles ; la forme choisit la mise en page : carré → anneaux seuls, large → valeurs à
 * droite, haut → valeurs dessous.
 */
class RingsFreeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rings = runCatching { loadRings(context) }.getOrNull() ?: Rings.build(Rings.defaults, { null }, { 0.0 })
        provideContent { Content(context, rings) }
    }

    @Composable
    private fun Content(ctx: Context, rings: List<Rings.Ring>) {
        val size = LocalSize.current
        val d = ctx.resources.displayMetrics.density
        // plafond de taille : les images d'un widget passent par une mémoire partagée bornée
        val scale = minOf(d, MAX_PX / maxOf(size.width.value, size.height.value, 1f))
        val bmp = RingsScene.draw(ctx, rings, (size.width.value * scale).toInt(), (size.height.value * scale).toInt(), scale)
        Box(GlanceModifier.fillMaxSize().clickable(WK.open(ctx, "home")), contentAlignment = Alignment.Center) {
            Image(ImageProvider(bmp), tr("Anneaux du jour"), GlanceModifier.size(size.width, size.height))
        }
    }

    private companion object { const val MAX_PX = 1000f }
}

/** Mise en page de l'image du widget sans cadre, selon le rapport largeur / hauteur. */
internal object RingsScene {
    fun draw(ctx: Context, rings: List<Rings.Ring>, w: Int, h: Int, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = 4 * density
        val ratio = w / h.toFloat()
        val minRow = 16 * density
        when {
            // large : anneaux à gauche, une ligne par mesure à droite
            ratio > 1.4f && w - h > 60 * density -> {
                val side = h - 2 * pad
                c.drawBitmap(WK.rings(rings, side.toInt(), ctx), pad, pad, null)
                values(ctx, c, rings, pad * 3 + side, pad, w - side - pad * 4, side)
            }
            // haut : anneaux en haut, valeurs dessous s'il reste la place d'une ligne lisible par mesure
            ratio < 0.75f && h - w > rings.size * minRow -> {
                val side = w - 2 * pad
                c.drawBitmap(WK.rings(rings, side.toInt(), ctx), pad, pad, null)
                values(ctx, c, rings, pad, side + pad * 2, w - 2 * pad, h - side - pad * 3)
            }
            else -> {
                val side = minOf(w, h) - 2 * pad
                c.drawBitmap(WK.rings(rings, side.toInt(), ctx), (w - side) / 2, (h - side) / 2, null)
            }
        }
        return bmp
    }

    /** Symbole, valeur dans la couleur de l'anneau et objectif, ombrés pour rester lisibles sur tout fond d'écran. */
    private fun values(ctx: Context, c: Canvas, rings: List<Rings.Ring>, x: Float, y: Float, w: Float, h: Float) {
        val rowH = minOf(h / rings.size, h * 0.34f)
        val top = y + (h - rowH * rings.size) / 2
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(200, 255, 255, 255) }
        val texts = rings.map { r -> (r.value?.let { fmt(r.kind, it) } ?: "—") to (if (r.goal > 0) " / " + fmt(r.kind, r.goal) else "") }
        // une seule taille pour toutes les lignes : la plus grande qui tient dans la largeur et la hauteur de ligne
        var font = rowH * 0.62f
        fun width(f: Float) = texts.maxOf { (v, g) ->
            bold.textSize = f; light.textSize = f * 0.62f
            f * 1.25f + bold.measureText(v) + light.measureText(g)
        }
        if (width(font) > w) font *= w / width(font)
        bold.textSize = font; light.textSize = font * 0.62f
        val shadow = android.graphics.Color.argb(150, 0, 0, 0)
        bold.setShadowLayer(font * 0.12f, 0f, font * 0.05f, shadow); light.setShadowLayer(font * 0.12f, 0f, font * 0.05f, shadow)
        rings.forEachIndexed { i, r ->
            val cy = top + rowH * (i + 0.5f)
            val base = cy + font * 0.36f
            ContextCompat.getDrawable(ctx, WK.icon(r.kind))?.mutate()?.let { d ->
                val s = font * 0.95f
                d.setTint(r.kind.color.toArgb())
                d.setBounds(x.toInt(), (cy - s / 2).toInt(), (x + s).toInt(), (cy + s / 2).toInt())
                d.draw(c)
            }
            val (v, g) = texts[i]
            bold.color = r.kind.color.toArgb()
            val tx = x + font * 1.25f
            c.drawText(v, tx, base, bold)
            c.drawText(g, tx + bold.measureText(v), base, light)
        }
    }
}

class RingsFreeWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RingsFreeWidget() }
