package app.superhealth.ui.widget

import android.content.Context
import android.graphics.*
import app.superhealth.cards.*
import app.superhealth.ui.tr

/**
 * Dessin de la carte Athlète pour les widgets : Glance ne sait pas afficher le shader de l'app, on peint donc une
 * version fixe de la carte (dégradé du biome, cadre de la rareté, photo détourée, note, stats et bonus) dans une image.
 */
internal object CardArt {
    private fun c(v: Long) = v.toInt()

    fun render(ctx: Context, st: CardStore.State, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val bg = st.background?.let(Pull::of)
        val card = bg?.card
        val e = card?.element
        val pal = card?.palette
        val radius = w * 0.075f
        val outer = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val clip = Path().apply { addRoundRect(outer, radius, radius, Path.Direction.CW) }
        cv.save(); cv.clipPath(clip)

        // Scène : dégradé vertical du biome, halo, puis trois reliefs en couches.
        val top = pal?.b?.let(::c) ?: 0xFF3C4BFF.toInt(); val bottom = pal?.c?.let(::c) ?: 0xFF07080D.toInt(); val light = pal?.a?.let(::c) ?: 0xFF9AA8FF.toInt()
        cv.drawRect(outer, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(top, blend(top, bottom, 0.55f), bottom), null, Shader.TileMode.CLAMP) })
        cv.drawCircle(w * 0.5f, h * 0.36f, w * 0.62f, Paint().apply { shader = RadialGradient(w * 0.5f, h * 0.36f, w * 0.62f, intArrayOf(withAlpha(light, 170), Color.TRANSPARENT), null, Shader.TileMode.CLAMP) })
        val seed = card?.seed ?: 0.37f
        for (layer in 0..2) {
            val y0 = h * (0.5f + layer * 0.09f)
            val path = Path().apply {
                moveTo(0f, h.toFloat()); lineTo(0f, y0)
                var x = 0f; var k = 0
                while (x < w) {
                    k++; x += w / 7f
                    val peak = y0 - h * (0.05f + 0.09f * frac(seed * 13.7f * (layer + 1) + k * 0.618f)) * if (e == Element.GLACE || e == Element.TERRE) 1.4f else 0.8f
                    lineTo(x - w / 14f, peak); lineTo(x, y0 - h * 0.02f * frac(k * 0.37f + layer))
                }
                lineTo(w.toFloat(), h.toFloat()); close()
            }
            cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blend(blend(top, bottom, 0.45f + layer * 0.2f), 0xFF000000.toInt(), layer * 0.12f) })
        }
        // Rayons de lumière en diagonale (reflet holographique pour les finitions).
        val shiny = bg?.finish?.let { it != Finish.NORMALE } == true
        cv.save(); cv.rotate(-18f, w / 2f, h / 2f)
        for (i in 0..(if (shiny) 5 else 2)) {
            val x = w * (-0.2f + i * 0.28f)
            cv.drawRect(x, -h * 0.2f, x + w * 0.08f, h * 1.2f, Paint().apply {
                shader = LinearGradient(x, 0f, x + w * 0.08f, 0f, intArrayOf(Color.TRANSPARENT, withAlpha(Color.WHITE, if (shiny) 46 else 22), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            })
        }
        cv.restore()

        // Photo détourée, sinon une haltère.
        val cut = CardStore.cutoutFile(ctx).takeIf { it.exists() && st.photo != PhotoSource.AUCUNE }?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
        if (cut != null) {
            val box = RectF(w * 0.18f, h * 0.12f, w * 0.98f, h * 0.66f)
            val s = minOf(box.width() / cut.width, box.height() / cut.height)
            val dw = cut.width * s; val dh = cut.height * s
            cv.drawBitmap(cut, null, RectF(box.right - dw, box.bottom - dh, box.right, box.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(Color.WHITE, 60) }
            val cy = h * 0.42f; val cx = w * 0.6f
            cv.drawRoundRect(cx - w * 0.22f, cy - w * 0.018f, cx + w * 0.22f, cy + w * 0.018f, 8f, 8f, p)
            for (d in listOf(-1, 1)) for (k in 0..1) cv.drawRoundRect(cx + d * w * (0.15f + k * 0.045f) - w * 0.02f, cy - w * (0.1f - k * 0.025f), cx + d * w * (0.15f + k * 0.045f) + w * 0.02f, cy + w * (0.1f - k * 0.025f), 10f, 10f, p)
        }

        // Voile bas pour lire les stats.
        cv.drawRect(0f, h * 0.55f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, h * 0.55f, 0f, h * 0.78f, Color.TRANSPARENT, withAlpha(0xFF05060A.toInt(), 235), Shader.TileMode.CLAMP) })

        val stats = CardStore.snapshot.value?.now ?: st.lastStats
        val ovr = stats?.ovr ?: st.lastOvr10.takeIf { it > 0 }?.let { (it + 5) / 10 }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); setShadowLayer(w * 0.012f, 0f, w * 0.006f, withAlpha(Color.BLACK, 160)) }
        // Note et palier en haut à gauche.
        text.textSize = w * 0.2f
        cv.drawText(ovr?.toString() ?: "—", w * 0.07f, h * 0.2f, text)
        text.textSize = w * 0.055f; text.letterSpacing = 0.12f
        val tier = Tier.of(ovr)
        cv.drawText(tr(tier.label).uppercase(), w * 0.075f, h * 0.255f, text.apply { color = tierColor(tier) })
        text.color = Color.WHITE
        e?.let { el ->
            text.textSize = w * 0.05f
            cv.drawText(tr(el.magic).uppercase(), w * 0.075f, h * 0.31f, text.apply { color = c(el.mid) })
            text.color = Color.WHITE
        }

        // Nom.
        text.letterSpacing = 0.06f; text.textSize = w * 0.085f
        val name = "@" + (app.superhealth.ui.userName() ?: "moi").lowercase().replace(Regex("\\s+"), "")
        val nw = text.measureText(name)
        cv.drawText(name, (w - minOf(nw, w * 0.9f)) / 2f, h * 0.72f, text.apply { if (nw > w * 0.9f) textScaleX = w * 0.9f / nw })
        text.textScaleX = 1f

        // Stats : deux rangées de trois ; celles que le fond renforce prennent la couleur du biome.
        val boosted = card?.elements?.map { it.stat }.orEmpty()
        val order = listOf(Stat.PEC, Stat.DOS, Stat.BRA, Stat.JAM, Stat.REG, Stat.CAR)
        order.forEachIndexed { i, s ->
            val col = i % 3; val row = i / 3
            val cx = w * (0.2f + col * 0.3f); val y = h * (0.81f + row * 0.085f)
            val v = stats?.get(s)?.toString() ?: "–"
            text.textSize = w * 0.075f; text.letterSpacing = 0f
            val hue = if (s in boosted) c(card!!.elements.first { it.stat == s }.mid) else Color.WHITE
            text.color = hue
            val vw = text.measureText(v)
            cv.drawText(v, cx - vw - w * 0.012f, y, text)
            text.textSize = w * 0.048f; text.color = withAlpha(Color.WHITE, 190)
            cv.drawText(s.code, cx + w * 0.012f, y, text)
        }
        text.color = Color.WHITE
        // Ligne du fond : nom et bonus.
        if (bg != null) {
            text.textSize = w * 0.038f; text.color = withAlpha(Color.WHITE, 170)
            val line = bg.card.name + " · " + tr("Niv.") + " ${st.level(bg.card)} · " + Bonus.label(bg, st.level(bg.card))
            val lw = text.measureText(line)
            cv.drawText(line, (w - minOf(lw, w * 0.9f)) / 2f, h * 0.965f, text.apply { if (lw > w * 0.9f) textScaleX = w * 0.9f / lw })
            text.textScaleX = 1f
        }
        cv.restore()

        // Cadre : couleur de la rareté, arc-en-ciel pour une chromatique.
        val stroke = w * 0.022f
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
        val rc = card?.rarity?.color?.let(::c) ?: 0xFF9AA8FF.toInt()
        frame.shader = if (bg?.finish == Finish.CHROMATIQUE) SweepGradient(w / 2f, h / 2f, intArrayOf(0xFFFF4F7B.toInt(), 0xFFFFD36B.toInt(), 0xFF6FD3A0.toInt(), 0xFF4FA8FF.toInt(), 0xFFB46CFF.toInt(), 0xFFFF4F7B.toInt()), null)
            else LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(blend(rc, Color.WHITE, 0.5f), rc, blend(rc, Color.BLACK, 0.3f), blend(rc, Color.WHITE, 0.4f)), null, Shader.TileMode.CLAMP)
        cv.drawRoundRect(RectF(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2), radius, radius, frame)
        frame.shader = null; frame.color = withAlpha(Color.WHITE, 60); frame.strokeWidth = stroke * 0.3f
        val inset = stroke * 1.6f
        cv.drawRoundRect(RectF(inset, inset, w - inset, h - inset), radius * 0.8f, radius * 0.8f, frame)
        return bmp
    }

    /** Fond des widgets de quêtes : nuit chaude, lueur dorée en coin. */
    fun questBackdrop(square: Boolean = false): Bitmap {
        val w = if (square) 240 else 480; val h = 240
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFF231A05.toInt(), 0xFF141724.toInt(), 0xFF0B0C14.toInt()), null, Shader.TileMode.CLAMP) })
        cv.drawCircle(w * 0.95f, 0f, w * 0.55f, Paint().apply { shader = RadialGradient(w * 0.95f, 0f, w * 0.55f, intArrayOf(0x66FFC94A, Color.TRANSPARENT), null, Shader.TileMode.CLAMP) })
        cv.drawCircle(0f, h.toFloat(), w * 0.4f, Paint().apply { shader = RadialGradient(0f, h.toFloat(), w * 0.4f, intArrayOf(0x337C5CFF, Color.TRANSPARENT), null, Shader.TileMode.CLAMP) })
        return bmp
    }

    private fun tierColor(t: Tier) = when (t) {
        Tier.BRONZE -> 0xFFE0A070.toInt(); Tier.ARGENT -> 0xFFD8DEE8.toInt(); Tier.OR -> 0xFFFFD36B.toInt(); Tier.ELITE -> 0xFF8AE0FF.toInt(); Tier.ICONE -> 0xFFFF8AD8.toInt()
    }
    private fun frac(x: Float) = x - kotlin.math.floor(x)
    private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)
    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t).toInt() shl s
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }
}
