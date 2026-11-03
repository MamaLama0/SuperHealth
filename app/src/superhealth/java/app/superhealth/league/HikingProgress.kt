package app.superhealth.league

import android.content.Context
import android.util.AtomicFile
import app.superhealth.bridge.Bridge
import app.superhealth.bridge.HcOutdoorSync
import app.superhealth.hiking.HikeGeometry
import app.superhealth.hiking.Hiking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** Durable, local activity summaries for hiking rewards. No GPS coordinates enter this ledger or public totals. */
object HikingProgress {
    @Serializable data class Outing(val id:String,val start:Long,val end:Long,val seconds:Long,val meters:Double?=null,val ascent:Double?=null)
    val outings=MutableStateFlow<List<Outing>>(emptyList())
    private val json=Json { ignoreUnknownKeys=true;encodeDefaults=true }
    private fun file(ctx:Context)=AtomicFile(File(ctx.filesDir,"hiking/progress.json").apply{parentFile?.mkdirs()})
    fun load(ctx:Context) {
        outings.value=runCatching{file(ctx).openRead().bufferedReader().use{json.decodeFromString(ListSerializer(Outing.serializer()),it.readText())}}
            .getOrDefault(emptyList()).filter(::qualified).take(10000)
    }
    fun qualified(o:Outing):Boolean = o.start>0 && o.end>o.start && o.seconds>=600 && o.seconds <= (o.end-o.start)/1000+5 &&
        (o.meters?.let { it.isFinite() && it in 500.0..2e7 } ?: (o.seconds>=1800)) &&
        (o.ascent?.let { it.isFinite() && it in 0.0..100000.0 } ?: true)
    fun merge(current:List<Outing>,previous:List<Outing>,now:Long=System.currentTimeMillis()):List<Outing> =
        (current+previous).distinctBy{it.id}.filter{qualified(it) && it.end<=now+300000}.fold(emptyList<Outing>()){accepted,o ->
            if(accepted.any{HcOutdoorSync.duplicate(Bridge.Kind.HIKE,it.start,it.end,Bridge.Kind.HIKE,o.start,o.end)}) accepted else accepted+o
        }.sortedByDescending{it.start}.take(10000)
    suspend fun collect(ctx:Context,from:Long):List<Outing> {
        val library=Hiking.library.value
        val local=library.completed.map { log ->
            val route=library.routes.firstOrNull{it.id==log.route}
            Outing("local-${log.route}",log.time,log.end ?: route?.points?.mapNotNull{it.time}?.maxOrNull() ?: log.time+log.seconds*1000,
                log.seconds,log.meters,log.ascent ?: route?.let{HikeGeometry.ascent(it.points)})
        }
        val external=Bridge.activities(from).filter{it.kind==Bridge.Kind.HIKE}.map { a ->
            Outing(a.key,a.start,a.end,a.run?.activeSec?.toLong() ?: (a.end-a.start)/1000,a.distanceM,a.run?.ascentM)
        }
        val next=merge(local+external,outings.value)
        if(next!=outings.value) {
            val f=file(ctx);val output=f.startWrite()
            try{output.write(json.encodeToString(ListSerializer(Outing.serializer()),next).toByteArray());f.finishWrite(output)}
            catch(e:Exception){f.failWrite(output);throw e}
            outings.value=next
        }
        return next
    }
    fun day(o:Outing,zone:ZoneId=ZoneId.systemDefault())=Instant.ofEpochMilli(o.start).atZone(zone).toLocalDate().toEpochDay()
    fun totals(rows:List<Outing>):Map<String,Long> = mapOf("hikes" to rows.size.toLong(),
        "hikeMeters" to rows.sumOf{it.meters ?: 0.0}.toLong().coerceAtMost(1_000_000_000L),"hikeAscent" to rows.sumOf{it.ascent ?: 0.0}.toLong().coerceAtMost(1_000_000_000L))
}
