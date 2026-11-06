package app.superhealth.cards

import app.superhealth.nutrition.domain.Sex
import app.superhealth.training.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.SplittableRandom

class AthleteCardsTest {
    private val day = 86_400_000L

    @Test fun packRatesMatchTheDisplayedTablesAndGoldPacksTripleThem() {
        fun run(gold: Boolean): Triple<Double, Double, Double> {
            val rng = SplittableRandom(if (gold) 7 else 42)
            var epic = 0; var rare = 0; var duo = 0; var chroma = 0
            val n = 300_000
            repeat(n) { k ->
                val booster = Booster.entries[k % Booster.entries.size]
                val r = PackEngine.open(rng.nextLong(), gold, booster)
                assertTrue(r.pulls.size in 3..4)
                // Rien ne se cumule : un duo est toujours en finition normale.
                assertTrue(r.pulls.none { it.card.duo && it.finish != Finish.NORMALE })
                // Un booster ne donne que des fonds de son biome (les duos le croisent).
                assertTrue(r.pulls.all { booster.element in it.card.elements })
                if (r.pulls.any { it.card.rarity >= Rarity.RARE }) rare++
                if (r.pulls.any { it.card.rarity == Rarity.EPIQUE }) epic++
                if (r.pulls.any { it.card.duo }) duo++
                if (r.pulls.any { it.finish == Finish.CHROMATIQUE }) chroma++
            }
            if (!gold) assertEquals(PackEngine.chromaPerPack(), chroma / n.toDouble(), 0.003)
            return Triple(rare / n.toDouble(), epic / n.toDouble(), duo / n.toDouble())
        }
        val (rare, epic, duo) = run(false)
        assertEquals(PackEngine.perPack(Rarity.RARE), rare, 0.002)
        assertEquals(PackEngine.perPack(Rarity.EPIQUE), epic, 0.001)
        assertEquals(PackEngine.duoPerPack(), duo, 0.002)
        assertTrue(rare in 0.05..0.063); assertTrue(epic in 0.009..0.016)
        // L'animée, au bonus plus faible, sort plus souvent que la chromatique, à toutes les raretés.
        assertTrue(Rarity.entries.all { PackEngine.effectOdds(it, Finish.ANIMEE) > PackEngine.effectOdds(it, Finish.CHROMATIQUE) })
        assertEquals(PackEngine.DUO_ODDS, PackEngine.cardOdds(Rarity.RARE, duo = true), 1e-12)
        val (gRare, gEpic, _) = run(true)
        assertEquals(PackEngine.perPack(Rarity.RARE, gold = true), gRare, 0.004)
        assertEquals(PackEngine.perPack(Rarity.EPIQUE, gold = true), gEpic, 0.002)
        // Le 10ᵉ pack est doré, puis le 20ᵉ.
        assertEquals(listOf(9L, 19L), (0L until 20L).filter { PackEngine.isGold(it) })
        // La charte par carte : les parts de rareté font 100 %.
        assertEquals(1.0, Rarity.entries.sumOf { PackEngine.share(it) }, 1e-9)
        assertEquals(1.0, Rarity.entries.sumOf { PackEngine.share(it, gold = true) }, 1e-9)
        assertEquals(1, PackEngine.untilGold(9)); assertEquals(10, PackEngine.untilGold(10))
    }

    @Test fun catalogHas36BackgroundsPerBiomeAndDuosOfEveryRarity() {
        assertEquals(4 * 36 + 6 * 6, Catalog.cards.size)
        assertEquals(Catalog.cards.size, Catalog.cards.map { it.name }.toSet().size)
        assertEquals("N001", Catalog.cards.first().id); assertEquals("N180", Catalog.cards.last().id)
        val duos = Catalog.cards.filter { it.duo }
        assertEquals(36, duos.size)
        assertEquals(6, duos.groupingBy { setOf(it.element, it.element2!!) }.eachCount().size)
        Rarity.entries.forEach { r -> assertEquals(6 * listOf(2, 2, 1, 1)[r.ordinal], duos.count { it.rarity == r }) }
        Element.entries.forEach { e -> val own = Catalog.cards.filter { it.element == e && !it.duo }
            assertEquals(36, own.size)
            assertEquals(listOf(14, 11, 7, 4), Rarity.entries.map { r -> own.count { it.rarity == r } })
            // Dans un biome, aucun fond ne partage cadrage, éléments et palette avec un autre.
            assertEquals(own.size, own.map { Triple(it.look.frame, it.look.features, it.palette) }.toSet().size) }
        Booster.entries.forEach { b -> Rarity.entries.forEach { r -> listOf(true, false).forEach { d ->
            assertTrue("$b $r duo=$d", Catalog.ofBooster(b).any { it.rarity == r && it.duo == d }) } } }
        // RÉG et CAR n'ont pas de biome : elles ne se boostent pas.
        assertEquals(Stat.families.toSet(), Element.entries.map { it.stat }.toSet())
        assertNull(Element.of(Stat.REG)); assertNull(Element.of(Stat.CAR))
    }

    @Test fun fusionLevelsAndBonusFollowRarity() {
        // Fusion : 2 exemplaires pour le niveau 2, 4 pour le 3, 16 pour le 4, 256 pour le 5 ; +10 % d'effet par niveau.
        assertEquals(listOf(1, 2, 2, 3, 3, 4, 4, 5), listOf(1, 2, 3, 4, 15, 16, 255, 256).map { Levels.of(it) })
        assertEquals(listOf(1, 2, 12, 240, null), (1..5).map { Levels.cost(it) })
        val single = { r: Rarity -> Catalog.cards.first { it.rarity == r && !it.duo } }
        assertEquals(listOf(50, 100, 200, 400), Rarity.entries.map { Bonus.hundredths(single(it), 1) })
        assertEquals(280, Bonus.hundredths(single(Rarity.RARE), Levels.MAX))
        assertEquals(55, Bonus.hundredths(single(Rarity.COMMUNE), 2))
        // Chaque effet augmente le bonus : brillante × 1,1, animée × 1,25, chromatique × 1,5.
        assertEquals(listOf(200, 220, 250, 300), Finish.entries.map { Bonus.hundredths(single(Rarity.RARE), 1, it) })
        assertEquals(listOf(75, 150, 300, 600), Rarity.entries.map { Bonus.hundredths(single(it), 1, Finish.CHROMATIQUE) })
        assertEquals("+0,75 % " + single(Rarity.COMMUNE).element.stat.code, Bonus.label(Pull(single(Rarity.COMMUNE), Finish.CHROMATIQUE), 1))
        val rare = single(Rarity.RARE)
        val stats = AthleteStats(values = Stat.families.associate { it.code to 80 }, ovr10 = 800, computedAt = 0)
        // Rare niveau 1 : 2 % de 80 = +1,6, au dixième ; un duo commune renforce deux stats de 0,4.
        assertEquals(mapOf(rare.element.stat to 1.6), Bonus.points(stats, rare, 1))
        val duo = Catalog.cards.first { it.duo && it.rarity == Rarity.COMMUNE }
        assertEquals(mapOf(duo.element.stat to 0.4, duo.element2!!.stat to 0.4), Bonus.points(stats, duo, 1))
        assertTrue(Bonus.points(stats, Catalog.defaultFor(Tier.OR), 1).isEmpty())
        assertEquals(mapOf(rare.element.stat to 2.4), Bonus.points(stats, rare, 1, Finish.CHROMATIQUE))
    }

    @Test fun sharedCardsRoundTripThroughAPlainTextPost() {
        val pull = Pull(Catalog.cards.first { it.duo && it.rarity == Rarity.EPIQUE }, Finish.CHROMATIQUE)
        val draft = CardShare.draft(pull, 3, CardShare.Event.ATHLETE, ovr = 87)
        assertEquals("TEXT", draft.kind)
        assertEquals(CardShare.Shared(pull, 3, 87), CardShare.parse(draft.detail))
        assertFalse(CardShare.caption(draft.detail).contains("Fond N"))
        assertNull(CardShare.parse("Fond N999 · NORMALE · Niv. 2"))
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

    @Test fun commonVariantsOfTheDatabaseAreRecognised() {
        fun lift(n: String) = Standards.match(Exercise(n, n))?.lift
        assertEquals(Standards.Lift.PULLUP, lift("Overhand grip pull-up"))
        assertEquals(Standards.Lift.PULLDOWN, lift("Wide grip cable lat pulldown"))
        assertEquals(Standards.Lift.ROW, lift("Bent-over barbell row"))
        assertEquals(Standards.Lift.ROW, lift("Seated dual handle cable row (elbows-in)"))
        assertEquals(Standards.Lift.LEG_PRESS, lift("45° leg press"))
        assertEquals(Standards.Lift.BENCH, lift("Pin-loaded machine chest press"))
        assertEquals(Standards.Lift.BENCH, lift("45° incline dumbbell press"))
        assertEquals(Standards.Lift.OHP, lift("Pin-loaded machine shoulder press"))
        assertEquals(Standards.Lift.TRICEPS, lift("Cable rope triceps pushdown"))
        assertEquals(Standards.Lift.DIP, lift("Bodyweight chest dip"))
        assertNull(lift("Band-assisted chin-up"))
        // Charte de compatibilité : rattachés à un mouvement de référence, mais avec un poids réduit.
        fun conf(n: String) = Standards.match(Exercise(n, n))?.confidence
        assertEquals(1.0, conf("Overhand grip pull-up")!!, 0.0)
        // Classiques à repères propres : poids plein.
        assertEquals(Standards.Lift.LEG_EXTENSION, lift("Leg extension")); assertEquals(1.0, conf("Leg extension")!!, 0.0)
        assertEquals(Standards.Lift.LEG_CURL, lift("Seated leg curl"))
        assertEquals(Standards.Lift.CALF_RAISE, lift("Standing machine calf raise"))
        assertEquals(Standards.Lift.SHRUG, lift("Barbell shrug"))
        assertEquals(Standards.Lift.FLY, lift("Pec deck machine fly"))
        assertEquals(Standards.Lift.LUNGE, lift("Dumbbell Bulgarian split squat"))
        assertEquals(Standards.Lift.CURL, lift("Dumbbell hammer curl")); assertEquals(1.0, conf("Dumbbell hammer curl")!!, 0.0)
        assertEquals(Standards.Lift.LATERAL_RAISE, lift("Dumbbell lateral raise")); assertEquals(1.0, conf("Dumbbell lateral raise")!!, 0.0)
        assertEquals(Standards.Lift.OHP, lift("Barbell upright row")); assertTrue(conf("Barbell upright row")!! < 1.0)
        assertNull(lift("Bodyweight squat"))
        // Un bras sur machine : la moitié de la charge.
        assertEquals(0.5, Standards.match(Exercise("x", "Single arm cable lat pulldown"))!!.coef, 0.001)
        // Un tirage vertical se note à la charge de la machine, pas au poids du corps.
        assertFalse(Standards.Lift.PULLDOWN.bodyweightLoad)
    }

    @Test fun machinesAndIsolationGiveFairStatsAndHeavyLoadsStillWin() {
        val press = Exercise("p", "Barbell bench press", metrics = listOf("w", "r"), region = "upper", primaryGroups = listOf("chest"), secondaryGroups = listOf("triceps"))
        val incline = Exercise("i", "Incline barbell bench press", metrics = listOf("w", "r"), region = "upper", primaryGroups = listOf("chest"))
        val fly = Exercise("f", "Pec deck machine fly", metrics = listOf("w", "r"), region = "upper", primaryGroups = listOf("chest"))
        val machine = Exercise("m", "Pin-loaded machine chest press", metrics = listOf("w", "r"), region = "upper", primaryGroups = listOf("chest"))
        val db = ExerciseDb(ExerciseDbFile("test", listOf(press, incline, fly, machine), emptyList(), emptyMap()))
        val now = System.currentTimeMillis()
        fun pec(vararg ex: Pair<Exercise, Double>) = AthleteStatsEngine.compute(AthleteStatsEngine.Input((1..40 step 3).map { d ->
            Workout("w$d", "S", now - d * day, 3600, ex.map { (e, kg) -> SessionExercise("e" + e.id, e.id, List(4) { SetLog("s$it", weight = kg, reps = 8, rir = 2, done = true) }) }) },
            db, 80.0, Sex.MALE, 30, now, weeklyTarget = 2))[Stat.PEC]!!
        val barbell = pec(press to 80.0, incline to 66.0)
        val machines = pec(machine to 72.0, fly to 50.0)
        val heavy = pec(machine to 100.0, fly to 70.0)
        assertTrue("machines $machines vs barres $barbell", machines in barbell - 6..barbell)
        assertTrue("lourd sur machine $heavy vs barres $barbell", heavy > barbell)
    }

    @Test fun aRecentAccountIsNotPenalisedForWeeksBeforeItsFirstSession() {
        val bench = Exercise("b", "Barbell bench press", metrics = listOf("w", "r"), region = "upper", primaryGroups = listOf("chest"))
        val db = ExerciseDb(ExerciseDbFile("test", listOf(bench), emptyList(), emptyMap()))
        val now = System.currentTimeMillis()
        fun w(daysAgo: Int) = Workout("w$daysAgo", "S", now - daysAgo * day, 3600,
            listOf(SessionExercise("e", bench.id, List(4) { SetLog("s$it", weight = 100.0, reps = 5, rir = 2, done = true) })))
        // Trois séances par semaine depuis deux semaines seulement, contre la même cadence sur six semaines.
        val fresh = AthleteStatsEngine.compute(AthleteStatsEngine.Input((1..14 step 2).map(::w), db, 80.0, Sex.MALE, 30, now))
        val long = AthleteStatsEngine.compute(AthleteStatsEngine.Input((1..42 step 2).map(::w), db, 80.0, Sex.MALE, 30, now))
        assertTrue("${fresh[Stat.PEC]} vs ${long[Stat.PEC]}", kotlin.math.abs(fresh[Stat.PEC]!! - long[Stat.PEC]!!) <= 1)
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

class FusionStateTest {
    @Test fun spareCopiesKeepOneOfEachFinish() {
        val c = Catalog.cards.first()
        val st = CardStore.State(owned = mapOf("${c.id}:NORMALE" to 3, "${c.id}:BRILLANTE" to 1))
        assertEquals(2, st.spare(c)); assertEquals(1, st.level(c)); assertTrue(!st.canFuse(c)); assertTrue("il faut aussi 20 éclats", st.copy(eclats = 20).canFuse(c)); assertEquals(20, st.fusePrice(c))
        val fused = st.copy(owned = mapOf("${c.id}:NORMALE" to 2, "${c.id}:BRILLANTE" to 1), merged = mapOf(c.id to 1))
        assertEquals(2, fused.level(c)); assertEquals(2, fused.fuseCost(c)); assertTrue(!fused.canFuse(c))
    }
}
