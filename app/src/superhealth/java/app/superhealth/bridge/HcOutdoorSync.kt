package app.superhealth.bridge

import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import app.superhealth.hiking.HikeGeometry
import app.superhealth.hiking.Hiking
import java.time.Instant
import java.time.ZoneId

/** Stable IDs keep repeated exports as updates. Foreign HC sessions are never re-exported. */
internal object HcOutdoorSync {
    data class Outdoor(val id: String, val kind: Bridge.Kind, val title: String, val start: Long, val end: Long,
        val distance: Double?, val kcal: Double? = null)
    fun imported(runs: List<RunImport.Run>) = runs.map { r -> Outdoor("import-${r.id}",
        runCatching { Bridge.Kind.valueOf(r.kind) }.getOrDefault(Bridge.Kind.OTHER),r.name,r.start,r.end,r.distanceM,r.kcal) }
    fun hikes(library: Hiking.Library) = library.completed.mapNotNull { log ->
        val route=library.routes.firstOrNull { it.id==log.route }
        val end=log.end ?: route?.points?.mapNotNull { it.time }?.maxOrNull() ?: return@mapNotNull null
        Outdoor("hike-${log.route}",Bridge.Kind.HIKE,route?.name ?: log.name.ifBlank { "Randonnée" },log.time,end,log.meters)
    }
    fun type(kind: Bridge.Kind) = when(kind) {
        Bridge.Kind.RUN -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        Bridge.Kind.WALK -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        Bridge.Kind.HIKE -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
        Bridge.Kind.BIKE -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        Bridge.Kind.SWIM -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER
        Bridge.Kind.YOGA -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
        Bridge.Kind.STRENGTH -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        Bridge.Kind.OTHER -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }
    fun records(items: List<Outdoor>, from: Long, version: Long, distanceGranted: Boolean, caloriesGranted: Boolean): List<Record> {
        val zone=ZoneId.systemDefault()
        return items.distinctBy { it.id }.filter { it.start>=from && it.end>it.start && it.end<=version }.flatMap { item ->
            val start=Instant.ofEpochMilli(item.start);val end=Instant.ofEpochMilli(item.end)
            val a=zone.rules.getOffset(start);val b=zone.rules.getOffset(end)
            listOfNotNull(
                ExerciseSessionRecord(startTime=start,startZoneOffset=a,endTime=end,endZoneOffset=b,exerciseType=type(item.kind),title=item.title,
                    metadata=Metadata.manualEntry(clientRecordId="outdoor-session-${item.id}",clientRecordVersion=version)),
                item.distance?.takeIf { distanceGranted && it.isFinite() && it>0 }?.let {
                    DistanceRecord(start,a,end,b,Length.meters(it),Metadata.manualEntry(clientRecordId="outdoor-distance-${item.id}",clientRecordVersion=version))
                },
                item.kcal?.takeIf { caloriesGranted && it.isFinite() && it>0 }?.let {
                    ActiveCaloriesBurnedRecord(start,a,end,b,Energy.kilocalories(it),Metadata.manualEntry(clientRecordId="outdoor-calories-${item.id}",clientRecordVersion=version))
                },
            )
        }
    }
    /** Ignore incidental overlaps and different sports. */
    fun duplicate(a: Bridge.Kind, start: Long, end: Long, b: Bridge.Kind, otherStart: Long, otherEnd: Long): Boolean {
        if(a!=b || end<=start || otherEnd<=otherStart) return false
        return (minOf(end,otherEnd)-maxOf(start,otherStart)).coerceAtLeast(0)>0.75*maxOf(end-start,otherEnd-otherStart)
    }
}
