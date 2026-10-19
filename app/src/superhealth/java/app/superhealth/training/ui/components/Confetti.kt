package app.superhealth.training.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

/**
 * Pluie de confettis de fin de séance (comme l'artboard « confetti » de mf_wo_demo.riv : cercles, anneaux et étoiles
 * vert / bleu / rouge / jaune). Dessinée en Compose : le runtime Rive embarqué ne gère pas les émetteurs liés aux données.
 */
@Composable
fun Confetti(modifier: Modifier, durationMs: Int = 3800) {
    val colors = listOf(Color(0xFF4CB179), Color(0xFF276EF1), Color(0xFFF46B5A), Color(0xFFFFC447))
    data class P(val x: Float, val vx: Float, val vy: Float, val size: Float, val color: Color, val spin: Float, val shape: Int, val delay: Float)
    val parts = remember {
        val r = Random(System.nanoTime())
        List(90) { P(r.nextFloat(), (r.nextFloat() - 0.5f) * 0.5f, 0.6f + r.nextFloat() * 0.9f, 6f + r.nextFloat() * 8f,
            colors[r.nextInt(colors.size)], (r.nextFloat() - 0.5f) * 900f, r.nextInt(3), r.nextFloat() * 0.25f) }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
    Canvas(modifier) {
        parts.forEach { p ->
            val k = ((t.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (k <= 0f || k >= 1f) return@forEach
            val x = (p.x + p.vx * k) * size.width
            val y = -30f + (p.vy * 0.55f * k + 0.45f * k * k) * size.height
            val alpha = if (k > 0.8f) (1f - k) / 0.2f else 1f
            val c = p.color.copy(alpha = alpha)
            val s = p.size * density * 0.9f
            rotate(p.spin * k, Offset(x, y)) {
                when (p.shape) {
                    0 -> drawCircle(c, s / 2, Offset(x, y))
                    1 -> drawCircle(c, s / 2, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(s / 4))
                    else -> drawRect(c, Offset(x - s / 2, y - s / 4), Size(s, s / 2))
                }
            }
        }
    }
}
