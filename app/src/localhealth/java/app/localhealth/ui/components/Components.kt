package app.localhealth.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localhealth.ui.theme.ColorFamily
import nodomain.freeyourgadget.gadgetbridge.R

/** Animated 0..1 progress, replayed whenever [target] changes. */
@Composable
fun animatedProgress(target: Float): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing)) }
    return anim.value
}

@Composable
fun GsIcon(@DrawableRes res: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp, description: String? = null) {
    Icon(painterResource(res), description, modifier.size(size), tint = tint)
}

/**
 * Google Health's "today" stat pill: a 71dp rounded card with a tall icon pill on the left,
 * a title and either a large value or a subtitle.
 */
@Composable
fun PillStat(
    @DrawableRes icon: Int,
    title: String,
    family: ColorFamily,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    iconTint: Color = family.onContainer,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .height(71.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(family.container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(32.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(family.containerVariant),
            contentAlignment = Alignment.Center,
        ) {
            GsIcon(icon, iconTint, size = 22.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = family.onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) {
                Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
                    color = family.onContainer, maxLines = 1)
            } else if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = family.onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Placeholder pill used to fill an incomplete carousel page. */
@Composable
fun EmptyPill(modifier: Modifier = Modifier) {
    Box(modifier.height(71.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

/** Full-width 48dp tonal pill button ("Enregistrer", "Commencer"). */
@Composable
fun TonalWideButton(text: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        GsIcon(icon, MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
    }
}

/** Small tonal pill button ("Modifier"). */
@Composable
fun TonalChipButton(text: String, @DrawableRes icon: Int?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        contentPadding = PaddingValues(start = 16.dp, end = 20.dp),
    ) {
        if (icon != null) {
            GsIcon(icon, MaterialTheme.colorScheme.onSecondaryContainer, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
    }
}

@Composable
fun OutlinedWideButton(text: String, @DrawableRes icon: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(59.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        if (icon != null) {
            GsIcon(icon, MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal))
    }
}

/** 48dp round grey button (the pencil under the today buttons, period navigation…). */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    description: String?,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    mirrored: Boolean = false,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GsIcon(
            icon,
            if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = if (mirrored) Modifier.graphicsLayer(scaleX = -1f) else Modifier,
            description = description,
        )
    }
}

/** Carousel page indicator: elongated dark pill for the current page, grey dots otherwise. */
@Composable
fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 26.dp else 10.dp, label = "dot")
            Box(
                Modifier.width(w).height(10.dp).clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            )
        }
    }
}

/** Large section heading ("Statistiques clés", "Stats importantes"…). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 8.dp, end = 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** Medium list heading ("Appareils", "Applis et services"…). */
@Composable
fun ListHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** Shape of a row inside a Google Health grouped list: large outer corners, small inner ones. */
fun groupedShape(index: Int, count: Int, outer: Dp = 28.dp, inner: Dp = 4.dp): Shape = RoundedCornerShape(
    topStart = if (index == 0) outer else inner,
    topEnd = if (index == 0) outer else inner,
    bottomStart = if (index == count - 1) outer else inner,
    bottomEnd = if (index == count - 1) outer else inner,
)

@Composable
fun GroupedItem(
    index: Int,
    count: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = if (index < count - 1) 2.dp else 0.dp),
        shape = groupedShape(index, count),
        color = container,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Simple grouped-list row: icon, title and optional subtitle. */
@Composable
fun GroupedRow(
    index: Int,
    count: Int,
    @DrawableRes icon: Int?,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    GroupedItem(index, count, onClick, container = container) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        } else if (icon != null) {
            GsIcon(icon, iconTint)
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Stats importantes" category tile: icon, title, coloured status line. */
@Composable
fun CategoryTile(
    @DrawableRes icon: Int,
    title: String,
    status: String,
    statusColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            GsIcon(icon, iconTint)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onSurface)
                Text(status, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    color = statusColor)
            }
        }
    }
}

/** Two items per row with Google Health's 8dp gutter. */
@Composable
fun <T> TwoColumnGrid(items: List<T>, modifier: Modifier = Modifier, gap: Dp = 8.dp, cell: @Composable (T, Modifier) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Top app bar used by every secondary screen: back arrow, title, optional actions. */
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            GsIcon(R.drawable.gs_arrow_back_vd_theme_24, MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** Round avatar with the user's initial and Google's blue ring. */
@Composable
fun Avatar(initial: String, onClick: () -> Unit, size: Dp = 40.dp) {
    Box(
        Modifier.size(size + 8.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size + 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)))
        Box(Modifier.size(size + 1.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
        Box(
            Modifier.size(size - 2.dp).clip(CircleShape).background(Color(0xFFEF6C00)),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
        }
    }
}
