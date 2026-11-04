package app.superhealth.social

import app.superhealth.league.GroupRecords
import app.superhealth.league.SharedActivity
import app.superhealth.nutrition.ui.num
import app.superhealth.ui.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import kotlin.math.cos

fun FriendPost.forSharing(details: Boolean, route: Boolean): FriendPost {
    val codec = Json
    val strength = workout?.let { codec.decodeFromJsonElement(SharedActivity.Strength.serializer(), it) }
    val outdoor = activity?.let { codec.decodeFromJsonElement(SharedActivity.Outdoor.serializer(), it) }
    val pr = record?.let { codec.decodeFromJsonElement(GroupRecords.Record.serializer(), it) }
    return copy(
        workout = strength?.let { codec.encodeToJsonElement(SharedActivity.Strength.serializer(), if (details) it else it.copy(exercises = emptyList())).jsonObject },
        activity = outdoor?.let { codec.encodeToJsonElement(SharedActivity.Outdoor.serializer(), (if (details) it else it.withoutDetails()).copy(route = it.route.takeIf { route })).jsonObject },
        record = pr?.let { codec.encodeToJsonElement(GroupRecords.Record.serializer(), if (details) it else it.copy(previous = null, trail = emptyList(), muscles = emptyList())).jsonObject })
}

/** The second stream has two encryption layers. Keep its exact reviewed snapshot within that budget. */
fun FriendPost.boundedForCircle():FriendPost {
    var post=this
    while(Json.encodeToString(FriendPost.serializer(),post).encodeToByteArray().size>6000) {
        val strength=post.workout?.let{Json.decodeFromJsonElement(SharedActivity.Strength.serializer(),it)}
        val outdoor=post.activity?.let{Json.decodeFromJsonElement(SharedActivity.Outdoor.serializer(),it)}
        post=when {
            strength!=null && strength.exercises.isNotEmpty()->post.copy(workout=Json.encodeToJsonElement(SharedActivity.Strength.serializer(),strength.copy(exercises=strength.exercises.dropLast(1))).jsonObject)
            outdoor?.route!=null && SharedActivity.Route.decode(outdoor.route)!!.size>12 ->post.copy(activity=Json.encodeToJsonElement(SharedActivity.Outdoor.serializer(),outdoor.copy(route=SharedActivity.Route.encode(SharedActivity.sample(SharedActivity.Route.decode(outdoor.route)!!,SharedActivity.Route.decode(outdoor.route)!!.size/2)))).jsonObject)
            outdoor!=null && (outdoor.speed.isNotEmpty() || outdoor.elevation.isNotEmpty() || outdoor.heart.isNotEmpty())->post.copy(activity=Json.encodeToJsonElement(SharedActivity.Outdoor.serializer(),outdoor.copy(speed=emptyList(),elevation=emptyList(),heart=emptyList())).jsonObject)
            post.record!=null ->post.forSharing(false,false).also{require(it!=post){"Publication trop volumineuse."}}
            else->error("Publication trop volumineuse.")
        }
    }
    return post
}

@Composable
fun FriendSnapshotView(post: FriendPost, preview: Boolean = false) {
    val strength = remember(post.workout) { post.workout?.let { Json.decodeFromJsonElement(SharedActivity.Strength.serializer(), it) } }
    val outdoor = remember(post.activity) { post.activity?.let { Json.decodeFromJsonElement(SharedActivity.Outdoor.serializer(), it) } }
    val record = remember(post.record) { post.record?.let { Json.decodeFromJsonElement(GroupRecords.Record.serializer(), it) } }
    var expanded by remember { mutableStateOf(preview) }
    strength?.let {
        Text("${it.seconds / 60} min · ${it.setCount} " + tr("séries") + " · " + num(it.volumeKg, 0) + " kg")
        if (it.exercises.isNotEmpty()) {
            if (!preview) TextButton({ expanded = !expanded }) { Text(tr(if (expanded) "Réduire" else "Voir le détail")) }
            if (expanded) it.exercises.forEach { exercise ->
                Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                Text(exercise.sets.joinToString(" / ") { set -> listOfNotNull(set.kg?.let { kg -> num(kg, 1) + " kg" }, set.reps?.let { reps -> "$reps " + tr("reps") }).joinToString(" × ") })
            }
        }
    }
    record?.let { Text(num(it.value, 1) + " " + it.unit + " · " + tr(GroupRecords.label(it))); it.previous?.let { previous -> Text(tr("Précédent record") + " · " + num(previous, 1) + " " + it.unit) } }
    outdoor?.let {
        Text(num(it.seconds / 60, 0) + " min" + (it.metres?.let { metres -> " · " + num(metres / 1000, 2) + " km" } ?: ""))
        it.ascent?.let { metres -> Text(tr("Dénivelé") + " · " + num(metres, 0) + " m") }
        it.avgHr?.let { hr -> Text(tr("Fréquence moyenne") + " · $hr bpm") }
        it.kcal?.let { kcal -> Text(num(kcal, 0) + " kcal") }
        it.route?.let { encoded ->
            val points = remember(encoded) { SharedActivity.Route.decode(encoded).orEmpty() }
            if (points.size >= 2) {
                Text(tr("Tracé GPS partagé"), style = MaterialTheme.typography.labelMedium)
                val color = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(200.dp).padding(12.dp)) {
                    val origin = points.first().second
                    val correction = cos(points.map { point -> point.first }.average() * kotlin.math.PI / 180)
                    val projected = points.map { point ->
                        val raw = point.second - origin
                        val longitude = when { raw > 180 -> raw - 360; raw < -180 -> raw + 360; else -> raw }
                        longitude * correction to -point.first
                    }
                    val x = projected.minOf { point -> point.first }; val y = projected.minOf { point -> point.second }
                    val width = (projected.maxOf { point -> point.first } - x).coerceAtLeast(1e-8)
                    val height = (projected.maxOf { point -> point.second } - y).coerceAtLeast(1e-8)
                    val scale = minOf(size.width / width, size.height / height)
                    val offsetX = (size.width - width * scale) / 2; val offsetY = (size.height - height * scale) / 2
                    val path = Path()
                    projected.forEachIndexed { index, point ->
                        val px = (offsetX + (point.first - x) * scale).toFloat(); val py = (offsetY + (point.second - y) * scale).toFloat()
                        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
                }
            }
        }
    }
}
