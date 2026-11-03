package app.superhealth.hiking

import org.junit.Assert.*
import org.junit.Test

class HikingTest {
    @Test fun gpxRoundtripPreservesGapsAltitudeAndNames() {
        val points = listOf(HikePoint(48.0,2.0,100.0,1700000000000,1),HikePoint(48.001,2.0,110.0,segment=1),
            HikePoint(49.0,3.0,200.0,segment=2),HikePoint(49.001,3.0,220.0,segment=2))
        val route = HikeRoute("test","Trail & <river>",points=points)
        val imported = HikeGeometry.parseGpx(HikeGeometry.exportGpx(route))
        assertEquals(route.name,imported.name)
        assertEquals(points,imported.points)
        assertEquals(222.39,HikeGeometry.length(imported.points),.5)
        assertEquals(30.0,HikeGeometry.ascent(imported.points)!!,.001)
    }
    @Test fun gpxRejectsEntitiesInvalidCoordinatesAndEmptyTracks() {
        listOf("<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><gpx/>",
            "<gpx><trk><trkseg><trkpt lat='NaN' lon='2'/><trkpt lat='48' lon='2'/></trkseg></trk></gpx>",
            "<gpx><wpt lat='48' lon='2'/></gpx>").forEach {
            assertTrue(runCatching { HikeGeometry.parseGpx(it) }.isFailure)
        }
    }
    @Test fun nearestProjectionFindsProgressOnSegmentInsteadOfNearestVertex() {
        val points=listOf(HikePoint(48.0,2.0),HikePoint(48.01,2.0))
        val pos=HikeGeometry.position(points,HikePoint(48.005,2.001))!!
        assertEquals(556.0,pos.progress,2.0)
        assertEquals(74.4,pos.offset,2.0)
        assertEquals(pos.progress,pos.remaining,2.0)
    }
    @Test fun gapsNeverCreateAFictitiousTrailAndBrokenOsmRelationsAreExcluded() {
        val a=HikePoint(48.0,2.0);val b=HikePoint(48.001,2.0);val c=HikePoint(48.002,2.0)
        assertEquals(listOf(c,b,a),Hiking.stitch(mutableListOf(listOf(b,a),listOf(b,c))))
        assertNull(Hiking.stitch(mutableListOf(listOf(a,b),listOf(HikePoint(49.0,3.0),HikePoint(49.1,3.0)))))
        assertNull(HikeGeometry.position(listOf(a.copy(segment=1),b.copy(segment=2)),a))
    }
    @Test fun elapsedTimeStopsDuringPauseAndAfterProcessDeath() {
        val s=Hiking.Session(started=1000,seconds=60,resumed=10000)
        Hiking.tracking.value=true
        assertEquals(65,Hiking.elapsed(s,15000))
        assertEquals(60,Hiking.elapsed(s.copy(paused=true),15000))
        Hiking.tracking.value=false
        assertEquals(60,Hiking.elapsed(s,15000))
    }
    @Test fun gpsSamplesDoNotRoundAwayTimeAndCheckpointsPreserveTheActiveInterval() {
        val route=HikeRoute("one","Route",description="Long notes",points=listOf(HikePoint(48.0,2.0),HikePoint(48.01,2.0)),directions=listOf("Step"))
        val start=Hiking.Session(route=route,started=1000,resumed=1000)
        Hiking.tracking.value=true
        try {
            val sampled=start.copy(points=listOf(HikePoint(48.0,2.0)))
            assertEquals(30,Hiking.elapsed(sampled,31900))
            val saved=Hiking.checkpoint(sampled,31900)
            assertEquals(30,saved.seconds)
            assertEquals(sampled.points,saved.points)
            assertTrue(saved.route!!.directions.isEmpty())
            assertTrue(saved.route!!.description.isEmpty())
            assertEquals(30,Hiking.elapsed(saved.copy(paused=true),60000))
            Hiking.tracking.value=false
            assertEquals(30,Hiking.elapsed(saved,60000))
            Hiking.tracking.value=true
            assertEquals(40,Hiking.elapsed(saved.copy(resumed=60000),70000))
        } finally { Hiking.tracking.value=false }
    }
}
