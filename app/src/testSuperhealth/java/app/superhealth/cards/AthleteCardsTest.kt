package app.superhealth.cards

import app.superhealth.nutrition.domain.Sex
import app.superhealth.training.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.SplittableRandom

class AthleteCardsTest {
    private val day = 86_400_000L

    @Test fun packRatesMatchTheDisplayedTablesAndPityAlwaysHolds() {
        val rng = SplittableRandom(42)
        var epic = 0; var rare = 0; var three = 0; var sinceR = 0; var sinceE = 0; var worstR = 0; var gapR = 0; var worstE = 0; var gapE = 0
        val n = 400_000
        repeat(n) { k ->
            val booster = Booster.entries[k % Booster.entries.size]
            val r = PackEngine.open(rng.nextLong(), sinceR, sinceE, booster)
            assertTrue(r.pulls.size in 2..3)
            if (r.pulls.size == 3 && !r.prism) three++
            // Un booster ne donne que des fonds de son biome (les épiques duo le croisent).
            assertTrue(r.pulls.all { booster.element in it.card.elements })
            if (r.pulls.any { it.card.rarity >= Rarity.RARE }) { rare++; worstR = maxOf(worstR, gapR + 1); gapR = 0 } else gapR++
            if (r.pulls.any { it.card.rarity == Rarity.EPIQUE }) { epic++; worstE = maxOf(worstE, gapE + 1); gapE = 0 } else gapE++
            sinceR = r.sinceRare; sinceE = r.sinceEpic
        }
        assertEquals(PackEngine.BONUS_ODDS, three / n.toDouble(), 0.01)
        // Sans garantie ≈ 3 % de Rare ou mieux et 0,6 % d'épique ; garantie comprise ≈ 3,9 % et 0,8 %.
        assertEquals(0.030, PackEngine.perPack(Rarity.RARE), 0.003)
        assertTrue(PackEngine.withPity(Rarity.RARE) in 0.03..0.04)
        assertEquals(0.006, PackEngine.perPack(Rarity.EPIQUE), 0.002)
        assertEquals(PackEngine.withPity(Rarity.RARE), rare / n.toDouble(), 0.003)
        assertEquals(PackEngine.withPity(Rarity.EPIQUE), epic / n.toDouble(), 0.0015)
        assertTrue(epic / n.toDouble() in 0.001..0.01)
        assertTrue("pity Rare dépassée : $worstR", worstR <= PackEngine.RARE_PITY)
        assertTrue("pity Épique dépassée : $worstE", worstE <= PackEngine.EPIC_PITY)
    }

    @Test fun catalogHasFourBiomesAndTwoDuosPerPair() {
        assertEquals(4 * 10 + 12, Catalog.cards.size)
        assertEquals(Catalog.cards.size, Catalog.cards.map { it.name }.toSet().size)
        assertEquals("N001", Catalog.cards.first().id)
        val pairs = Catalog.ofRarity(Rarity.EPIQUE).groupingBy { setOf(it.element, it.element2!!) }.eachCount()
        assertEquals(6, pairs.size); assertTrue(pairs.values.all { it == 2 })
        // Dans un biome, aucun fond ne partage composition et palette avec un autre.
        Element.entries.forEach { e -> val own = Catalog.cards.filter { it.element == e && it.element2 == null }
            assertEquals(own.size, own.map { Triple(it.look.features, it.look.horizon, it.palette) }.toSet().size) }
        Booster.entries.forEach { b -> Rarity.entries.forEach { r -> assertTrue("$b $r", Catalog.ofBooster(b).any { it.rarity == r }) } }
        // RÉG et CAR n'ont pas de biome : elles ne se boostent pas.
        assertEquals(Stat.families.toSet(), Element.entries.map { it.stat }.toSet())
        assertNull(Element.of(Stat.REG)); assertNull(Element.of(Stat.CAR))
    }

    @Test fun duplicatesLevelUpAndBonusStaysSmall() {
        assertEquals(listOf(1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5), (1..12).map { Levels.of(it) })
        val rare = Catalog.ofRarity(Rarity.RARE).first()
        assertEquals(14, Bonus.tenths(rare, Levels.MAX))
        assertEquals(0, Bonus.tenths(Catalog.ofRarity(Rarity.COMMUNE).first(), Levels.MAX))
        assertEquals(4, Catalog.cards.count { it.element == rare.element && it.element2 == null && it.rarity == Rarity.PEU_COMMUNE })
        val stats = AthleteStats(values = mapOf(rare.element.stat.code to 80), ovr10 = 800, computedAt = 0)
        // Rare niveau 1 : 1 % de 80 = +0,8, au dixième.
        assertEquals(mapOf(rare.element.stat to 0.8), Bonus.points(stats, rare, 1))
        assertTrue(Bonus.points(stats, Catalog.defaultFor(Tier.OR), 1).isEmpty())
    }

    @Test fun strengthStandardsPlaceAnIntermediateBenchAroundSeventy() {
        val bench = Exercise("b", "Barbell bench press", metrics = listOf("w", "r"), region = "upper", primaryGroups = listOf("chest"))
        assertEquals(Standards.Lift.BENCH, Standards.match(bench)?.lift)
        val levels = Standards.thresholds(Standards.Lift.BENCH, Sex.MALE, 80.0)
        assertEquals(70.0, Standards.points(1.10, levels), 0.01)
        assertEquals(93.0, Standards.points(1.80, levels), 0.01)
        // Plus lourd : le même rapport compte davantage.
        assertTrue(Standards.points(1.10, Standards.thresholds(Standards.Lift.BENCH, Sex.MALE, 110.0)) > 70.0)
        assertEquals(73.0, Vo2Norms.points(45.4, Sex.MALE, 25), 0.01)
    }

    @Test fun statsComeFromRealSessionsAndStayHonestWhenDataIsMissing() {
        val bench = Exercise("b", "Barbell bench press", metrics = listOf("w", "r"), region = "upper", primaryGroups = listOf("chest"), secondaryGroups = listOf("triceps", "frontDelts"))
        val squat = Exercise("s", "Barbell back squat", metrics = listOf("w", "r"), region = "lower", primaryGroups = listOf("quads", "glutes"))
        val db = ExerciseDb(ExerciseDbFile("test", listOf(bench, squat), emptyList(), emptyMap()))
        val now = System.currentTimeMillis()
        fun w(daysAgo: Int, ex: Exercise, kg: Double) = Workout("w$daysAgo${ex.id}", "S", now - daysAgo * day, 3600,
            listOf(SessionExercise("e", ex.id, List(4) { SetLog("s$it", weight = kg, reps = 5, rir = 2, done = true) })))
        val history = (1..40 step 3).flatMap { listOf(w(it, bench, 85.0), w(it, squat, 115.0)) }
        val stats = AthleteStatsEngine.compute(AthleteStatsEngine.Input(history, db, 80.0, Sex.MALE, 30, now, weeklyTarget = 2))
        val pec = stats[Stat.PEC]!!; val jam = stats[Stat.JAM]!!
        // 85 kg × 5 à RIR 2 ≈ 1,3× le poids de corps au couché : entre intermédiaire et avancé.
        assertTrue("PEC $pec", pec in 72..84)
        assertTrue("JAM $jam", jam in 60..80)
        assertNull("aucune donnée de dos : pas de zéro", stats[Stat.DOS])
        assertNull("pas de cardio mesuré", stats[Stat.CAR])
        assertNotNull(stats.ovr10)
        assertEquals(Stat.JAM, stats.mostProfitable.takeIf { jam < pec } ?: Stat.JAM)
        val empty = AthleteStatsEngine.compute(AthleteStatsEngine.Input(emptyList(), db, 80.0, Sex.MALE, 30, now))
        assertNull(empty.ovr10)
    }

    @Test fun aMuscleNeedsSeveralExercisesAndSecondariesCountLess() {
        val bench = Exercise("b", "Barbell bench press", metrics = listOf("w", "r"), region = "upper", primaryGroups = listOf("chest"), secondaryGroups = listOf("triceps", "frontDelts"))
        val incline = Exercise("i", "Incline barbell bench press", metrics = listOf("w", "r"), region = "upper", primaryGroups = listOf("chest"), secondaryGroups = listOf("frontDelts"))
        val db = ExerciseDb(ExerciseDbFile("test", listOf(bench, incline), emptyList(), emptyMap()))
        val now = System.currentTimeMillis()
        fun w(daysAgo: Int, vararg ex: Pair<Exercise, Double>) = Workout("w$daysAgo", "S", now - daysAgo * day, 3600,
            ex.map { (e, kg) -> SessionExercise("e" + e.id, e.id, List(4) { SetLog("s$it", weight = kg, reps = 5, rir = 2, done = true) }) })
        fun pec(vararg ex: Pair<Exercise, Double>) = AthleteStatsEngine.compute(AthleteStatsEngine.Input((1..40 step 3).map { w(it, *ex) }, db, 80.0, Sex.MALE, 30, now, weeklyTarget = 2))
        val one = pec(bench to 85.0); val two = pec(bench to 85.0, incline to 75.0)
        assertNotNull(Standards.match(incline))
        // Un seul exercice ne suffit pas pour le plein : le deuxième fait monter PEC.
        assertTrue("PEC ${one[Stat.PEC]} → ${two[Stat.PEC]}", two[Stat.PEC]!! > one[Stat.PEC]!!)
        // Les épaules, seulement secondaires, restent sous les pectoraux.
        assertTrue((two.muscles["frontDelts"] ?: 0) < (two.muscles["chest"] ?: 0))
    }
}
