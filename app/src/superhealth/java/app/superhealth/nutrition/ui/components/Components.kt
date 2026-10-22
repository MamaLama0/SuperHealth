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
import app.superhealth.ui.drawPixelArt
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

/** Icône d'aliment associée au nom (pixel-art du pack si elle existe, sinon MacroFactor) ; pastille neutre si aucune ne correspond. */
@Composable
fun FoodIcon(name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val id = remember(name) { FoodIcons.match(name) }
    val px = remember(id) { id?.let { FoodIcons.pixel(it) } }
    val bmp = remember(id) { if (px == null) id?.let { FoodIcons.bitmap(it) } else null }
    Box(modifier.size(size).clip(CircleShape).background(Mf.colors.chip), contentAlignment = Alignment.Center) {
        if (px != null) Canvas(Modifier.size(size * 0.76f)) { drawPixelArt(px, center, this.size.minDimension) }
        else if (bmp != null) Image(bmp, null, Modifier.size(size * 0.72f))
        else Icon(Icons.Rounded.Restaurant, null, Modifier.size(size * 0.45f), tint = Mf.colors.textSecondary)
    }
}

@Composable
fun AssetImage(path: String, modifier: Modifier = Modifier, crop: Boolean = false) {
    val bmp = remember(path) { FoodIcons.asset(path) }
    if (bmp != null) Image(bmp, null, modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 4.dp, track: Color = Mf.colors.chip) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
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

/** Pastille de pourcentage colorée (fiche aliment). */
@Composable
fun PercentPill(pct: Int, color: Color) {
    Text("${pct}%", Modifier.clip(CircleShape).background(color.copy(alpha = 0.28f)).border(0.dp, Color.Transparent, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Mf.colors.text)
}

