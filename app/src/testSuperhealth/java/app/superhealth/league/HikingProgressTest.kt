package app.superhealth.league

import app.superhealth.hiking.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HikingProgressTest {
    private val now=System.currentTimeMillis()
    private fun outing(id:String="local",seconds:Long=1800,meters:Double?=2500.0)=HikingProgress.Outing(id,now-3600000,now-3600000+seconds*1000,seconds,meters,150.0)
    @Test fun rewardsRequireAnActualQualifyingOuting() {
        assertFalse(HikingProgress.qualified(outing(seconds=599)))
        assertFalse(HikingProgress.qualified(outing(meters=499.0)))
        assertFalse(HikingProgress.qualified(outing(seconds=600,meters=null)))
        assertTrue(HikingProgress.qualified(outing(seconds=1800,meters=null)))
        assertTrue(HikingProgress.qualified(outing(seconds=600,meters=500.0)))
        assertFalse(HikingProgress.qualified(outing(meters=Double.NaN)))
        assertFalse(HikingProgress.qualified(outing().copy(seconds=10000)))
        // Merely viewing or importing a planned route produces no completed outing.
        assertTrue(HikingProgress.merge(emptyList(),emptyList(),now).isEmpty())
    }
    @Test fun watchHealthConnectAndPhoneCopiesCountOnceAndHistorySurvivesTheReadWindow() {
        val recorded=outing()
        val duplicate=recorded.copy(id="health-connect",start=recorded.start+10000,end=recorded.end+10000)
        val merged=HikingProgress.merge(listOf(recorded,duplicate),emptyList(),now)
        assertEquals(listOf(recorded),merged)
        assertEquals(merged,HikingProgress.merge(emptyList(),merged,now+86400000))
        val updated=recorded.copy(meters=2700.0)
        assertEquals(listOf(updated),HikingProgress.merge(listOf(updated),merged,now))
    }
    @Test fun hikingBadgesUseRealTotalsAndCanDecorateTheSocialProfile() {
        val rows=listOf(outing(),outing("two").copy(start=now-7200000,end=now-5400000))
        val totals=HikingProgress.totals(rows)
        assertEquals(2L,totals["hikes"])
        assertEquals(5000L,totals["hikeMeters"])
        val earned=Achievements.unlocked(Progression.Summary(totals=totals))
        assertTrue("hikes_1" in earned)
        assertFalse("hikes_5" in earned)
        assertTrue("hike_ascent_100" in earned)
        assertTrue(Achievements.validDecor("hikes_1",listOf("hikes_1")))
        assertTrue(Achievements.catalog.filter{it.id.startsWith("hike")}.all{it.asset.startsWith("game/hiking/")})
    }
    @Test fun hikingQuestsAreOptInAchievableAndClaimedOnlyOnce() {
        val monday=LocalDate.of(2026,11,2).toEpochDay()
        val track=setOf(Progression.Track.HIKE);val focus=setOf(WeeklyQuests.Focus.HIKING)
        assertTrue(WeeklyQuests.update(null,monday,track,emptyMap(),focus,WeeklyQuests.Evidence()).quests.isEmpty())
        val first=WeeklyQuests.update(null,monday,track,emptyMap(),focus,WeeklyQuests.Evidence(hikingEnabled=true))
        assertEquals(WeeklyQuests.Metric.HIKE,first.quests.single().metric)
        assertEquals(1,first.quests.single().target)
        val complete=WeeklyQuests.update(first,monday,track,mapOf(WeeklyQuests.Metric.HIKE to 1),focus,WeeklyQuests.Evidence(hikingEnabled=true))
        val claimed=WeeklyQuests.claim(complete,WeeklyQuests.Metric.HIKE,monday)
        assertTrue(claimed.quests.single().claimed)
        assertEquals(claimed,WeeklyQuests.claim(claimed,WeeklyQuests.Metric.HIKE,monday))
        assertEquals(80,claimed.quests.single().reward)
    }
    @Test fun hikingXpStaysOncePerDayWithinTheExistingDailyCap() {
        val day=LocalDate.now().toEpochDay()
        val result=Progression.calculate(mapOf(day to setOf(Progression.Track.HIKE)),day,setOf(Progression.Track.HIKE),mapOf("HIKE" to 1))
        assertEquals(30L,result.summary.totalXp)
        assertEquals(1,result.summary.streaks.single().current)
        assertEquals(120L,Progression.calculate(mapOf(day to Progression.Track.entries.toSet()),day,Progression.Track.entries.toSet()).summary.totalXp)
    }
    @Test fun hikingShareCanRemoveEveryGpsCoordinateBeforePublication() {
        val points=listOf(HikePoint(45.0,6.0),HikePoint(45.01,6.01))
        val route=HikeRoute("recording-test","Test hike",points=points)
        val log=Hiking.Completed(route.id,now-3600000,2500.0,1800,150.0,now-1800000,route.name)
        val draft=SharedActivity.hikingDraft(route,log)
        assertEquals("HIKE",draft.activity!!.sport)
        assertTrue(SharedActivity.valid(draft.activity!!,now))
        assertNull(draft.shared(route=false,details=true).activity!!.route)
        assertEquals(2500.0,draft.activity!!.metres!!,.0)
    }
}
