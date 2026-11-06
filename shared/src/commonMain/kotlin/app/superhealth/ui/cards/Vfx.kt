package app.superhealth.ui.cards

import app.superhealth.platform.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import app.superhealth.cards.Element
import kotlin.math.*

/**
 * Effets de magie procéduraux, dans l'esprit des animations de combat des JRPG : cercle runique au lancement,
 * projectile, impact propre à chaque élément, puis éclat. Tout est dessiné en mode additif sur fond sombre ;
 * les temps sont en secondes et chaque fonction est pure (même temps, même image).
 */
object Vfx {
    private val Add = BlendMode.Plus

    /** Graine stable d'une particule. */
    private fun h(i: Int, k: Int = 0): Float { val x = sin((i * 127.1f + k * 311.7f)) * 43758.547f; return x - floor(x) }

    fun colors(e: Element) = Triple(e.core.c(), e.mid.c(), e.outer.c())

    private fun DrawScope.glow(c: Offset, r: Float, color: Color, alpha: Float = 1f) {
        if (r <= 0f || alpha <= 0f) return
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), color.copy(alpha = 0f)), c, r), r, c, blendMode = Add)
    }

    /** Cercle runique : anneaux tracés progressivement ([draw] 0..1), graduations, hexagramme ; [squash] pour le poser au sol. */
    fun DrawScope.magicCircle(c: Offset, r: Float, t: Float, color: Color, draw: Float, squash: Float = 1f, alpha: Float = 1f) {
        if (draw <= 0f || alpha <= 0f) return
        val a = alpha.coerceIn(0f, 1f)
        withTransform({ scale(1f, squash, c) }) {
            glow(c, r * 1.25f, color, 0.35f * a * draw)
            rotate(t * 25f, c) {
                drawArc(color.copy(alpha = a), -90f, 360f * draw, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(r * 0.035f), blendMode = Add)
                val ticks = 36
                for (i in 0 until (ticks * draw).toInt()) {
                    val ang = i * 2 * PI.toFloat() / ticks
                    val len = if (i % 3 == 0) 0.12f else 0.06f
                    drawLine(color.copy(alpha = a * 0.9f), Offset(c.x + cos(ang) * r * 0.92f, c.y + sin(ang) * r * 0.92f),
                        Offset(c.x + cos(ang) * r * (0.92f - len), c.y + sin(ang) * r * (0.92f - len)), r * 0.02f, blendMode = Add)
                }
            }
            rotate(-t * 40f, c) {
                val r2 = r * 0.7f
                drawArc(color.copy(alpha = a * 0.8f), 90f, 360f * draw, false, Offset(c.x - r2, c.y - r2), Size(r2 * 2, r2 * 2), style = Stroke(r * 0.02f), blendMode = Add)
                // Hexagramme : deux triangles qui apparaissent après les anneaux.
                val k = ((draw - 0.35f) / 0.65f).coerceIn(0f, 1f)
                if (k > 0f) for (rot in listOf(0f, 60f)) {
                    val p = Path()
                    for (j in 0..3) { val ang = Math.toRadians((rot + j * 120 - 90).toDouble()).toFloat(); val pt = Offset(c.x + cos(ang) * r2, c.y + sin(ang) * r2); if (j == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
                    drawPath(p, color.copy(alpha = a * k), style = Stroke(r * 0.022f), blendMode = Add)
                }
                // Petites runes : points et barres sur l'anneau intérieur.
                for (j in 0 until 12) {
                    val ang = j * PI.toFloat() / 6
                    val pt = Offset(c.x + cos(ang) * r * 0.81f, c.y + sin(ang) * r * 0.81f)
                    drawCircle(color.copy(alpha = a * draw), r * 0.025f, pt, blendMode = Add)
                }
            }
        }
    }

    /** Aura d'élément autour d'un point : particules qui montent ou tournent selon l'élément. */
    fun DrawScope.aura(e: Element, c: Offset, r: Float, t: Float, alpha: Float = 1f, count: Int = 40) {
        val (core, mid, outer) = colors(e)
        val count = CardPerf.particles(count)
        glow(c, r * 1.3f, outer, 0.25f * alpha)
        for (i in 0 until count) {
            val life = 1.6f + h(i) * 1.2f
            val k = ((t + h(i, 1) * life) % life) / life
            val ang = h(i, 2) * 2 * PI.toFloat()
            val pos = when (e) {
                Element.FEU -> Offset(c.x + cos(ang) * r * (0.4f + 0.6f * h(i, 3)) + sin(t * 3 + i) * r * 0.05f, c.y + r * 0.6f - k * r * 1.8f)
                Element.GLACE -> Offset(c.x + cos(ang) * r * (0.3f + 0.8f * h(i, 3)), c.y - r * 0.8f + k * r * 1.6f)
                Element.FOUDRE -> Offset(c.x + cos(ang) * r * (0.5f + 0.5f * k), c.y + sin(ang) * r * (0.5f + 0.5f * k))
                Element.TERRE -> Offset(c.x + cos(ang) * r * 0.9f, c.y + r * 0.7f - sin(k * PI.toFloat()) * r * 0.5f)
            }
            val fade = sin(k * PI.toFloat()) * alpha
            val col = if (k < 0.3f) core else if (k < 0.7f) mid else outer
            drawCircle(col.copy(alpha = fade.coerceIn(0f, 1f)), r * (0.02f + 0.03f * h(i, 4)), pos, blendMode = Add)
        }
    }

    /** Projectile de [from] vers [to] ([k] 0..1), avec traînée. */
    fun DrawScope.projectile(e: Element, from: Offset, to: Offset, k: Float, t: Float, size: Float) {
        val (core, mid, outer) = colors(e)
        if (e == Element.FOUDRE) { bolt(from, to, t, mid, core, size * 0.08f, (k * 1.5f).coerceIn(0f, 1f)); return }
        val arc = if (e == Element.TERRE) size * 1.6f else size * 0.5f
        fun at(x: Float): Offset { val p = from + (to - from) * x; return Offset(p.x, p.y - sin(x * PI.toFloat()) * arc) }
        for (j in 14 downTo 0) {
            val x = (k - j * 0.025f).coerceIn(0f, 1f)
            val p = at(x)
            val wob = Offset.Zero
            glow(p + wob, size * (0.45f - j * 0.025f), if (j < 3) core else if (j < 8) mid else outer, 0.9f - j * 0.055f)
        }
        val head = at(k)
        when (e) {
            Element.TERRE -> rotate(t * 600f, head) { rock(head, size * 0.3f, 7, mid, outer) }
            Element.GLACE -> rotate(t * 400f, head) { shard(head, size * 0.5f, 0f, core) }
            else -> glow(head, size * 0.35f, Color.White, 0.9f)
        }
    }

    /** Impact à [c], [t] secondes après le choc (≈ 1,1 s). */
    fun DrawScope.impact(e: Element, c: Offset, t: Float, size: Float) {
        if (t < 0f || t > 1.6f) return
        val (core, mid, outer) = colors(e)
        val fade = (1f - t / 1.4f).coerceIn(0f, 1f)
        // Éclair blanc commun, puis onde de choc.
        glow(c, size * (0.6f + t * 2f), Color.White, (1f - t * 4f).coerceIn(0f, 1f))
        drawCircle(mid.copy(alpha = fade * 0.9f), size * (0.2f + t * 2.2f), c, style = Stroke(size * 0.06f * fade + 1f), blendMode = Add)
        when (e) {
            Element.FEU -> {
                glow(c, size * (0.5f + t * 1.2f), mid, fade)
                glow(c, size * (0.3f + t * 0.6f), core, fade)
                for (i in 0 until 46) {
                    val ang = h(i) * 2 * PI.toFloat(); val sp = size * (0.6f + h(i, 1) * 1.6f)
                    val d = sp * (1 - exp(-t * 3f)); val rise = t * t * size * 0.8f
                    val p = Offset(c.x + cos(ang) * d, c.y + sin(ang) * d - rise)
                    val k = (t / (0.6f + h(i, 2))).coerceIn(0f, 1f)
                    drawCircle(lerp(core, outer, k).copy(alpha = (1 - k)), size * (0.05f + 0.07f * h(i, 3)) * (1 - k * 0.5f), p, blendMode = Add)
                }
            }
            Element.GLACE -> {
                for (i in 0 until 14) {
                    val ang = i * 2 * PI.toFloat() / 14 + h(i) * 0.3f
                    val d = size * (0.2f + (1 - exp(-t * 5f)) * (0.9f + h(i, 1) * 0.8f))
                    shard(Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), size * (0.25f + h(i, 2) * 0.2f) * fade, ang, if (i % 2 == 0) core else mid)
                }
                for (i in 0 until 8) { val ang = i * PI.toFloat() / 4; drawLine(core.copy(alpha = fade), c, Offset(c.x + cos(ang) * size * 0.9f, c.y + sin(ang) * size * 0.9f), size * 0.025f, blendMode = Add) }
                glow(c, size * 0.8f, mid, fade * 0.8f)
            }
            Element.FOUDRE -> {
                for (i in 0 until 6) {
                    val ang = h(i + (t * 18).toInt() * 7) * 2 * PI.toFloat()
                    bolt(c, Offset(c.x + cos(ang) * size * 1.4f, c.y + sin(ang) * size * 1.4f), t + i, mid, core, size * 0.04f, fade)
                }
                glow(c, size * 1.1f, outer, fade)
                glow(c, size * 0.5f, core, fade * (0.6f + 0.4f * sin(t * 60f)))
            }
            Element.TERRE -> {
                glow(c, size * (0.6f + t), mid, fade * 0.7f)
                for (i in 0 until 16) {
                    val ang = -PI.toFloat() * (0.1f + 0.8f * h(i)); val v = size * (2f + h(i, 1) * 2.5f)
                    val p = Offset(c.x + cos(ang) * v * t, c.y + sin(ang) * v * t + 4.5f * size * t * t)
                    rotate(t * 300f * (h(i, 2) - 0.5f), p) { rock(p, size * (0.08f + 0.1f * h(i, 3)), 5 + i % 3, mid.copy(alpha = fade), outer.copy(alpha = fade)) }
                }
                for (i in 0 until 6) { val ang = (i / 6f) * PI.toFloat() * 2; val p = Path().apply { moveTo(c.x, c.y)
                    lineTo(c.x + cos(ang) * size * 0.5f, c.y + sin(ang) * size * 0.25f); lineTo(c.x + cos(ang + 0.2f) * size * 0.9f, c.y + sin(ang + 0.2f) * size * 0.45f) }
                    drawPath(p, core.copy(alpha = fade), style = Stroke(size * 0.03f), blendMode = Add) }
            }
        }
    }

    private fun DrawScope.bolt(from: Offset, to: Offset, t: Float, outer: Color, core: Color, width: Float, alpha: Float) {
        if (alpha <= 0f) return
        val seed = (t * 20).toInt()
        val n = 9
        val p = Path().apply { moveTo(from.x, from.y)
            val d = to - from; val nx = -d.y; val ny = d.x; val len = sqrt(nx * nx + ny * ny).coerceAtLeast(1f)
            for (i in 1 until n) { val k = i / n.toFloat(); val off = (h(seed * 31 + i) - 0.5f) * 0.25f * len
                lineTo(from.x + d.x * k + nx / len * off, from.y + d.y * k + ny / len * off) }
            lineTo(to.x, to.y) }
        drawPath(p, outer.copy(alpha = alpha * 0.6f), style = Stroke(width * 3.5f), blendMode = Add)
        drawPath(p, core.copy(alpha = alpha), style = Stroke(width), blendMode = Add)
    }

    private fun DrawScope.shard(c: Offset, len: Float, ang: Float, color: Color) {
        if (len <= 0f) return
        val p = Path().apply {
            moveTo(c.x + cos(ang) * len, c.y + sin(ang) * len)
            lineTo(c.x + cos(ang + 2.6f) * len * 0.3f, c.y + sin(ang + 2.6f) * len * 0.3f)
            lineTo(c.x - cos(ang) * len * 0.4f, c.y - sin(ang) * len * 0.4f)
            lineTo(c.x + cos(ang - 2.6f) * len * 0.3f, c.y + sin(ang - 2.6f) * len * 0.3f); close()
        }
        drawPath(p, color, blendMode = Add)
    }

    private fun DrawScope.rock(c: Offset, r: Float, sides: Int, fill: Color, edge: Color) {
        val p = Path()
        for (i in 0..sides) { val ang = i * 2 * PI.toFloat() / sides; val rr = r * (0.75f + 0.25f * h(i, sides)); val pt = Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr)
            if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
        drawPath(p, fill); drawPath(p, edge, style = Stroke(r * 0.15f))
    }

    fun DrawScope.star(c: Offset, r: Float, color: Color) {
        if (r <= 0f) return
        val p = Path().apply { moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y); quadraticTo(c.x, c.y, c.x, c.y + r); quadraticTo(c.x, c.y, c.x - r, c.y); quadraticTo(c.x, c.y, c.x, c.y - r) }
        drawPath(p, color, blendMode = Add)
    }

    /** Pilier de lumière vertical, pour les invocations. */
    fun DrawScope.pillar(c: Offset, width: Float, height: Float, color: Color, alpha: Float) {
        if (alpha <= 0f) return
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = alpha * 0.8f), Color.White.copy(alpha = alpha), color.copy(alpha = alpha * 0.8f), Color.Transparent), c.x - width, c.x + width),
            Offset(c.x - width, c.y - height), Size(width * 2, height), blendMode = Add)
    }

    /** Emblème d'un élément (flamme, flocon, éclair…), pour les boosters et les bandeaux de duel. */
    fun DrawScope.emblem(e: Element, c: Offset, r: Float, color: Color) {
        val st = Stroke(r * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (e) {
            Element.FEU -> drawPath(Path().apply { moveTo(c.x, c.y - r); cubicTo(c.x + r * 0.9f, c.y - r * 0.1f, c.x + r * 0.7f, c.y + r, c.x, c.y + r)
                cubicTo(c.x - r * 0.7f, c.y + r, c.x - r * 0.8f, c.y + r * 0.1f, c.x - r * 0.25f, c.y - r * 0.3f); cubicTo(c.x - r * 0.2f, c.y + r * 0.1f, c.x + r * 0.1f, c.y + r * 0.1f, c.x, c.y - r) }, color)
            Element.GLACE -> for (i in 0 until 6) { val ang = i * PI.toFloat() / 3; val tip = Offset(c.x + cos(ang) * r, c.y + sin(ang) * r)
                drawLine(color, c, tip, r * 0.14f, StrokeCap.Round)
                val mid = Offset(c.x + cos(ang) * r * 0.55f, c.y + sin(ang) * r * 0.55f)
                drawLine(color, mid, Offset(mid.x + cos(ang + 0.8f) * r * 0.3f, mid.y + sin(ang + 0.8f) * r * 0.3f), r * 0.1f, StrokeCap.Round)
                drawLine(color, mid, Offset(mid.x + cos(ang - 0.8f) * r * 0.3f, mid.y + sin(ang - 0.8f) * r * 0.3f), r * 0.1f, StrokeCap.Round) }
            Element.FOUDRE -> drawPath(Path().apply { moveTo(c.x + r * 0.25f, c.y - r); lineTo(c.x - r * 0.45f, c.y + r * 0.1f); lineTo(c.x - r * 0.02f, c.y + r * 0.1f)
                lineTo(c.x - r * 0.25f, c.y + r); lineTo(c.x + r * 0.5f, c.y - r * 0.15f); lineTo(c.x + r * 0.05f, c.y - r * 0.15f); close() }, color)
            Element.TERRE -> drawPath(Path().apply { moveTo(c.x - r, c.y + r * 0.7f); lineTo(c.x - r * 0.3f, c.y - r * 0.6f); lineTo(c.x, c.y - r * 0.1f); lineTo(c.x + r * 0.35f, c.y - r * 0.8f); lineTo(c.x + r, c.y + r * 0.7f); close() }, color)
        }
    }
}
