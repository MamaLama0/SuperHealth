package app.superhealth.nutrition.ui.components

import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.ui.FoodIcons
import app.superhealth.nutrition.ui.num
import app.superhealth.ui.theme.CaloriesColor
import app.superhealth.ui.theme.CarbsColor
import app.superhealth.ui.theme.FatColor
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.ProteinColor
import kotlin.math.roundToInt

val CardShape = RoundedCornerShape(app.superhealth.ui.theme.CardRadius)

/** Carte blanche (clair) / #1E1E1E (sombre), coins 16 dp, sans ombre. */
@Composable
fun MfCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(Mf.colors.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding),
        content = content,
    )
}

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr(title), style = MaterialTheme.typography.headlineMedium)
            subtitle?.let { Text(tr(it), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary) }
        }
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(text), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Icône d'aliment MacroFactor associée au nom ; pastille neutre si aucune ne correspond. */
@Composable
fun FoodIcon(name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val bmp = remember(name) { FoodIcons.match(name)?.let { FoodIcons.bitmap(it) } }
    Box(modifier.size(size).clip(CircleShape).background(Mf.colors.chip), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, null, Modifier.size(size * 0.72f))
        else Icon(Icons.Rounded.Restaurant, null, Modifier.size(size * 0.45f), tint = Mf.colors.textSecondary)
    }
}

@Composable
fun AssetImage(path: String, modifier: Modifier = Modifier, crop: Boolean = false) {
    val bmp = remember(path) { FoodIcons.asset(path) }
    if (bmp != null) Image(bmp, null, modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Mf.colors.chip)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(color))
    }
}

/** Anneau de progression (cercle de fond + arc). */
@Composable
fun Ring(fraction: Float, color: Color, size: Dp, stroke: Dp = 4.dp, track: Color = Mf.colors.chip, content: @Composable BoxScope.() -> Unit = {}) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            if (fraction > 0f) drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

private fun ratio(v: Double, t: Double?) = if (t == null || t <= 0) 0f else (v / t).toFloat()

/** Ligne « 🔥 583 / 2025 · P 22 / 98 · L 23 / 67 · G 84 / 256 » avec barres colorées, comme l'en-tête du journal MacroFactor. */
@Composable
fun MacroSummary(consumed: Macros, target: Macros?, energyValue: (Double) -> Double, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MacroCell(null, energyValue(consumed.kcal), target?.kcal?.let(energyValue), ratio(consumed.kcal, target?.kcal), CaloriesColor, Modifier.weight(1.25f))
        MacroCell(tr("P"), consumed.protein, target?.protein, ratio(consumed.protein, target?.protein), ProteinColor, Modifier.weight(1f))
        MacroCell(tr("L"), consumed.fat, target?.fat, ratio(consumed.fat, target?.fat), FatColor, Modifier.weight(1f))
        MacroCell(tr("G"), consumed.carbs, target?.carbs, ratio(consumed.carbs, target?.carbs), CarbsColor, Modifier.weight(1f))
    }
}

@Composable
private fun MacroCell(letter: String?, value: Double, target: Double?, fraction: Float, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (letter == null) Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(13.dp), tint = Mf.colors.text)
            else Text(letter, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(3.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(value.roundToInt().toString()) }
                    target?.let { withStyle(SpanStyle(color = Mf.colors.textSecondary)) { append(" / ${it.roundToInt()}") } }
                },
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Clip,
            )
        }
        Spacer(Modifier.height(5.dp))
        ProgressBar(fraction, color, height = 3.dp)
    }
}

/** « 295🔥 17P 21L 12G » — ligne compacte de macros d'une entrée. */
@Composable
fun MacroLine(m: Macros, energyValue: (Double) -> Double, suffix: String? = null, style: TextStyle = MaterialTheme.typography.bodySmall, color: Color = Mf.colors.textSecondary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(energyValue(m.kcal).roundToInt().toString(), style = style, color = color)
        Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(11.dp).padding(start = 1.dp), tint = color)
        Text(trf("  %1\$sP  %2\$sL  %3\$sG", (m.protein.roundToInt()), (m.fat.roundToInt()), (m.carbs.roundToInt())) + (suffix?.let { "  •  ${it}" } ?: ""),
            style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Ligne de réglage façon MacroFactor : titre, valeur à droite, chevron. */
@Composable
fun SettingRow(title: String, value: String? = null, subtitle: String? = null, icon: ImageVector? = null, danger: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Mf.colors.chip), contentAlignment = Alignment.Center) {
                Icon(it, null, Modifier.size(18.dp), tint = if (danger) MaterialTheme.colorScheme.error else Mf.colors.text)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(tr(title), style = MaterialTheme.typography.bodyLarge, color = if (danger) MaterialTheme.colorScheme.error else Mf.colors.text)
            subtitle?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
        }
        value?.let { Text(tr(it), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, textAlign = TextAlign.End,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).widthIn(max = 150.dp)) }
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr(title), style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Mf.colors.accent, checkedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent, uncheckedTrackColor = Mf.colors.chip))
    }
}

@Composable
fun RowDivider() = Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(Mf.colors.divider))

/** Groupe de lignes dans une carte (réglages). */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(Mf.colors.card), content = content)
}

/** Bouton pilule : « fort » (noir/blanc) ou neutre (gris). */
@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, strong: Boolean = true, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg = if (strong) Mf.colors.strong else Mf.colors.chip
    val fg = if (strong) Mf.colors.onStrong else Mf.colors.text
    Row(
        modifier.clip(CircleShape).background(if (enabled) bg else bg.copy(alpha = 0.35f)).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = fg); Spacer(Modifier.width(8.dp)) }
        Text(tr(text), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = fg,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** Choix exclusif sous forme de pastilles. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> ChipChoice(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    // Pastilles renvoyées à la ligne plutôt que défilantes : tout reste visible, quelle que soit la langue.
    androidx.compose.foundation.layout.FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val sel = o == selected
            Text(
                tr(label(o)),
                Modifier.clip(CircleShape).background(if (sel) Mf.colors.strong else Mf.colors.chip).clickable { onSelect(o) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = if (sel) Mf.colors.onStrong else Mf.colors.text,
            )
        }
    }
}

/** Champ numérique sobre : libellé au-dessus, fond gris arrondi, suffixe d'unité. */
@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null, keyboard: KeyboardType = KeyboardType.Decimal) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(tr(label), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mf.colors.chip).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value, onChange, Modifier.weight(1f), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Mf.colors.text),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard), cursorBrush = SolidColor(Mf.colors.accent),
            )
            suffix?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary) }
        }
    }
}

@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "") {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(tr(label), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mf.colors.chip).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (value.isEmpty()) Text(tr(placeholder), style = MaterialTheme.typography.bodyLarge, color = Mf.colors.textTertiary)
            BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Mf.colors.text), cursorBrush = SolidColor(Mf.colors.accent))
        }
    }
}

/** Grande valeur + libellé (statistiques des cartes). */
@Composable
fun BigStat(value: String, label: String, modifier: Modifier = Modifier, unit: String? = null, color: Color = Mf.colors.text) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
            unit?.let { Text(" ${it}", style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 3.dp)) }
        }
        Text(tr(label), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }
}

data class Series(
    val points: List<Pair<Long, Double>>, val color: Color, val dots: Boolean = false, val dashed: Boolean = false,
    /** Bande (bas, haut) remplie autour de la courbe. */
    val band: List<Triple<Long, Double, Double>>? = null, val width: Float = 2.5f,
)

/** Courbe : points (pesées) et/ou lignes (tendance, dépense), grille discrète. */
@Composable
fun LineChart(series: List<Series>, modifier: Modifier = Modifier, height: Dp = 160.dp, showAxis: Boolean = true, format: (Double) -> String = { num(it, 1) }) {
    val all = series.flatMap { it.points } + series.flatMap { s -> s.band.orEmpty().flatMap { listOf(it.first to it.second, it.first to it.third) } }
    if (all.size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(tr("Pas encore assez de données"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        return
    }
    val minX = all.minOf { it.first }.toDouble(); val maxX = maxOf(all.maxOf { it.first }.toDouble(), minX + 1)
    val pad = (all.maxOf { it.second } - all.minOf { it.second }).coerceAtLeast(0.5) * 0.12
    val minY = all.minOf { it.second } - pad; val maxY = all.maxOf { it.second } + pad
    val grid = Mf.colors.divider
    val axisColor = Mf.colors.textSecondary
    Row(modifier.fillMaxWidth()) {
        Canvas(Modifier.weight(1f).height(height)) {
            fun x(v: Long) = ((v - minX) / (maxX - minX) * size.width).toFloat()
            fun y(v: Double) = (size.height - ((v - minY) / (maxY - minY)) * size.height).toFloat()
            listOf(0f, 0.5f, 1f).forEach { f -> drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f), 1.dp.toPx()) }
            series.forEach { s ->
                s.band?.takeIf { it.size >= 2 }?.let { b ->
                    val path = Path()
                    b.forEachIndexed { i, (px, lo, _) -> if (i == 0) path.moveTo(x(px), y(lo)) else path.lineTo(x(px), y(lo)) }
                    b.reversed().forEach { (px, _, hi) -> path.lineTo(x(px), y(hi)) }
                    path.close()
                    drawPath(path, s.color.copy(alpha = 0.18f))
                }
                if (s.dots) {
                    s.points.forEach { (px, py) -> drawCircle(s.color.copy(alpha = 0.55f), radius = 2.5.dp.toPx(), center = Offset(x(px), y(py))) }
                } else if (s.points.size >= 2) {
                    val path = Path()
                    s.points.forEachIndexed { i, (px, py) -> if (i == 0) path.moveTo(x(px), y(py)) else path.lineTo(x(px), y(py)) }
                    drawPath(path, s.color, style = Stroke(s.width.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null))
                }
            }
        }
        if (showAxis) Column(Modifier.height(height).padding(start = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf(maxY, (maxY + minY) / 2, minY).forEach { Text(format(it), fontSize = 10.sp, color = axisColor) }
        }
    }
}

/** Barres journalières avec ligne de cible pointillée. */
@Composable
fun BarChart(values: List<Double?>, target: List<Double?>, color: Color, modifier: Modifier = Modifier, height: Dp = 120.dp, labels: List<String> = emptyList()) {
    val max = (values.filterNotNull() + target.filterNotNull()).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val track = Mf.colors.chip
    val line = Mf.colors.textSecondary
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.56f
            values.forEachIndexed { i, v ->
                val left = i * slot + (slot - bw) / 2
                drawRoundRect(track, Offset(left, 0f), Size(bw, size.height), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                if (v != null && v > 0) {
                    val h = (v / max * size.height).toFloat().coerceAtLeast(bw)
                    drawRoundRect(color, Offset(left, size.height - h), Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                }
                target.getOrNull(i)?.let { t ->
                    val ty = (size.height - t / max * size.height).toFloat()
                    drawLine(line, Offset(i * slot + 2, ty), Offset((i + 1) * slot - 2, ty), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
            }
        }
        if (labels.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), fontSize = 10.sp, color = Mf.colors.textSecondary, textAlign = TextAlign.Center) }
        }
    }
}

/** Pastille de pourcentage colorée (fiche aliment). */
@Composable
fun PercentPill(pct: Int, color: Color) {
    Text("${pct}%", Modifier.clip(CircleShape).background(color.copy(alpha = 0.28f)).border(0.dp, Color.Transparent, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Mf.colors.text)
}

/** Barres signées autour de zéro (balance énergétique : surplus au-dessus, déficit en dessous). */
@Composable
fun SignedBarChart(values: List<Double?>, posColor: Color, negColor: Color, modifier: Modifier = Modifier, height: Dp = 140.dp) {
    val maxAbs = values.filterNotNull().maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1.0) ?: 1.0
    val axis = Mf.colors.divider
    Canvas(modifier.fillMaxWidth().height(height)) {
        val mid = size.height / 2
        drawLine(axis, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        val n = values.size.coerceAtLeast(1)
        val slot = size.width / n
        val bw = (slot * 0.6f).coerceAtMost(14.dp.toPx())
        values.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val h = (kotlin.math.abs(v) / maxAbs * (mid - 2)).toFloat()
            val left = i * slot + (slot - bw) / 2
            val top = if (v >= 0) mid - h else mid
            drawRoundRect(if (v >= 0) posColor else negColor, Offset(left, top), Size(bw, h.coerceAtLeast(1f)),
                androidx.compose.ui.geometry.CornerRadius(bw / 3))
        }
    }
}

/** Cascade (progression d'objectif) : barre initiale, variations, barre restante. */
@Composable
fun WaterfallChart(start: Double, steps: List<Double>, remaining: Double, toward: (Double) -> Boolean, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val good = Mf.colors.accent
    val bad = Mf.colors.textTertiary
    val base = Mf.colors.strong
    Canvas(modifier.fillMaxWidth().height(height)) {
        val total = kotlin.math.max(kotlin.math.abs(start), 0.1)
        val n = steps.size + 2
        val slot = size.width / n
        val bw = slot * 0.62f
        fun yOf(v: Double) = (size.height * (1 - v / total)).toFloat().coerceIn(0f, size.height)
        drawRoundRect(base, Offset((slot - bw) / 2, yOf(kotlin.math.abs(start))), Size(bw, size.height - yOf(kotlin.math.abs(start))), androidx.compose.ui.geometry.CornerRadius(4f))
        var level = kotlin.math.abs(start)
        steps.forEachIndexed { i, d ->
            val next = (level - (if (toward(d)) kotlin.math.abs(d) else -kotlin.math.abs(d))).coerceIn(0.0, total)
            val top = yOf(kotlin.math.max(level, next)); val bot = yOf(kotlin.math.min(level, next))
            drawRoundRect(if (toward(d)) good else bad, Offset((i + 1) * slot + (slot - bw) / 2, top), Size(bw, (bot - top).coerceAtLeast(2f)), androidx.compose.ui.geometry.CornerRadius(4f))
            level = next
        }
        val r = kotlin.math.abs(remaining).coerceAtMost(total)
        drawRoundRect(base.copy(alpha = 0.35f), Offset((n - 1) * slot + (slot - bw) / 2, yOf(r)), Size(bw, size.height - yOf(r)), androidx.compose.ui.geometry.CornerRadius(4f))
    }
}
