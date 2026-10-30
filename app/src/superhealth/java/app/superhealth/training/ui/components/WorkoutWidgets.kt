package app.superhealth.training.ui.components

import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import app.superhealth.training.domain.Exercise
import app.superhealth.training.domain.MuscleGroup
import app.superhealth.training.domain.Sex
import app.superhealth.training.ui.Assets
import app.superhealth.training.ui.AsyncAsset
import app.superhealth.ui.theme.BodyGrey
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.MuscleIdle
import app.superhealth.ui.theme.WorkoutBlue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable private data class BodyPath(val id: String = "", val d: String = "", val fill: String? = null)
@Serializable private data class BodyFile(val w: Float, val h: Float, val paths: List<BodyPath>)
private class Body(val w: Float, val h: Float, val parts: List<Pair<String, Path>>)

private val bodyCache = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<Body>>()
private val json = Json { ignoreUnknownKeys = true }

private fun body(name: String): Body? = bodyCache.getOrPut(name) {
    java.util.Optional.ofNullable(Assets.text("body/${name}.json")?.let { t ->
        val f = json.decodeFromString(BodyFile.serializer(), t)
        Body(f.w, f.h, f.paths.mapNotNull { p -> runCatching { p.id to PathParser.createPathFromPathData(p.d).asComposePath() }.getOrNull() })
    })
}.orElse(null)

/** Lit à l'avance les silhouettes (hors du fil principal) : l'onglet Activités s'ouvre sans à-coup. */
fun preloadBodies(sex: Sex) {
    val prefix = if (sex == Sex.FEMALE) "avatar_female" else "avatar_male"
    body("${prefix}_front"); body("${prefix}_back")
}

/**
 * Silhouettes avant / arrière (assets/body de MacroFactor Workouts) : un tracé par groupe musculaire, coloré selon
 * l'intensité 0..1 (gris #EEEEEE → bleu #276EF1), comme l'écran « Workout Complete ».
 */
@Composable
fun BodyMap(intensity: Map<String, Float>, sex: Sex, modifier: Modifier = Modifier, color: Color = WorkoutBlue) {
    val prefix = if (sex == Sex.FEMALE) "avatar_female" else "avatar_male"
    val front = remember(prefix) { body("${prefix}_front") }
    val back = remember(prefix) { body("${prefix}_back") }
    val idle = if (Mf.colors.dark) Color(0xFF3A3A3A) else MuscleIdle
    val bodyC = if (Mf.colors.dark) Color(0xFF2A2A2A) else BodyGrey
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(front, back).forEach { b ->
            if (b != null) Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val s = minOf(size.width / b.w, size.height / b.h)
                translate((size.width - b.w * s) / 2, (size.height - b.h * s) / 2) {
                    scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                        b.parts.forEach { (id, path) ->
                            val c = when (id) {
                                "body" -> bodyC
                                else -> intensity[id]?.takeIf { it > 0f }?.let { lerp(idle, color, 0.25f + 0.75f * it.coerceIn(0f, 1f)) } ?: idle
                            }
                            drawPath(path, c)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Corps « rayons X » d'une séance : silhouette neutre, muscles du jour en dégradé de [hue], en gris pour une séance faite
 * ([hue] null). Face ou dos selon les muscles visés, cadré sur le haut, le bas ou le corps entier.
 */
@Composable
fun XRayBody(targets: Set<MuscleGroup>, sex: Sex, hue: Color?, modifier: Modifier = Modifier) {
    val prefix = if (sex == Sex.FEMALE) "avatar_female" else "avatar_male"
    val front = remember(prefix) { body("${prefix}_front") }
    val back = remember(prefix) { body("${prefix}_back") }
    val keys = targets.map { it.key }.toSet()
    val b = (if ((back?.parts?.count { it.first in keys } ?: 0) > (front?.parts?.count { it.first in keys } ?: 0)) back else front) ?: return
    val (from, to) = when {
        targets.isNotEmpty() && targets.all { it.upper } -> 0.06f to 0.54f
        targets.isNotEmpty() && targets.none { it.upper } -> 0.44f to 1f
        else -> 0f to 1f
    }
    val dark = Mf.colors.dark
    val base = SolidColor(if (dark) Color(0xFF2A2D34) else Color(0xFFD7DAE0))
    val idle = SolidColor(if (dark) Color(0xFF3A3E47) else Color(0xFFC4C8D0))
    val done = SolidColor(Mf.colors.textSecondary)
    Canvas(modifier) {
        val y0 = b.h * from; val hh = b.h * (to - from)
        val s = minOf(size.width / b.w, size.height / hh)
        val hot = if (hue == null) done else app.superhealth.ui.theme.dataBrush(hue, Offset(0f, y0 + hh), Offset(0f, y0))
        clipRect {
            translate((size.width - b.w * s) / 2, (size.height - hh * s) / 2 - y0 * s) {
                scale(s, s, pivot = Offset.Zero) {
                    b.parts.forEach { (id, path) -> drawPath(path, when { id == "body" -> base; id in keys -> hot; else -> idle }) }
                }
            }
        }
    }
}

/** Vignette d'exercice : photo « preferred » de l'APK, sinon initiales. */
@Composable
fun ExerciseThumb(e: Exercise?, size: Dp = 48.dp, corner: Dp = 12.dp) {
    AsyncAsset(e?.thumbnail(), Modifier.size(size).clip(RoundedCornerShape(corner)), fallback = e?.name ?: "?", sample = if (size <= 64.dp) 2 else 1)
}

/** Étiquettes de groupes musculaires (MuscleGroupTagRow). */
@Composable
fun MuscleTags(e: Exercise, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        e.muscleGroups.forEach { Tag(it.label.tr(), strong = true) }
        e.secondaryMuscleGroups.filter { it !in e.muscleGroups }.take(3).forEach { Tag(it.label.tr(), strong = false) }
    }
}

@Composable
fun Tag(text: String, strong: Boolean = false) {
    Text(text, Modifier.clip(CircleShape).background(if (strong) WorkoutBlue.copy(alpha = 0.14f) else Mf.colors.chip)
        .padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium,
        color = if (strong) WorkoutBlue else Mf.colors.textSecondary, maxLines = 1)
}

/** Vignette de groupe musculaire (assets/muscle_groups). */
@Composable
fun MuscleThumb(g: MuscleGroup, sex: Sex, size: Dp = 44.dp) {
    val dir = if (sex == Sex.FEMALE) "female" else "male"
    AsyncAsset("muscles/${dir}/${g.key.replace(Regex("([A-Z])")) { "_" + it.value.lowercase() }}.webp", Modifier.size(size).clip(RoundedCornerShape(10.dp)), crop = true)
}

/** Rendu sobre des fiches markdown de l'APK (titres ##, listes numérotées imbriquées, liens Notion retirés). */
@Composable
fun MarkdownText(md: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        md.lines().forEach { raw ->
            val line = raw.trimEnd()
            if (line.isBlank()) return@forEach
            val indent = line.length - line.trimStart().length
            val t = line.trim()
            when {
                t.startsWith("## ") -> Text(t.removePrefix("## "), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                t.startsWith("# ") -> Text(t.removePrefix("# "), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                Regex("^\\d+\\. ").containsMatchIn(t) || t.startsWith("- ") -> {
                    val marker = Regex("^(\\d+\\.|-)\\s").find(t)!!.groupValues[1]
                    Row(Modifier.padding(start = (indent / 4 * 16).dp, top = 3.dp)) {
                        Text(if (marker == "-") "•" else marker, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.width(24.dp))
                        Text(inline(t.substring(marker.length).trim()), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> Text(inline(t), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    val re = Regex("\\[([^\\]]+)]\\([^)]*\\)|\\*\\*([^*]+)\\*\\*")
    var i = 0
    re.findAll(s).forEach { m ->
        append(s.substring(i, m.range.first))
        if (m.groupValues[1].isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
        else withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[2]) }
        i = m.range.last + 1
    }
    append(s.substring(i))
}

/** Ligne titre + valeur compacte. */
@Composable
fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Spacer(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
