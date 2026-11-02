package app.superhealth.debug

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import app.superhealth.*
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.domain.*
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.ProgramDay
import app.superhealth.training.domain.ProgramExercise
import app.superhealth.training.domain.TargetSet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate

/** Fixtures et constats uniquement dans l'installation QA séparée. Refuse l'app personnelle, même en debug. */
class RegressionProbeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageName != "app.superhealth.qa") { finish(); return }
        val text = TextView(this).apply { textSize = 16f; setPadding(24, 50, 24, 24) }
        setContentView(text)
        scope.launch {
            try {
                when (intent.getStringExtra("op")) {
                    "seed" -> seed()
                    "socialseed" -> socialSeed()
                    "free" -> {
                        val db = WorkoutApp.db.first { it != null }!!
                        val e = db.exercises.first { it.usesWeight && it.usesReps }
                        val set = app.superhealth.training.domain.SetLog("qa-free-set", weight = 40.0, reps = 10, rir = 2, done = false)
                        val session = app.superhealth.training.domain.Session("qa-free", "Séance libre QA", System.currentTimeMillis(), listOf(app.superhealth.training.domain.SessionExercise("qa-free-ex", e.id, listOf(set))), freeForm = true)
                        WorkoutApp.store.update { it.copy(session = session) }
                    }
                    "sealed" -> {
                        val path = "/v2/cards/" + "a".repeat(64) + "/" + "b".repeat(64)
                        val raw = app.superhealth.league.League.card("QA sealed", app.superhealth.league.League.compute(this@RegressionProbeActivity), "https://qa.invalid" + path, detailed = true)
                        val key = app.superhealth.league.RelayCrypto.newKey()
                        val card = app.superhealth.league.League.parse(raw)!!
                        File(filesDir, "qa/envelope.json").writeText(app.superhealth.league.RelayCrypto.encrypt(raw, key, path, card.revision))
                        File(filesDir, "qa/relay-key.txt").writeText(key)
                        File(filesDir, "qa/card.txt").writeText(raw)
                    }
                    "unseal" -> {
                        val key = File(filesDir, "qa/in-key.txt").readText()
                        val body = File(filesDir, "qa/in-envelope.json").readText()
                        val path = "/v2/cards/" + "a".repeat(64) + "/" + "b".repeat(64)
                        val raw = app.superhealth.league.RelayCrypto.decrypt(body, key, path)
                        val card = app.superhealth.league.League.parse(raw) ?: error("Invalid received signature")
                        app.superhealth.league.League.accept(this@RegressionProbeActivity, card)
                        File(filesDir, "qa/unsealed.txt").writeText("AES-GCM and signature OK; model=" + card.model + "; XP=" + card.progress?.totalXp)
                    }
                    "card" -> File(filesDir, "qa/card.txt").apply { parentFile?.mkdirs(); writeText(app.superhealth.league.League.myCard(this@RegressionProbeActivity, true)) }
                    "accept" -> {
                        val card = app.superhealth.league.League.parse(intent.getStringExtra("card").orEmpty()) ?: error("invalid card")
                        app.superhealth.league.League.accept(this@RegressionProbeActivity, card)
                    }
                    "backup" -> {
                        val before = WorkoutApp.store.export()
                        val bytes = app.superhealth.backup.Archive.build(this@RegressionProbeActivity)
                        check(app.superhealth.backup.BackupCrypto.isEncrypted(bytes))
                        val plain = app.superhealth.backup.BackupCrypto.decrypt(bytes, app.superhealth.backup.Secrets.backupCode(this@RegressionProbeActivity))
                        val entries = java.util.zip.ZipInputStream(plain.inputStream()).use { zip ->
                            generateSequence { zip.nextEntry }.map { it.name }.toList()
                        }
                        check("montre.zip" in entries && "nutrition.json" in entries && "musculation.json" in entries)
                        val report = app.superhealth.backup.Archive.restore(this@RegressionProbeActivity, bytes)
                        check("échec" !in report)
                        check(before == WorkoutApp.store.export())
                        File(filesDir, "qa/backup.txt").writeText("Encrypted roundtrip OK; entries=$entries; $report")
                    }
                    "intro" -> SuperPrefs.update { it.copy(onboarded = false) }
                    "regression" -> {
                        val db = WorkoutApp.db.first { it != null }!!
                        val p = assets.open("programs/arms-30-days.json").bufferedReader().use { app.superhealth.training.domain.ProgramTransfer.read(it, db.exercises.map { e -> e.id }.toSet()) }
                        check(p.numCycles == 5 && p.days.size == 7)
                        check(p.days.sumOf { it.exercises.sumOf { e -> e.skippedCycles.size } } == 24)
                        WorkoutApp.store.update { it.copy(programs = it.programs.filterNot { it.name == p.name } + p.copy(cycleIndex = 1, doneDayIds = listOf(p.days.first().id)), settings = it.settings.copy(activeProgramId = p.id)) }
                        val challenge = "a".repeat(64)
                        val card = app.superhealth.league.League.parse(app.superhealth.league.League.helloCard())!!
                        check(app.superhealth.league.League.verifyProof(card.k, challenge, app.superhealth.league.League.proof(challenge)))
                        check(!app.superhealth.league.League.verifyProof(card.k, "b".repeat(64), app.superhealth.league.League.proof(challenge)))
                    }
                    "peer" -> {
                        val peer = app.superhealth.league.Nearby.create(applicationContext, knownOnly = true)
                        peer.start()
                        try { delay(20000) } finally { peer.stop() }
                    }
                    "nearby" -> {
                        val peer = app.superhealth.league.Nearby.create(applicationContext)
                        peer.start()
                        try {
                            withTimeout(25000) {
                                val found = peer.found.first { it.isNotEmpty() }
                                found.forEach { app.superhealth.league.League.accept(this@RegressionProbeActivity, it) }
                            }
                        } finally { peer.stop() }
                    }
                }
                snapshot()
                text.text = "QA OK · ${intent.getStringExtra("op") ?: "snapshot"}"
            } catch (e: Exception) {
                File(filesDir, "qa/error.txt").apply { parentFile?.mkdirs(); writeText(e.stackTraceToString()) }
                text.text = "QA ERROR · ${e.message}"
            }
        }
    }

    private suspend fun socialSeed() {
        val today = LocalDate.now().toEpochDay()
        val repo = NutriApp.repository
        val entries = (0L..20L).map { i -> app.superhealth.nutrition.data.FoodEntryEntity(id = 900000L + i, epochDay = today - i, meal = 1,
            name = "Repas QA", grams = 400.0, kcal = if (i == 0L) 650.0 else 2500.0, protein = 45.0, fat = 20.0, carbs = 72.5) }
        repo.entries.upsertAll(entries)
        val db = WorkoutApp.db.first { it != null }!!
        val e = db.exercises.first { it.usesWeight && it.usesReps && it.thumbnail() != null }
        val demoProgram = app.superhealth.training.domain.Program("qa-tracking-plan", "Programme QA · 4 séances", days = (1..4).map { n ->
            ProgramDay("qa-tracking-day-$n", "Séance $n", listOf(ProgramExercise("qa-tracking-ex-$n", e.id, listOf(List(3) { TargetSet() }))))
        })
        val history = listOf(20L, 18L, 16L, 13L, 11L, 9L, 6L, 4L, 2L, 0L, 0L, 0L).mapIndexed { index, ago ->
            val start = LocalDate.ofEpochDay(today - ago).atTime(17, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            app.superhealth.training.domain.Workout("qa-history-$index", "Séance QA", start, 1800,
                listOf(app.superhealth.training.domain.SessionExercise("qa-history-ex-$index", e.id, List(3) { j ->
                    app.superhealth.training.domain.SetLog("qa-log-$index-$j", weight = 40.0 + index * 2.5, reps = 10, rir = 2, done = true)
                })), programId = demoProgram.id, cycleIndex = 0, plannedSessions = 4)
        }
        WorkoutApp.store.update { it.copy(history = it.history.filterNot { w -> w.id.startsWith("qa-history-") } + history, session = null,
            programs = it.programs.filterNot { p -> p.id == demoProgram.id } + demoProgram,
            settings = it.settings.copy(activeProgramId = demoProgram.id)) }
        SuperPrefs.update { it.copy(onboarded = true, watchModule = false, healthConnect = false, updateCheck = false,
            coachEnabled = true, leagueEnabled = true, dashboard = listOf(DashCard.STREAKS, DashCard.PROGRESSION, DashCard.LEAGUE, DashCard.FRIENDS, DashCard.CALORIES, DashCard.NEXT_WORKOUT),
            activityAsked = true, activitySections = listOf("STRENGTH", "RUN"), features = setOf(Feature.NUTRITION.name, Feature.TRAINING.name)) }
        app.superhealth.league.League.configure(this) { it.copy(shareProgress = true, shareActivities = true, syncsPerDay = 0, refreshOnOpen = false, tracked = setOf("FOOD", "STRENGTH")) }
    }

    private suspend fun seed() {
        val today = LocalDate.now().toEpochDay()
        SuperPrefs.update { it.copy(onboarded = true, watchModule = false, healthConnect = false, updateCheck = false,
            features = setOf(Feature.NUTRITION.name, Feature.TRAINING.name), weighPrompt = false,
            dashboard = listOf(DashCard.LEAGUE, DashCard.CALORIES, DashCard.NEXT_WORKOUT)) }
        val repo = NutriApp.repository
        repo.prefs.setSettings(Settings(onboarded = true, language = Language.FR))
        repo.prefs.setProgram(Program(startEpochDay = today))
        repo.startGoal(Goal(startDay = today, type = GoalType.MAINTENANCE, startWeightKg = 80.0, targetWeightKg = 80.0))
        repo.weighIns.upsert(app.superhealth.nutrition.data.WeighInEntity(today, 80.0))
        repo.saveCheckIn(Targets.checkIn(today, 2500.0, 80.0, 60.0, repo.prefs.program.value, repo.prefs.goals.value.last()))
        val db = WorkoutApp.db.first { it != null }!!
        val ex = db.exercises.filter { it.thumbnail() != null }.take(5)
        val p = app.superhealth.training.domain.Program("qa-program", "QA réorganisation", days = listOf(
            ProgramDay("qa-day", "Exercices QA", ex.mapIndexed { i, e -> ProgramExercise("qa-ex-$i", e.id, listOf(List(3) { TargetSet() })) })
        ), numCycles = 1)
        WorkoutApp.store.update { it.copy(programs = listOf(p), settings = it.settings.copy(onboarded = true, activeProgramId = p.id)) }
        nodomain.freeyourgadget.gadgetbridge.GBApplication.getPrefs().preferences.edit()
            .putString(nodomain.freeyourgadget.gadgetbridge.model.ActivityUser.PREF_USER_NAME, "QA " + android.os.Build.MODEL).apply()
        app.superhealth.league.League.configure(this) { it.copy(shareActivities = true, syncsPerDay = 0) }
    }

    private suspend fun snapshot() {
        val dir = File(filesDir, "qa").apply { mkdirs() }
        File(dir, "training.json").writeText(WorkoutApp.store.export())
        File(dir, "nutrition.json").writeText(app.superhealth.nutrition.data.AppJson.encodeToString(Program.serializer(), NutriApp.repository.prefs.program.value))
        val score = app.superhealth.league.League.compute(this)
        File(dir, "progress.json").writeText(app.superhealth.nutrition.data.AppJson.encodeToString(app.superhealth.league.Progression.Summary.serializer(), app.superhealth.league.ProgressRepository.current.value!!.summary))
        File(dir, "score.txt").writeText("ready=${score.ready}; total=${score.total}; end=${score.endDay}; friends=${app.superhealth.league.League.state.value.friends.size}")
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
