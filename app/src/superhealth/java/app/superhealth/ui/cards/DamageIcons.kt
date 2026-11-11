package app.superhealth.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import app.superhealth.cards.DamageType
import app.superhealth.ui.tr

// Same four paths as RelicCuration.types: consistent on cards, collection and dungeon.
private val damagePaths = mapOf(
    DamageType.MELEE to "M4 20L8 16M5 13L11 19M8 15L17 4L21 3L20 7L10 17M4 18L6 20",
    DamageType.RANGED to "M7 3Q22 12 7 21L12 12ZM3 12H21M17 8L21 12L17 16",
    DamageType.ARCANIST to "M12 2L19 12L12 22L5 12ZM12 6V18M8 12H16M2 4V8M0 6H4M22 16V20M20 18H24",
    DamageType.DIVINE to "M12 7A5 5 0 1 1 11.99 7M12 1V4M12 20V23M1 12H4M20 12H23M4 4L6 6M18 18L20 20M4 20L6 18M18 6L20 4",
)
private val damageVectors = damagePaths.mapValues { (type, path) ->
    ImageVector.Builder(type.name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = null, stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
}

@Composable
internal fun DamageIcon(type: DamageType, modifier: Modifier = Modifier.size(24.dp)) {
    Icon(damageVectors.getValue(type), contentDescription = tr(type.label), modifier = modifier, tint = Color(type.tint))
}

@Composable
internal fun DamageChip(type: DamageType) {
    Box(Modifier.clip(CircleShape).background(Color(type.tint).copy(alpha = .10f)).padding(8.dp)) { DamageIcon(type) }
}

@Composable
internal fun DamageChips(types: List<DamageType>) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { types.forEach { DamageChip(it) } }
}
