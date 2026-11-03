package app.superhealth.bridge

import androidx.health.connect.client.records.*
import org.junit.Assert.*
import org.junit.Test

class HcOutdoorSyncTest {
    private val now=System.currentTimeMillis()
    private fun run(id:String="one",kind:Bridge.Kind=Bridge.Kind.RUN)=HcOutdoorSync.Outdoor(id,kind,"Course",now-1800000,now-1000,5000.0,320.0)
    @Test fun runningAndTreadmillAndHikingRetainTheirSport() {
        assertEquals(Bridge.Kind.RUN,Bridge.hcKindOf(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))
        assertEquals(Bridge.Kind.RUN,Bridge.hcKindOf(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL))
        assertEquals(Bridge.Kind.HIKE,Bridge.hcKindOf(ExerciseSessionRecord.EXERCISE_TYPE_HIKING))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_HIKING,HcOutdoorSync.type(Bridge.Kind.HIKE))
    }
    @Test fun watchRunningVariantsUseTheHealthConnectRunningTypes() {
        listOf("RUNNING","TRAIL_RUN","ULTRA_RUN","TRACK_RUN","OUTDOOR_RUNNING").forEach { name ->
            val kind=java.lang.Enum.valueOf(nodomain.freeyourgadget.gadgetbridge.model.ActivityKind::class.java,name)
            assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers.WorkoutSyncerUtils.mapActivityKindToExerciseType(kind))
        }
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
            nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers.WorkoutSyncerUtils.mapActivityKindToExerciseType(nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.TREADMILL))
    }
    @Test fun exportUsesStableIdsSeparateMetricsAndCorrectTimes() {
        val a=HcOutdoorSync.records(listOf(run()),0,now,true,true)
        val b=HcOutdoorSync.records(listOf(run()),0,now+1000,true,true)
        assertEquals(3,a.size)
        assertEquals(a.map{it.metadata.clientRecordId},b.map{it.metadata.clientRecordId})
        val session=a.filterIsInstance<ExerciseSessionRecord>().single()
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,session.exerciseType)
        assertEquals(run().start,session.startTime.toEpochMilli())
        assertEquals(5000.0,a.filterIsInstance<DistanceRecord>().single().distance.inMeters,0.0)
        assertEquals(320.0,a.filterIsInstance<ActiveCaloriesBurnedRecord>().single().energy.inKilocalories,0.0)
    }
    @Test fun exportHonorsPartialPermissionsWindowAndInvalidMeasurements() {
        val items=listOf(run(),run("old").copy(start=1,end=2),run("invalid").copy(end=0))
        val records=HcOutdoorSync.records(items,now-3600000,now,false,false)
        assertEquals(1,records.size)
        assertTrue(records.single() is ExerciseSessionRecord)
        assertEquals(1,HcOutdoorSync.records(listOf(run().copy(distance=Double.NaN,kcal=-1.0)),0,now,true,true).size)
        assertTrue(HcOutdoorSync.records(listOf(run().copy(end=now+1000)),0,now,true,true).isEmpty())
    }
    @Test fun deduplicationKeepsDifferentSportsAndIncidentalOverlaps() {
        assertTrue(HcOutdoorSync.duplicate(Bridge.Kind.RUN,0,10000,Bridge.Kind.RUN,100,10100))
        assertFalse(HcOutdoorSync.duplicate(Bridge.Kind.STRENGTH,0,10000,Bridge.Kind.RUN,0,10000))
        assertFalse(HcOutdoorSync.duplicate(Bridge.Kind.RUN,0,10000,Bridge.Kind.RUN,9999,20000))
        assertFalse(HcOutdoorSync.duplicate(Bridge.Kind.RUN,0,0,Bridge.Kind.RUN,0,10000))
    }
}
