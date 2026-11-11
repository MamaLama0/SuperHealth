package app.superhealth.ui.cards

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlin.math.cos
import kotlin.math.sin

internal enum class DungeonRoom(val label: String, val stone: Color, val light: Color, val moss: Color) {
    CRYPT("Crypte oubliée", Color(0xFF44434B), Color(0xFFE7BD82), Color(0xFF716653)),
    ROOTS("Remparts envahis", Color(0xFF384B46), Color(0xFFB8DEAD), Color(0xFF6D8D59)),
    EMBERS("Forges ensevelies", Color(0xFF514039), Color(0xFFF0B080), Color(0xFF82513C));

    companion object { fun floor(floor: Int) = entries[((floor - 1).coerceAtLeast(0) / 3) % entries.size] }
}

/** Stable masonry per room; generated once per size, without a texture or a running animation. */
internal fun Modifier.dungeonMasonry(room: DungeonRoom, chamber: Boolean = false) = clipToBounds().drawWithCache {
    val random = Random(71 + room.ordinal)
    val brickW = 54.dp.toPx()
    val brickH = 26.dp.toPx()
    val mortar = 2.dp.toPx()
    val archX = if (chamber) size.width * .5f else 56.dp.toPx()
    val archW = if (chamber) size.width * .25f else 38.dp.toPx()
    val floorY = size.height * .84f
    val archTop = size.height * (if (chamber) .08f else .18f)
    val arch = Path().apply {
        moveTo(archX - archW, floorY)
        lineTo(archX - archW, archTop + archW)
        cubicTo(archX - archW, archTop - archW * .3f, archX + archW, archTop - archW * .3f,
            archX + archW, archTop + archW)
        lineTo(archX + archW, floorY)
        close()
    }
    val wallSize = size
    val bricks = buildList {
        for (row in 0..(wallSize.height / brickH).toInt()) {
            for (col in -1..(wallSize.width / brickW).toInt()) {
                val x = col * brickW + (if (row % 2 == 0) 0f else brickW * .5f)
                add(Triple(Offset(x + mortar, row * brickH + mortar),
                    room.stone.copy(alpha = .55f + random.nextFloat() * .4f), random.nextFloat()))
            }
        }
    }
    val stains = List(70) { Triple(Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
        (1f + random.nextFloat() * 5f).dp.toPx(), random.nextFloat()) }
    val cracks = Path().apply {
        bricks.filterIndexed { i, _ -> i % 3 == 0 }.forEach { (origin, _, seed) ->
            val x = origin.x + brickW * (.25f + seed * .4f)
            moveTo(x, origin.y)
            lineTo(x - 5.dp.toPx(), origin.y + brickH * .35f)
            lineTo(x + 2.dp.toPx(), origin.y + brickH * .52f)
            lineTo(x - 3.dp.toPx(), origin.y + brickH * .77f)
        }
    }
    val grit = List(420) { Offset(random.nextFloat() * size.width, random.nextFloat() * size.height) }
    val roots = Path().apply {
        repeat(5) { i ->
            val x = size.width - (i * 22 + 12).dp.toPx()
            moveTo(x, 0f)
            cubicTo(x - 12.dp.toPx(), size.height * .23f, x + 8.dp.toPx(), size.height * .38f,
                x - 20.dp.toPx(), size.height * .64f)
        }
    }
    onDrawBehind {
        drawRect(Color(0xFF15191A))
        bricks.forEach { (origin, color, _) ->
            drawRoundRect(color, origin, Size(brickW - mortar * 2, brickH - mortar * 2), CornerRadius(3.dp.toPx()))
            drawLine(Color.White.copy(alpha = .08f), origin + Offset(3.dp.toPx(), 1.dp.toPx()),
                origin + Offset(brickW - 8.dp.toPx(), 1.dp.toPx()), 1.dp.toPx())
        }
        drawPath(cracks, Color.Black.copy(alpha = .4f), style = Stroke(1.dp.toPx()))
        grit.forEachIndexed { index, point ->
            drawCircle(if (index % 3 == 0) room.light.copy(alpha = .14f) else Color.Black.copy(alpha = .22f),
                (if (index % 4 == 0) .9.dp else .45.dp).toPx(), point)
        }
        stains.forEach { (point, radius, seed) ->
            drawOval(if (seed > .55f) room.moss.copy(alpha = .26f) else Color.Black.copy(alpha = .22f),
                point, Size(radius * 2.5f, radius))
        }
        drawPath(arch, Color(0xFF0D1316))
        drawPath(arch, room.stone, style = Stroke(9.dp.toPx()))
        drawPath(arch, room.light.copy(alpha = .2f), style = Stroke(1.dp.toPx()))
        // Individual voussoirs around the arch, and courses down both pillars.
        for (i in 0..8) {
            val angle = Math.PI + i * Math.PI / 8
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val center = Offset(archX, archTop + archW)
            drawLine(Color(0xFF14191A), center + direction * (archW - 4.dp.toPx()),
                center + direction * (archW + 5.dp.toPx()), 2.dp.toPx())
        }
        for (side in listOf(-1, 1)) for (i in 1..4) {
            val y = archTop + archW + i * 22.dp.toPx()
            drawLine(Color(0xFF14191A), Offset(archX + side * archW - 5.dp.toPx(), y),
                Offset(archX + side * archW + 5.dp.toPx(), y), 2.dp.toPx())
        }
        if (room == DungeonRoom.ROOTS) drawPath(roots, room.moss.copy(alpha = .55f), style = Stroke(2.dp.toPx()))
        val torch = Offset(10.dp.toPx(), size.height * .44f)
        drawCircle(Brush.radialGradient(listOf(room.light.copy(alpha = .38f), Color.Transparent),
            center = torch, radius = 32.dp.toPx()), 32.dp.toPx(), torch)
        drawLine(Color(0xFF846D52), torch + Offset(0f, 3.dp.toPx()), torch + Offset(0f, 22.dp.toPx()), 3.dp.toPx())
        drawOval(room.light, torch - Offset(3.dp.toPx(), 8.dp.toPx()), Size(6.dp.toPx(), 13.dp.toPx()))
        drawOval(Color(0xFFFFEDD0), torch - Offset(1.3.dp.toPx(), 1.dp.toPx()), Size(2.6.dp.toPx(), 6.dp.toPx()))
        drawRect(Brush.radialGradient(listOf(room.light.copy(alpha = .18f), Color.Transparent),
            center = Offset(archX, size.height * .56f), radius = size.height * .8f))
        // Keep the wall legible around the doorway and give text a quiet, dark backing.
        if (!chamber) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xB311171B), Color(0xE611171B)),
            startX = 75.dp.toPx(), endX = size.width))
        // Perspective slabs make the ground explicit and meet the creature's foot plane.
        drawRect(Brush.verticalGradient(listOf(room.stone, Color(0xFF141718)), startY = floorY, endY = size.height),
            Offset(0f, floorY), Size(size.width, size.height - floorY))
        drawLine(room.light.copy(alpha = .23f), Offset(0f, floorY), Offset(size.width, floorY), 1.dp.toPx())
        for (i in -3..3) {
            drawLine(Color.Black.copy(alpha = .42f), Offset(archX + i * 34.dp.toPx(), floorY),
                Offset(archX + i * 70.dp.toPx(), size.height), 2.dp.toPx())
        }
        drawLine(Color.Black.copy(alpha = .34f), Offset(0f, size.height * .92f), Offset(size.width, size.height * .92f), 2.dp.toPx())
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent),
            center = Offset(archX, size.height * .87f), radius = archW),
            Offset(archX - archW, size.height * .83f), Size(archW * 2, size.height * .08f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99101315))))
    }
}


internal enum class DungeonCreature(val nameFr: String, val light: Long, val modelId: String? = null) {
    SKELETONMINION("Sbire squelette", 0xFFF4D29B),
    SKELETONWARRIOR("Guerrier squelette", 0xFFE3C6EE),
    SKELETONMAGE("Mage squelette", 0xFFCB9CF2),
    SKELETONROGUE("Voleur squelette", 0xFFB6CCED),
    SKELETONGOLEM("Golem d’ossements", 0xFFFFCA88),
    NECROMANCER("Liche", 0xFFE0A9FF),
    KNIGHT("Chevalier", 0xFFF4CC8B),
    MAGE("Mage", 0xFF9BC7FF),
    RANGER("Rôdeur", 0xFFC1DC94),
    BARBARIAN("Barbare", 0xFFF2AF87),
    ROGUE("Voleur", 0xFFCEAFF1),
    GOBELIN_BERSERKER("Gobelin berserker", 0xFFBD6254, "gobelin-berserker"),
    GOBELIN_ECLAIREUR("Gobelin éclaireur", 0xFF638FAD, "gobelin-eclaireur"),
    GOBELIN_MARAUDEUR("Gobelin maraudeur", 0xFF79A54B, "gobelin-maraudeur"),
    GOBELIN_OCCULTISTE("Gobelin occultiste", 0xFF9970AD, "gobelin-occultiste"),
    GOBELIN_REMPART("Gobelin rempart", 0xFFBA8C55, "gobelin-rempart"),
    SLIME_AMBRE("Slime ambre", 0xFFBA8C55, "slime-ambre"),
    SLIME_AZUR("Slime azur", 0xFF638FAD, "slime-azur"),
    SLIME_CARMIN("Slime carmin", 0xFFBD6254, "slime-carmin"),
    SLIME_MAUDIT("Slime maudit", 0xFF9970AD, "slime-maudit"),
    SLIME_MOUSSE("Slime mousse", 0xFF79A54B, "slime-mousse"),
    TROLL_BRISEFER("Brisefer, le troll gardien", 0xFF82977A, "troll-brisefer");

    companion object {
        // Same five affinity pairs as EnemyAffinity.floor and the server.
        private val goblins = listOf(GOBELIN_OCCULTISTE, GOBELIN_MARAUDEUR, GOBELIN_REMPART, GOBELIN_ECLAIREUR, GOBELIN_BERSERKER)
        private val slimes = listOf(SLIME_MAUDIT, SLIME_MOUSSE, SLIME_AMBRE, SLIME_AZUR, SLIME_CARMIN)
        private val skeletons = listOf(SKELETONMINION, SKELETONWARRIOR, SKELETONMAGE, SKELETONROGUE)
        private val bosses = listOf(TROLL_BRISEFER, SKELETONGOLEM, NECROMANCER)
        val adventurers = listOf(KNIGHT, MAGE, RANGER, BARBARIAN, ROGUE)
        fun floor(n: Int): DungeonCreature {
            if (n % 4 == 0) return bosses[(n / 4 - 1).mod(bosses.size)]
            val index = n - 1
            return when ((index / 5).mod(3)) {
                0 -> slimes[index.mod(5)]
                1 -> goblins[index.mod(5)]
                else -> skeletons[(index - index / 4).mod(skeletons.size)]
            }
        }
        fun weekly(week: Long) = bosses[((week + 3) / 7).mod(bosses.size)]
    }
}
