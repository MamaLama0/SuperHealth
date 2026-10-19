package app.localhealth.ui.components

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import app.localhealth.data.ChartKind
import app.localhealth.ui.theme.ColorFamily
import nodomain.freeyourgadget.gadgetbridge.R

@Composable
private fun rememberGoogleSans(weight: Int): Typeface {
    val context = LocalContext.current
    return remember(weight) {
        val base = runCatching { ResourcesCompat.getFont(context, R.font.google_sans_flex) }.getOrNull() ?: Typeface.DEFAULT
        Typeface.create(base, weight, false)
    }
}

/**
 * Google Health's "Cardio hebdomadaire" ring: thick grey track with progress, an inner disc with
 * the percentage, curved captions along the top and bottom of the disc and a "+N today" badge.
 */
@Composable
fun CardioRing(
    progress: Float,
    percentText: String,
    topCaption: String,
    bottomCaption: String,
    badge: String?,
    family: ColorFamily,
    modifier: Modifier = Modifier,
    size: Dp = 201.dp,
) {
    val p = animatedProgress(progress)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val inner = MaterialTheme.colorScheme.surfaceContainer
    val topColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bottomColor = MaterialTheme.colorScheme.primary
    val bold = rememberGoogleSans(700)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = this.size.width * 0.155f
            drawCircle(track)
            if (p > 0f) {
                val inset = ring / 2
                drawArc(
                    family.data, -90f, 360f * p, false,
                    Offset(inset, inset), Size(this.size.width - ring, this.size.height - ring),
                    style = Stroke(ring * 0.72f, cap = StrokeCap.Round),
                )
            }
            val innerR = this.size.width / 2 - ring
            drawCircle(inner, radius = innerR)
            // Captions follow a circle just inside the disc edge
            val textR = innerR - 16.dp.toPx()
            val c = center
            drawIntoCanvas { canvas ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = bold
                    textSize = 17.sp.toPx()
                    textAlign = Paint.Align.CENTER
                }
                val arc = Path().apply {
                    addArc(c.x - textR, c.y - textR, c.x + textR, c.y + textR, 180f, 180f)
                }
                paint.color = topColor.toArgb()
                canvas.nativeCanvas.drawTextOnPath(ellipsize(topCaption, paint, Math.PI.toFloat() * textR * 0.95f), arc, 0f, 0f, paint)
                // Bottom caption reads left-to-right, so walk the lower half counter-clockwise
                val r2 = textR + paint.textSize * 0.75f
                val bottomArc = Path().apply {
                    addArc(c.x - r2, c.y - r2, c.x + r2, c.y + r2, 180f, -180f)
                }
                paint.color = bottomColor.toArgb()
                canvas.nativeCanvas.drawTextOnPath(ellipsize(bottomCaption, paint, Math.PI.toFloat() * r2 * 0.95f), bottomArc, 0f, 0f, paint)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            val (num, unit) = percentText.partition { it.isDigit() || it == '+' || it == '-' }
            Text(num, style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp, lineHeight = 52.sp),
                color = MaterialTheme.colorScheme.onSurface)
            Text(unit.trim(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        if (badge != null) {
            Box(
                Modifier.align(Alignment.BottomCenter).size(32.dp).clip(CircleShape).background(family.dataBright),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = family.onFixed)
            }
        }
    }
}

private fun niceStep(raw: Double): Double {
    val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val f = raw / mag
    return mag * when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 2.5 -> 2.5
        f <= 5.0 -> 5.0
        else -> 10.0
    }
}

private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
    return text.substring(0, end).trimEnd() + "…"
}

/**
 * Week mini chart from Google Health's key-stat cards: seven day letters with today's letter
 * highlighted, and above them a line, dots or bars depending on [kind].
 */
@Composable
fun WeekMiniChart(
    values: List<Double?>,
    dayLetters: List<String>,
    todayIndex: Int,
    kind: ChartKind,
    family: ColorFamily,
    modifier: Modifier = Modifier,
    goal: Double? = null,
) {
    val max = (values.filterNotNull() + listOfNotNull(goal)).maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp)) {
            val n = values.size
            val step = size.width / n
            val empty = values.none { it != null && it > 0 }
            if (empty) {
                // Google Health's empty state: a dotted baseline, one dot per day
                val yy = size.height - 4.dp.toPx()
                drawLine(family.data.copy(alpha = 0.9f), Offset(step / 2, yy), Offset(size.width - step / 2, yy), 2.dp.toPx())
                values.indices.forEach { i -> drawCircle(family.data, 4.dp.toPx(), Offset(i * step + step / 2, yy)) }
                return@Canvas
            }
            when (kind) {
                ChartKind.BARS -> {
                    val barW = step * 0.78f
                    values.forEachIndexed { i, v ->
                        val x = i * step + (step - barW) / 2
                        drawRoundRect(family.container, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2))
                        if (v != null && v > 0) {
                            val h = (size.height * (v / max)).toFloat().coerceAtLeast(barW)
                            drawRoundRect(family.data, Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2))
                        }
                    }
                }
                ChartKind.LINE, ChartKind.DOTS -> {
                    val pts = values.mapIndexed { i, v ->
                        v?.let { Offset(i * step + step / 2, (size.height - 4.dp.toPx() - (size.height - 8.dp.toPx()) * (it / max)).toFloat()) }
                    }
                    val present = pts.filterNotNull()
                    if (present.isEmpty()) {
                        // Google Health draws a flat teal-ish baseline when there's nothing yet
                        drawLine(family.data, Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 2.dp.toPx())
                    } else {
                        if (kind == ChartKind.LINE) {
                            present.zipWithNext { a, b -> drawLine(family.data, a, b, 2.5.dp.toPx(), cap = StrokeCap.Round) }
                        }
                        present.forEach { drawCircle(family.data, 4.dp.toPx(), it) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            dayLetters.forEachIndexed { i, l ->
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(if (i == todayIndex) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(l, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Seven rounded bars for "Jours d'exercice": filled when that day had a workout. */
@Composable
fun ExerciseDaysBars(done: List<Boolean>, dayLetters: List<String>, todayIndex: Int, family: ColorFamily, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            done.forEach { d ->
                Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp))
                    .background(if (d) family.data else family.container.copy(alpha = 0.55f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
            dayLetters.forEachIndexed { i, l ->
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(if (i == todayIndex) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(l, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** White rounded chip under the mini chart ("Total semaine : 0", "Aucune donnée"). */
@Composable
fun ValueChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/**
 * Detail-page chart: bars, line or dots with the value axis on the right and x labels below,
 * like the metric pages in Google Health.
 */
@Composable
fun DetailChart(
    values: List<Double?>,
    xLabels: List<Pair<Int, String>>,
    kind: ChartKind,
    family: ColorFamily,
    formatAxis: (Double) -> String,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    goal: Double? = null,
    onSelect: ((Int) -> Unit)? = null,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val selColor = MaterialTheme.colorScheme.onSurface
    val tf = rememberGoogleSans(400)
    val present = values.filterNotNull()
    val rawMin = if (kind == ChartKind.BARS) 0.0 else (present.minOrNull() ?: 0.0)
    val rawMax = (present + listOfNotNull(goal)).maxOrNull() ?: 1.0
    val pad = if (kind == ChartKind.BARS) 0.0 else ((rawMax - rawMin).takeIf { it > 0 } ?: (rawMax * 0.05 + 1)) * 0.25
    // Round the axis to "nice" steps (1, 2, 2.5, 5 × 10^n) so ticks read 0 / 2 500 / 5 000…
    val tickStep = niceStep(((rawMax + pad) - (rawMin - pad)).coerceAtLeast(1e-6) / 3)
    val lo = if (kind == ChartKind.BARS) 0.0 else Math.floor((rawMin - pad) / tickStep) * tickStep
    val hi = (Math.ceil((rawMax + pad) / tickStep) * tickStep).takeIf { it > lo } ?: (lo + tickStep)
    BoxWithConstraints(modifier.fillMaxWidth().height(230.dp)) {
        val tapModifier = if (onSelect == null) Modifier else Modifier.pointerInput(values.size) {
            detectTapGestures { pos ->
                val w = size.width - 44.dp.toPx()
                val i = (pos.x / (w / values.size.coerceAtLeast(1))).toInt()
                if (i in values.indices && values[i] != null) onSelect(i)
            }
        }
        Canvas(Modifier.fillMaxSize().then(tapModifier)) {
            val axisW = 44.dp.toPx()
            val bottomH = 26.dp.toPx()
            val w = size.width - axisW
            val h = size.height - bottomH
            val n = values.size.coerceAtLeast(1)
            val step = w / n
            fun y(v: Double) = (h - (h * ((v - lo) / (hi - lo)))).toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                textSize = 13.sp.toPx()
                color = labelColor.toArgb()
            }
            // Value axis on the right
            val ticks = ((hi - lo) / tickStep).toInt().coerceIn(1, 6)
            for (t in 0..ticks) {
                val v = lo + tickStep * t
                val yy = y(v)
                drawLine(gridColor.copy(alpha = 0.5f), Offset(0f, yy), Offset(w, yy), 1f)
                drawIntoCanvas { it.nativeCanvas.drawText(formatAxis(v), w + 8.dp.toPx(), yy + paint.textSize / 3, paint) }
            }
            goal?.let {
                val gy = y(it)
                drawLine(family.data.copy(alpha = 0.6f), Offset(0f, gy), Offset(w, gy), 1.5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
            // X labels
            xLabels.forEach { (i, label) ->
                val x = i * step + step / 2
                drawLine(labelColor, Offset(x, h + 2.dp.toPx()), Offset(x, h + 6.dp.toPx()), 2f)
                paint.textAlign = Paint.Align.CENTER
                drawIntoCanvas { it.nativeCanvas.drawText(label, x.coerceIn(paint.measureText(label) / 2, w), size.height - 4.dp.toPx(), paint) }
                paint.textAlign = Paint.Align.LEFT
            }
            when (kind) {
                ChartKind.BARS -> {
                    val barW = (step * 0.62f).coerceAtMost(28.dp.toPx())
                    values.forEachIndexed { i, v ->
                        if (v != null && v > 0) {
                            val x = i * step + (step - barW) / 2
                            val top = y(v)
                            drawRoundRect(if (selected == null || selected == i) family.data else family.container,
                                Offset(x, top), Size(barW, h - top), CornerRadius(barW / 2.5f))
                        }
                    }
                }
                ChartKind.LINE, ChartKind.DOTS -> {
                    val pts = values.mapIndexed { i, v -> v?.let { Offset(i * step + step / 2, y(it)) } }
                    if (kind == ChartKind.LINE) {
                        pts.zipWithNext { a, b -> if (a != null && b != null) drawLine(family.data, a, b, 3.dp.toPx(), cap = StrokeCap.Round) }
                    }
                    val r = if (n > 60) 2.5.dp.toPx() else 4.5.dp.toPx()
                    pts.forEach { it?.let { o -> if (kind == ChartKind.DOTS || n <= 60) drawCircle(family.data, r, o) } }
                }
            }
            selected?.let { i ->
                val v = values.getOrNull(i) ?: return@let
                val x = i * step + step / 2
                drawLine(selColor, Offset(x, 0f), Offset(x, h), 2.5.dp.toPx(), cap = StrokeCap.Round)
                if (kind != ChartKind.BARS) {
                    drawCircle(Color.White, 7.dp.toPx(), Offset(x, y(v)))
                    drawCircle(selColor, 7.dp.toPx(), Offset(x, y(v)), style = Stroke(2.5.dp.toPx()))
                }
            }
        }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).offset().clip(CircleShape).background(color))
}
