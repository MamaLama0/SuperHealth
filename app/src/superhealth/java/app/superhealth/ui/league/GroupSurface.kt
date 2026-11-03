package app.superhealth.ui.league

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.CardTone
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.dashCardBackground

/** Uses the exact card treatment and contrast rules selected for the dashboard. */
@Composable
internal fun GroupSurface(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp), hue: Color = Mf.colors.accent, content: @Composable ColumnScope.() -> Unit) {
    CardTone(hue) {
        Column(modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).dashCardBackground(hue)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding), content = content)
    }
}

/** Posts stay calm and readable regardless of the dashboard's colored card variant. */
@Composable
internal fun PostSurface(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Mf.colors.card)
        .border(1.dp, Mf.colors.divider.copy(alpha = .55f), shape).padding(16.dp), content = content)
}
