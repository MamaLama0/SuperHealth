package app.superhealth.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.cards.Catalog
import app.superhealth.cards.Finish
import app.superhealth.cards.Palette
import app.superhealth.cards.Tier
import app.superhealth.ui.theme.Mf

private val arenaFoil = Catalog.defaultFor(Tier.ELITE).copy(
    palette = Palette(0xFFEAF9FF, 0xFF8064F8, 0xFF101432), seed = 9f
)
private val arenaRim = Brush.linearGradient(listOf(
    Color(0xFFC7FCFF), Color(0xFF7594FF), Color(0xFFEFA0FF), Color(0xFFFFDDBB)
))

internal fun Modifier.etchedMetal(tint: Color, engravedFrame: Boolean = false) = drawWithCache {
    val unit = size.minDimension
    val edge = 7.dp.toPx()
    val filigree = Path()
    val arm = if (engravedFrame) 15.dp.toPx() else 8.dp.toPx()
    val cut = 4.dp.toPx()
    val corners = mutableListOf<Offset>()
    for ((x, y) in listOf(1 to 1, -1 to 1, 1 to -1, -1 to -1)) {
        val ox = if (x > 0) edge else size.width - edge
        val oy = if (y > 0) edge else size.height - edge
        corners.add(Offset(ox, oy))
        filigree.moveTo(ox, oy + y * arm)
        filigree.lineTo(ox, oy + y * cut)
        filigree.lineTo(ox + x * cut, oy)
        filigree.lineTo(ox + x * arm, oy)
    }
    val grain = Path()
    val pits = Path()
    val random = kotlin.random.Random(32)
    val count = (size.width / 9.dp.toPx() * (size.height / 5.dp.toPx())).toInt().coerceIn(32, 1800)
    repeat(count) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val width = (.8f + random.nextFloat() * 5.5f).dp.toPx()
        grain.moveTo(x, y)
        grain.lineTo(x + width, y - width * .18f)
        if (it % 4 == 0) {
            pits.moveTo(x, y)
            pits.quadraticTo(x + width * .34f, y + width * .42f, x + width * .76f, y + width * .18f)
        }
    }
    val bands = Path()
    for (i in 1..5) {
        val x = size.width * (.11f + i * .16f)
        bands.moveTo(x, size.height * .06f)
        bands.lineTo(x - unit * .23f, size.height * .60f)
    }
    val reflection = Brush.linearGradient(listOf(Color.Transparent, tint.copy(alpha = .09f), Color.Transparent),
        start = Offset(0f, size.height * .73f), end = Offset(size.width, size.height * .23f))
    onDrawBehind {
        drawRect(reflection)
        drawPath(grain, tint.copy(alpha = .24f), style = Stroke(.52.dp.toPx(), cap = StrokeCap.Round))
        drawPath(pits, Color.Black.copy(alpha = .54f), style = Stroke(1.1.dp.toPx(), cap = StrokeCap.Round))
        if (engravedFrame) drawPath(bands, tint.copy(alpha = .075f), style = Stroke(1.1.dp.toPx()))
        drawPath(filigree, tint.copy(alpha = if (engravedFrame) .66f else .45f), style = Stroke(1.dp.toPx()))
        if (engravedFrame) corners.forEach { corner ->
            drawCircle(Color.Black.copy(alpha = .65f), 2.2.dp.toPx(), corner)
            drawCircle(tint.copy(alpha = .58f), 1.3.dp.toPx(), corner - Offset(.3.dp.toPx(), .3.dp.toPx()))
        }
    }
}

@Composable
internal fun CombatFoil(modifier: Modifier = Modifier) {
    val time = rememberCardTime()
    val tilt = rememberTilt()
    CardArt(arenaFoil, Finish.CHROMATIQUE, modifier, tilt = { tilt.value }, time = { time.value }, resolution = .70f)
}

@Composable
private fun CombatCards(modifier: Modifier = Modifier) {
    val corner = RoundedCornerShape(8.dp)
    Box(modifier.size(width = 61.dp, height = 59.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.offset(x = (-7).dp).size(width = 34.dp, height = 46.dp)
            .graphicsLayer { rotationZ = -16f }
            .background(Brush.linearGradient(listOf(Color(0xFF523690), Color(0xFF1F2348))), corner)
            .border(1.dp, arenaRim, corner))
        Box(Modifier.offset(x = 6.dp).size(width = 38.dp, height = 49.dp)
            .graphicsLayer { rotationZ = 12f }
            .clip(corner).background(Color(0xFF141632)).border(1.25.dp, arenaRim, corner),
            contentAlignment = Alignment.Center) {
            CombatFoil(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA3111026)))))
            Text("VS", style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black,
                shadow = Shadow(Color(0xFF101126), Offset(0f, 1f), 6f)))
        }
    }
}

@Composable
internal fun CombatShortcut(label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CombatCards()
        Text(label, style = MaterialTheme.typography.labelSmall, color = Mf.colors.text)
    }
}
