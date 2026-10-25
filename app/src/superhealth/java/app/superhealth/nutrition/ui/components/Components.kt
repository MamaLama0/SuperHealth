package app.superhealth.nutrition.ui.components

import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import app.superhealth.ui.theme.Base
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.ProteinColor
import kotlin.math.roundToInt

val CardShape = RoundedCornerShape(app.superhealth.ui.theme.CardRadius)

/** Carte : surface de carte (blanc en clair, #1C1C1E en sombre), grands coins, sans ombre. */
@Composable
fun MfCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(Mf.colors.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding),
        content = content,
    )
}

/**
 * Grand titre d'écran, actions rondes à droite. [onBack] : écran poussé, bouton retour rond au-dessus du titre.
 */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onBack: (() -> Unit)? = null,
                actions: @Composable RowScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth()) {
        if (onBack != null) Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            androidx.compose.material3.IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour")) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = if (onBack != null) 2.dp else 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(title), style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(tr(it), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 2.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** Bouton rond des barres de titre (bouton d'icône tonal de Material 3). */
@Composable
fun CircleAction(icon: ImageVector, label: String?, modifier: Modifier = Modifier, tint: Color = Mf.colors.text, onClick: () -> Unit) {
    Box(modifier.size(38.dp).clip(CircleShape).background(Mf.colors.card).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(20.dp), tint = tint)
    }
}

/**
 * Barre d'application d'un écran de détail, à la manière d'Android : flèche retour, titre aligné à gauche, actions à droite.
 */
@Composable
fun NavBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, backIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
           actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().height(60.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) androidx.compose.material3.IconButton(onClick = onBack) { Icon(backIcon, tr("Retour")) }
        else Spacer(Modifier.width(12.dp))
        Text(tr(title), Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleLarge, fontSize = 20.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Avatar (initiale du prénom) en haut à droite de chaque onglet : ouvre les réglages, joignables de partout. */
@Composable
fun SettingsAvatar(onClick: () -> Unit) {
    val name = remember { app.superhealth.ui.userName() }
    Box(Modifier.size(38.dp).clip(CircleShape).background(Mf.colors.card).clickable(onClickLabel = tr("Réglages"), onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text((name ?: "?").take(1).uppercase(app.superhealth.ui.Lang.locale), style = MaterialTheme.typography.titleMedium, color = Mf.colors.text)
    }
}

/** Titre de section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 2.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(text), style = MaterialTheme.typography.titleLarge, fontSize = 19.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** En-tête d'un groupe de lignes (listes de réglages), à la couleur d'accent comme les sous-titres des Réglages d'Android. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(tr(text), modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = Mf.colors.accent)
}

/** Icône d'aliment associée au nom, toujours en pixel-art (pack, sinon générée) ; aliment non reconnu : le repas générique. */
@Composable
fun FoodIcon(name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val px = remember(name) { (FoodIcons.match(name)?.let { FoodIcons.pixel(it) }) ?: FoodIcons.pixel(FoodIcons.GENERIC) }
    Box(modifier.size(size).clip(CircleShape).background(Mf.colors.chip), contentAlignment = Alignment.Center) {
        if (px != null) Canvas(Modifier.size(size * 0.76f)) { drawPixelArt(px, center, this.size.minDimension) }
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

/** Ligne de liste sobre : glyphe gris, titre, valeur grise à droite, chevron discret. */
@Composable
fun SettingRow(title: String, value: String? = null, subtitle: String? = null, icon: ImageVector? = null, danger: Boolean = false,
               hue: Color? = null, valueColor: Color? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            // glyphe seul, gris et fin : la couleur est réservée aux données ([hue] est ignoré, gardé pour les appels)
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                Icon(it, null, Modifier.size(21.dp), tint = if (danger) MaterialTheme.colorScheme.error else Mf.colors.textSecondary)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(tr(title), style = MaterialTheme.typography.bodyLarge, color = if (danger) MaterialTheme.colorScheme.error else Mf.colors.text)
            subtitle?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 1.dp)) }
        }
        value?.let { Text(tr(it), style = MaterialTheme.typography.bodyMedium, color = valueColor ?: Mf.colors.textSecondary, textAlign = TextAlign.End,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).widthIn(max = 160.dp)) }
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.padding(start = 2.dp).size(20.dp), tint = Mf.colors.textTertiary)
    }
}

/** Interrupteur de Material 3, à la couleur d'accent. [onChange] null : la ligne qui le porte gère l'appui. */
@Composable
fun Toggle(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.Switch(checked, onChange, modifier, enabled = enabled, colors = androidx.compose.material3.SwitchDefaults.colors(
        checkedTrackColor = Mf.colors.accent, checkedThumbColor = Mf.colors.onStrong, checkedBorderColor = Mf.colors.accent,
        uncheckedTrackColor = Mf.colors.chip, uncheckedThumbColor = Mf.colors.textTertiary, uncheckedBorderColor = Mf.colors.textTertiary,
    ))
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable { onChange(!checked) }.padding(start = 16.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr(title), style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 1.dp)) }
        }
        Spacer(Modifier.width(10.dp))
        Toggle(checked, null)
    }
}

/** Séparateur fin des listes groupées, aligné sur le texte. */
@Composable
fun RowDivider(inset: Dp = 16.dp) = Box(Modifier.padding(start = inset).fillMaxWidth().height(Dp.Hairline).background(Mf.colors.divider))

/** Groupe de lignes dans une carte (réglages). */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(Mf.colors.card), content = content)
}

/** Bouton capsule de Material 3 : plein (accent, texte blanc) ou tonal (fond teinté d'accent). */
@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, strong: Boolean = true, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg = if (strong) Mf.colors.strong else Mf.colors.accent.container(1.3f)
    val fg = if (strong) Mf.colors.onStrong else Mf.colors.accent.onContainer()
    Row(
        modifier.heightIn(min = 48.dp).clip(CircleShape).background(bg).graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, Modifier.size(19.dp), tint = fg); Spacer(Modifier.width(7.dp)) }
        Text(tr(text), style = MaterialTheme.typography.labelLarge, fontSize = 15.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Choix exclusif. Peu d'options courtes : boutons segmentés (la sélection glisse) ; sinon puces de filtre renvoyées à la
 * ligne (tout reste visible, quelle que soit la langue).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> ChipChoice(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val labels = options.map { tr(label(it)) }
    // segmenté : jusqu'à 4 libellés courts, ou 6 très courts (plages « 1S 1M 3M… »)
    if ((options.size in 2..4 && labels.sumOf { it.length } <= 30 && labels.all { it.length <= 12 }) ||
        (options.size in 5..6 && labels.all { it.length <= 5 })) {
        Segmented(labels, options.indexOf(selected), modifier) { onSelect(options[it]) }
        return
    }
    androidx.compose.foundation.layout.FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            val sel = o == selected
            // puce de filtre de Material 3 : contour fin, ou fond teinté et coche quand elle est choisie
            val shape = RoundedCornerShape(8.dp)
            Row(
                Modifier.height(34.dp).clip(shape).background(if (sel) Mf.colors.accent.container(1.4f) else Color.Transparent)
                    .border(1.dp, if (sel) Color.Transparent else Mf.colors.divider.copy(alpha = 1f), shape).clickable { onSelect(o) }
                    .padding(start = if (sel) 8.dp else 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sel) { Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, Modifier.size(17.dp), tint = Mf.colors.accent.onContainer()); Spacer(Modifier.width(6.dp)) }
                Text(labels[i], style = MaterialTheme.typography.labelLarge, color = if (sel) Mf.colors.accent.onContainer() else Mf.colors.text)
            }
        }
    }
}

/** Boutons segmentés : piste en capsule, segment choisi en pilule teintée d'accent. */
@Composable
fun Segmented(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val n = labels.size.coerceAtLeast(1)
    // largeur mesurée après coup (pas de BoxWithConstraints : il casse les rangées à hauteur intrinsèque du tableau de bord)
    var widthPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(modifier.fillMaxWidth().height(40.dp).clip(CircleShape).background(Mf.colors.chip).padding(3.dp)
        .onSizeChanged { widthPx = it.width }) {
        val w = with(density) { (widthPx / n).toDp() }
        val x by androidx.compose.animation.core.animateDpAsState(w * selected.coerceAtLeast(0),
            androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 700f), label = "seg")
        if (selected >= 0 && widthPx > 0) Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(Mf.colors.accent.container(1.5f)))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, l ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center) {
                    Text(l, style = MaterialTheme.typography.labelLarge, color = if (i == selected) Mf.colors.accent.onContainer() else Mf.colors.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

/** Champ numérique sobre : libellé au-dessus, fond gris arrondi, suffixe d'unité. */
@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null, keyboard: KeyboardType = KeyboardType.Decimal) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(tr(label), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.FieldRadius)).background(Mf.colors.chip).padding(horizontal = 14.dp, vertical = 12.dp),
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
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.FieldRadius)).background(Mf.colors.chip).padding(horizontal = 14.dp, vertical = 12.dp)) {
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

/**
 * État vide : grand symbole gris, titre, une phrase et, si besoin, une action.
 */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String? = null, modifier: Modifier = Modifier, hue: Color? = null,
               action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(54.dp), tint = hue ?: Mf.colors.textTertiary)
        Spacer(Modifier.height(14.dp))
        Text(tr(title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        text?.let {
            Spacer(Modifier.height(6.dp))
            Text(tr(it), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, textAlign = TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            PillButton(action, onClick = onAction)
        }
    }
}

/** Pastille de pourcentage colorée (fiche aliment). */
@Composable
fun PercentPill(pct: Int, color: Color) {
    Text("${pct}%", Modifier.clip(CircleShape).background(color.copy(alpha = 0.28f)).border(0.dp, Color.Transparent, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Mf.colors.text)
}

