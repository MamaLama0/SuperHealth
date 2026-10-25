package app.superhealth.training.ui.components

import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
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
import app.superhealth.training.ui.num
import app.superhealth.training.ui.Assets
import app.superhealth.ui.theme.Mf
import kotlin.math.roundToInt

val CardShape = RoundedCornerShape(app.superhealth.ui.theme.CardRadius)

/** Carte (composant partagé de l'app). */
@Composable
fun MfCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) =
    app.superhealth.nutrition.ui.components.MfCard(modifier, onClick, padding, content)

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onBack: (() -> Unit)? = null,
                actions: @Composable RowScope.() -> Unit = {}) =
    app.superhealth.nutrition.ui.components.ScreenTitle(tr(title), modifier, subtitle?.let(::tr), onBack, actions)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) = app.superhealth.nutrition.ui.components.SectionLabel(tr(text), modifier, trailing)

/** Icône d'aliment MacroFactor associée au nom ; pastille neutre si aucune ne correspond. */
@Composable
fun AssetImage(path: String, modifier: Modifier = Modifier, crop: Boolean = false) {
    val bmp = remember(path) { Assets.bitmap(path) }
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

@Composable
fun SettingRow(title: String, value: String? = null, subtitle: String? = null, icon: ImageVector? = null, danger: Boolean = false,
               hue: Color? = null, onClick: (() -> Unit)? = null) =
    app.superhealth.nutrition.ui.components.SettingRow(tr(title), value?.let(::tr), subtitle?.let(::tr), icon, danger, hue, onClick = onClick)

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) = app.superhealth.nutrition.ui.components.SwitchRow(tr(title), subtitle?.let(::tr), checked, onChange)

@Composable
fun RowDivider() = app.superhealth.nutrition.ui.components.RowDivider()

/** Groupe de lignes dans une carte (réglages). */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = app.superhealth.nutrition.ui.components.GroupCard(modifier, content)

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, strong: Boolean = true, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) =
    app.superhealth.nutrition.ui.components.PillButton(tr(text), modifier, strong, enabled, icon, onClick)

@Composable
fun <T> ChipChoice(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) =
    app.superhealth.nutrition.ui.components.ChipChoice(options, selected, { tr(label(it)) }, modifier, onSelect)

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null, keyboard: KeyboardType = KeyboardType.Decimal) =
    app.superhealth.nutrition.ui.components.NumberField(tr(label), value, onChange, modifier, suffix?.let(::tr), keyboard)

@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "") =
    app.superhealth.nutrition.ui.components.TextInput(tr(label), value, onChange, modifier, tr(placeholder))

@Composable
fun BigStat(value: String, label: String, modifier: Modifier = Modifier, unit: String? = null, color: Color = Mf.colors.text) =
    app.superhealth.nutrition.ui.components.BigStat(value, tr(label), modifier, unit, color)

/** Pastille de pourcentage colorée (fiche aliment). */
@Composable
fun PercentPill(pct: Int, color: Color) {
    Text("${pct}%", Modifier.clip(CircleShape).background(color.copy(alpha = 0.28f)).border(0.dp, Color.Transparent, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Mf.colors.text)
}

