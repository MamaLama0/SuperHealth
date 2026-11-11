package app.superhealth.debug

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import app.superhealth.SuperPrefs
import app.superhealth.backup.*
import app.superhealth.coach.*
import app.superhealth.meals.MealPlanner
import app.superhealth.modules.Modules
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.*
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.*
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

/** Synthetic, local-only regression probe; refuses every normal distribution package. */
class Test40ProbeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (packageName != "app.superhealth.test40") { finish(); return }
        val text = TextView(this).apply { setPadding(24, 50, 24, 24) }
        setContentView(text)
        val out = File(filesDir, "qa/test40.txt").apply { parentFile?.mkdirs(); writeText("RUNNING") }
        scope.launch {
            try {
                val day = LocalDate.now().toEpochDay()
                val repo = NutriApp.repository
                val json = Json { encodeDefaults = true }
                val content = MealPlanner.Content(system = "", user = "", slots = listOf(
                    MealPlanner.Slot(MealPlanner.Txt("Petit-déjeuner"), .25, 480), MealPlanner.Slot(MealPlanner.Txt("Déjeuner"), .35, 720),
                    MealPlanner.Slot(MealPlanner.Txt("Dîner"), .30, 1140), MealPlanner.Slot(MealPlanner.Txt("Collation"), .10, 960)))
                File(filesDir, "modules/test40-meals/planner.json").apply { parentFile?.mkdirs(); writeText(json.encodeToString(content)) }
                File(filesDir, "modules/installed.json").writeText(json.encodeToString(listOf(Modules.Installed("test40-meals", "meals", "Repas de test", "1", "local", false))))
                Modules.init(this@Test40ProbeActivity); MealPlanner.invalidate()
                val ingredient = MealPlanner.Ingredient("Aliment de test", 100.0, 100.0, 10.0, 2.0, 15.0, microsJson = "{\"iron\":2.5}")
                val meal = MealPlanner.Meal(1, "Repas de test", ingredients = listOf(ingredient, ingredient.copy(name = "Second aliment")))
                val plans = (0L..11L).associate { (day + it) to MealPlanner.Plan(day + it, "foods", listOf(meal)) }
                File(filesDir, "meals/plans.json").writeText(json.encodeToString(plans))
                MealPlanner.init(this@Test40ProbeActivity)
                check(MealPlanner.copyDay(day, day + 12) == 1)
                check(MealPlanner.copyDay(day, day + 12) == 0)
                check(MealPlanner.plans.value.size == 13)
                MealPlanner.init(this@Test40ProbeActivity)
                check(MealPlanner.plans.value.size == 13 && day in MealPlanner.plans.value)
                coroutineScope { repeat(2) { launch { MealPlanner.log(day, meal, 720) } } }
                var entries = repo.entries.rangeFlow(day, day).first()
                check(entries.size == 2) { "Double-tap duplicated entries" }
                check(entries.all { Micros.parse(it.microsJson)["iron"] == 2.5 })
                repo.entries.delete(entries.first().id)
                entries = repo.entries.rangeFlow(day, day).first()
                MealPlanner.reconcile(day, entries)
                check(MealPlanner.plans.value.getValue(day).meals.single().partiallyLogged)
                MealPlanner.log(day, meal, 720)
                check(repo.entries.rangeFlow(day, day).first().size == 1)
                MealPlanner.unlog(day, 1)
                check(repo.entries.rangeFlow(day, day).first().isEmpty())
                MealPlanner.log(day, meal, 720)
                check(runCatching { MealPlanner.log(day + 1, meal, 720) }.isFailure)
                check(repo.entries.rangeFlow(day + 1, day + 1).first().isEmpty())
                repo.dayFlags.upsert(DayFlagEntity(day - 3, partial = true))
                repo.dayFlags.upsert(DayFlagEntity(day - 2, fasted = true))
                SuperPrefs.update { it.copy(coachHidden = Coach.Share.entries.filter { s -> s != Coach.Share.FOOD }.map { s -> s.name }.toSet()) }
                val args = buildJsonObject { put("from", LocalDate.ofEpochDay(day - 3).toString()); put("to", LocalDate.ofEpochDay(day).toString()) }
                val foodContext = CoachTools.run(this@Test40ProbeActivity, "get_food_log", args).result
                check("incomplete (user-marked" in foodContext && "fasted (user-marked" in foodContext)
                val snapshot = withTimeout(60000) { Coach.context(this@Test40ProbeActivity) }
                check("food log status" in snapshot && "incomplete (user-marked" in snapshot && "Planned meals (not consumed" in snapshot)
                SuperPrefs.update { it.copy(coachHidden = Coach.Share.entries.map { s -> s.name }.toSet()) }
                check("user chose not to share" in CoachTools.run(this@Test40ProbeActivity, "get_food_log", args).result)
                withTimeout(30000) { WorkoutApp.db.first { it != null } }
                val training = app.superhealth.training.ui.AppViewModel(application)
                training.startEmpty()
                val session = training.state.value.session!!
                training.startEmpty()
                training.startCustom(CustomWorkout("other", "Other", emptyList()))
                training.startProgramDay(Program("other", "Other", days = listOf(ProgramDay("day", "Day"))), 0, 0)
                check(training.state.value.session == session) { "Existing session replaced" }
                File(filesDir, app.superhealth.cards.EquipmentStore.SAVE_FILE).writeText(json.encodeToString(app.superhealth.cards.EquipmentStore.State(packCredits = listOf("test-credit"))))
                app.superhealth.cards.EquipmentStore.reload(this@Test40ProbeActivity)
                val before = MealPlanner.plans.value
                val bytes = Archive.build(this@Test40ProbeActivity)
                check(BackupCrypto.isEncrypted(bytes))
                val plain = BackupCrypto.decrypt(bytes, Secrets.backupCode(this@Test40ProbeActivity))
                val names = ZipInputStream(plain.inputStream()).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }
                check("meals_plans.json" in names && "arsenal.json" in names && "nutrition.json" in names)
                MealPlanner.clear(day)
                val restored = Archive.restore(this@Test40ProbeActivity, bytes)
                check("échec" !in restored)
                check(MealPlanner.plans.value == before)
                check(app.superhealth.cards.EquipmentStore.state.value.packCredits == listOf("test-credit"))
                MealPlanner.reconcile(day, repo.entries.rangeFlow(day, day).first())
                check(MealPlanner.plans.value.getValue(day).meals.single().logged)
                training.update { it.copy(session = null) }
                SuperPrefs.update { it.copy(coachHidden = emptySet()) }
                val database = WorkoutApp.db.value!!
                val lifts = listOf(app.superhealth.cards.Standards.Lift.BENCH, app.superhealth.cards.Standards.Lift.SQUAT, app.superhealth.cards.Standards.Lift.ROW)
                    .map { lift -> database.exercises.first { app.superhealth.cards.Standards.match(it)?.lift == lift } }
                val now = System.currentTimeMillis()
                val gymHistory = (0..35).flatMap { week -> (1..3).map { offset ->
                    Workout("test40-$week-$offset", "Séance de test", now - (week * 7 + offset) * 86_400_000L, 3600,
                        lifts.mapIndexed { i, exercise -> SessionExercise("e$i", exercise.id,
                            List(4) { SetLog("s$it", weight = listOf(80.0, 120.0, 90.0)[i], reps = 8, rir = 1, done = true) }) }) } }
                training.update { it.copy(history = gymHistory) }
                training.saveProgram(Program("test40-plan", "Programme de test", days = listOf(ProgramDay("day", "Séance suivante", listOf(
                    ProgramExercise("exercise", lifts.first().id, listOf(listOf(TargetSet()))))))), activate = true)
                SuperPrefs.update { it.copy(leagueEnabled = true, activityAsked = true) }
                app.superhealth.league.ProgressRepository.compute(this@Test40ProbeActivity)
                app.superhealth.cards.CardStore.refresh(this@Test40ProbeActivity)
                val athlete = app.superhealth.cards.CardStore.snapshot.value!!.now
                check(athlete.training?.prScore != null && athlete.training?.volumeScore != null)
                check(athlete.training!!.evidence!!.weeklyKg > 0)
                val character = app.superhealth.cards.CharacterStats.from(app.superhealth.league.ProgressRepository.current.value!!,
                    app.superhealth.league.ProgressRepository.selectedTracks(SuperPrefs.settings.value), athlete)
                check(character.valid() && character.strength > 10 && character.constitution > 10)
                out.writeText("PASS: 13 retained dates; copying and occupied slots; double tap; partial deletion; undo; micronutrients; future-day guard; coach flags and privacy; session protection; encrypted archive and restored meal links; gym PR/annual volume calculations and compatible character stats")
                text.text = out.readText()
            } catch (e: Throwable) {
                out.writeText("FAIL: " + e.stackTraceToString()); text.text = out.readText()
            }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
