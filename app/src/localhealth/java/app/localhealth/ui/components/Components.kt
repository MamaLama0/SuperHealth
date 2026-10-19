package app.localhealth.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.localhealth.ui.theme.ColorFamily

/** Animated 0..1 progress, replayed whenever [target] changes. */
@Composable
fun animatedProgress(target: Float): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target, tween(900, easing = FastOutSlowInEasing)) }
    return anim.value
}

/** Concentric goal rings, outermost first (Google Health "today" hero). */
@Composable
fun GoalRings(rings: List<Pair<Float, ColorFamily>>, size: Dp, stroke: Dp = 14.dp, gap: Dp = 4.dp) {
    val progresses = rings.map { animatedProgress(it.first) }
    Canvas(Modifier.size(size)) {
        val strokePx = stroke.toPx()
        rings.forEachIndexed { i, (_, family) ->
            val inset = strokePx / 2 + i * (strokePx + gap.toPx())
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(family.data.copy(alpha = 0.18f), -90f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            if (progresses[i] > 0f) {
                drawArc(family.data, -90f, 360f * progresses[i], false, topLeft, arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun LinearGoalBar(progress: Float, family: ColorFamily, modifier: Modifier = Modifier) {
    val p = animatedProgress(progress)
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val r = size.height / 2
        drawRoundRect(family.data.copy(alpha = 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        if (p > 0f) {
            drawRoundRect(family.data, size = Size(size.width * p, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, family: ColorFamily, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).background(family.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = family.onContainer, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
fun HealthCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

/** Small metric tile: icon + label, big value with unit, optional goal bar / caption. */
@Composable
fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String?,
    unit: String?,
    family: ColorFamily,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    caption: String? = null,
    emptyText: String,
    onClick: (() -> Unit)? = null,
) {
    HealthCard(modifier, onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, family, 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            if (value == null) {
                Text(emptyText, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface)
                    if (unit != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(unit, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
            }
            if (progress != null && value != null) {
                Spacer(Modifier.height(10.dp))
                LinearGoalBar(progress, family)
            }
            if (caption != null) {
                Spacer(Modifier.height(6.dp))
                Text(caption, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Two tiles per row. */
@Composable
fun TileRow(left: @Composable (Modifier) -> Unit, right: (@Composable (Modifier) -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        left(Modifier.weight(1f))
        if (right != null) right(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

@Composable
fun LegendDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}
