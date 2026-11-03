package app.superhealth.stretch

import org.junit.Assert.*
import org.junit.Test

class StretchPlanningTest {
    private fun ex(id: String, zones: List<String> = listOf("Hamstrings"), type: String = "PassiveStretch", uni: Boolean = false, gear: List<String> = emptyList(), post: Boolean = true) =
        Stretch.Ex(id, id, type, zones = zones, uni = uni, gears = gear, daily = true, pre = true, post = post)

    @Test fun generationFitsTheBudgetAndTargetsExactMusclesWithoutMissingEquipment() {
        val pool = listOf(ex("ham-a", uni = true), ex("ham-b"), ex("quad", listOf("Quads")), ex("roller", gear = listOf("MassageRoller")), ex("pre-only", post = false))
        val r = Stretch.routine(Stretch.Kind.POST, setOf(Stretch.Group.THIGHS), 1, emptySet(), day = 0,
            zones = setOf("Hamstrings"), secondsPerSide = 45, availableExercises = pool)
        assertEquals(setOf(Stretch.Group.THIGHS), r.focusGroups)
        assertEquals(setOf("Hamstrings"), r.focusZones)
        assertEquals(1, r.plannedMinutes)
        assertEquals(45, r.defaultSeconds)
        val restored = kotlinx.serialization.json.Json.decodeFromString(Stretch.Routine.serializer(),
            kotlinx.serialization.json.Json.encodeToString(Stretch.Routine.serializer(), r))
        assertEquals(r, restored)
        assertTrue(r.items.isNotEmpty())
        assertTrue(r.seconds <= 60)
        assertTrue(r.items.all { it.id in setOf("ham-a", "ham-b") })
        assertTrue(r.items.all { it.sideSeconds >= 15 })
    }
    @Test fun swapsRespectTypeZoneEquipmentAndKeepTheChosenDuration() {
        val old = ex("original", uni = true).copy(secondsPerSide = 30)
        val suitable = ex("alternative", uni = true)
        val otherType = ex("active", type = "ActiveStretch")
        val otherZone = ex("quad", zones = listOf("Quads"))
        val needsGear = ex("roller", gear = listOf("MassageRoller"))
        assertEquals(listOf(suitable), Stretch.alternatives(old, listOf(old, suitable, otherType, otherZone, needsGear), emptySet()))
        assertTrue(otherType in Stretch.alternatives(old, listOf(otherType), emptySet(), sameType = false))
        val routine = Stretch.Routine("personal", Stretch.Kind.POST, listOf(old, otherZone))
        val changed = Stretch.replace(routine, 0, suitable)
        assertEquals(30, changed.items[0].sideSeconds)
        assertEquals(60, changed.items[0].seconds)
        assertEquals(otherZone, changed.items[1])
        assertEquals("original", routine.items[0].id)
    }
    @Test fun quickSessionsAndCustomExercisesNeedNoCatalogVideo() {
        val personal = ex("custom-hands", type = "Massage").copy(secondsPerSide = 15)
        val r = Stretch.routine(Stretch.Kind.DAILY, emptySet(), 1, emptySet(), day = 0, secondsPerSide = 15, availableExercises = listOf(personal))
        assertEquals(15, r.seconds)
        assertEquals(personal.id, r.items.single().id)
    }
    @Test fun dislikedExercisesAreExcludedEvenWhenTheyAreFavorites() {
        val pool = listOf(ex("avoided"), ex("allowed"))
        val r = Stretch.routine(Stretch.Kind.DAILY, emptySet(), 5, emptySet(), day = 0,
            favorites = setOf("avoided"), availableExercises = pool, disliked = setOf("avoided"))
        assertEquals(listOf("allowed"), r.items.map { it.id })
        assertTrue(Stretch.routine(Stretch.Kind.DAILY, emptySet(), 5, emptySet(), availableExercises = pool,
            disliked = setOf("avoided", "allowed")).items.isEmpty())
    }
    @Test fun anatomyPreviewMapsMusclesInsteadOfJustBroadAreas() {
        assertEquals(setOf(app.superhealth.training.domain.MuscleGroup.HAMSTRINGS), Stretch.musclesFor(listOf("Hamstrings")))
        assertTrue(Stretch.musclesFor(listOf("Unknown")).isEmpty())
        assertEquals(3, Stretch.musclesFor(listOf("Shoulders")).size)
    }

}
