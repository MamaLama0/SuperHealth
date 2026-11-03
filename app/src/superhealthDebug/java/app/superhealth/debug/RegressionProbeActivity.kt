package app.superhealth.debug

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import app.superhealth.*
import app.superhealth.league.League
import app.superhealth.league.HealthIndex
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
                    "adaptive-321" -> {
                        val out = File(filesDir, "qa/adaptive-321.txt").apply { parentFile?.mkdirs(); writeText("running") }
                        val repo = NutriApp.repository
                        val original = repo.prefs.settings.value
                        val originalProgram = repo.prefs.program.value
                        try {
                            repo.prefs.setSettings(original.copy(learnFoodPatterns = true, learnActivityEnergy = true))
                            val an = withTimeout(30000) { repo.analytics().first() }
                            check(an.program == originalProgram && an.currentTdee.isFinite())
                            val facts = app.superhealth.bridge.Bridge.activityFacts(System.currentTimeMillis() - 90L * 86_400_000)
                            check(facts.map { it.key }.distinct().size == facts.size)
                            app.superhealth.health.data.Performance.snapshot(this@RegressionProbeActivity)
                            val score = withTimeout(60000) { League.compute(this@RegressionProbeActivity) }
                            check(League.parse(League.card("QA", score))?.s == score.total)
                            val context = app.superhealth.coach.Coach.context(this@RegressionProbeActivity)
                            if (!app.superhealth.coach.Coach.shares(app.superhealth.coach.Coach.Share.INDEX)) check("App habits index v" !in context)
                        } finally {
                            repo.prefs.setSettings(original)
                        }
                        out.writeText("PASS nutrition, activity facts, race performance, signed score, local coach context")
                    }
                    "unified-32" -> {
                        val out = File(filesDir, "qa/unified-32.txt").apply { parentFile?.mkdirs(); writeText("running") }
                        val score = League.compute(this@RegressionProbeActivity)
                        val parsed = League.parse(League.card("QA", score)) ?: error("Signed score did not round trip")
                        check(parsed.model == score.model && parsed.s == score.total && parsed.ready == score.ready)
                        val current = app.superhealth.health.data.Scores.load(List(25) { 50.0 } + listOf(null, null, null))
                        check(current == (50.0 to 50.0))
                        val demo = League.Score("2026-W44", 750, mapOf(League.Pillar.SLEEP to 80, League.Pillar.FOOD to 70, League.Pillar.STEPS to 75), true, 20000)
                        val limited = app.superhealth.coach.Coach.indexContext(demo) { it != app.superhealth.coach.Coach.Share.FOOD }
                        check("750/1000" !in limited && "food:" !in limited && "sleep: 80/100" in limited)
                        val full = app.superhealth.coach.Coach.indexContext(demo) { true }
                        check("750/1000" in full)
                        out.writeText("PASS score-card, missing-wear, coach-permissions")
                    }
                    "social-304-prepare" -> {
                        seed()
                        SuperPrefs.update { it.copy(leagueEnabled = true, socialFeedEnabled = true, socialFeedIntroSeen = true) }
                        League.configure(this@RegressionProbeActivity) { League.State(customRelay = true,
                            relayUrl = intent.getStringExtra("server").orEmpty(), syncsPerDay = 0, shareActivities = true) }
                        val image = android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888)
                        image.eraseColor(android.graphics.Color.rgb(50, 140, 210))
                        app.superhealth.ui.ProfilePhoto.file(this@RegressionProbeActivity).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                        image.recycle(); app.superhealth.ui.ProfilePhoto.load(this@RegressionProbeActivity)
                    }
                    "social-304-react" -> {
                        val friend = League.state.value.friends.first { it.posts.isNotEmpty() }
                        check(app.superhealth.league.SocialReactions.react(this@RegressionProbeActivity, friend.id, friend.posts.first(), intent.getStringExtra("kind") ?: "LIKE"))
                    }
                    "social-304-report" -> {
                        val state = League.state.value
                        val avatar = state.friends.firstOrNull()?.avatar
                        val imageOk = if (avatar != null) app.superhealth.league.SocialPosts.downloadMedia(this@RegressionProbeActivity, avatar, state.friends.first()).length() > 0 else false
                        val post = state.posts.firstOrNull()
                        val reactions = post?.let { app.superhealth.league.SocialReactions.counts(state.reactions, state.friends, League.myId(), it.id) }.orEmpty()
                        val report = kotlinx.serialization.json.buildJsonObject {
                            put("friends", kotlinx.serialization.json.JsonPrimitive(state.friends.size))
                            put("ownPosts", kotlinx.serialization.json.JsonPrimitive(state.posts.size))
                            put("friendPosts", kotlinx.serialization.json.JsonPrimitive(state.friends.sumOf { it.posts.size }))
                            put("avatarDecoded", kotlinx.serialization.json.JsonPrimitive(imageOk))
                            put("likesOnOwnPost", kotlinx.serialization.json.JsonPrimitive(reactions["LIKE"] ?: 0))
                            put("notifications", kotlinx.serialization.json.JsonPrimitive(getSystemService(android.app.NotificationManager::class.java).activeNotifications.size))
                        }
                        File(filesDir, "qa/social-304.json").apply { parentFile?.mkdirs(); writeText(report.toString()) }
                    }
                    "social-save" -> {
                        val dir = File(filesDir, "qa/301-original").apply { mkdirs() }
                        check(!File(dir, "league.json").exists())
                        File(dir, "league.json").writeText(kotlinx.serialization.json.Json.encodeToString(League.State.serializer(), League.state.value))
                        File(dir, "settings.json").writeText(kotlinx.serialization.json.Json.encodeToString(SuperSettings.serializer(), SuperPrefs.settings.value))
                    }
                    "social-restore" -> {
                        val dir = File(filesDir, "qa/301-original")
                        val original = kotlinx.serialization.json.Json.decodeFromString(League.State.serializer(), File(dir, "league.json").readText())
                        val settings = kotlinx.serialization.json.Json.decodeFromString(SuperSettings.serializer(), File(dir, "settings.json").readText())
                        League.configure(this@RegressionProbeActivity) { original }
                        SuperPrefs.update { settings }
                        dir.deleteRecursively()
                    }
                    "feed-intro" -> SuperPrefs.update { it.copy(socialFeedIntroSeen = false, socialFeedEnabled = true) }
                    "feed-fixture" -> {
                        val now = System.currentTimeMillis()
                        League.configure(this@RegressionProbeActivity) { it.copy(posts = listOf(
                            app.superhealth.league.SocialPosts.Post("3".repeat(32), now, "La régularité finit par payer 💪", "WORKOUT", "Haut du corps", "45 min · 4 exercices\nDéveloppé couché · 3 × 10 × 60 kg"),
                            app.superhealth.league.SocialPosts.Post("4".repeat(32), now - 3600000, "La sortie du matin !", "RUN", "Ma sortie course", "5,20 km · 29 min"))) }
                    }
                    "post-publish" -> {
                        val source = app.superhealth.league.SocialPosts.Draft("Séance QA 3.0.1", "3 × 10 × 60 kg · sans GPS", "WORKOUT")
                        val result = app.superhealth.league.SocialPosts.publish(this@RegressionProbeActivity, "Échange chiffré entre téléphones", source,
                            intent.getStringExtra("uri")?.let(android.net.Uri::parse))
                        File(filesDir, "qa/publish-result.txt").writeText("sent=${result.sent}")
                    }
                    "post-download" -> {
                        val friend = League.state.value.friends.first { it.posts.any { p -> p.media != null } }
                        val post = friend.posts.first { it.media != null }
                        val downloaded = app.superhealth.league.SocialPosts.download(this@RegressionProbeActivity, post, friend)
                        File(filesDir, "qa/media-received.txt").writeText("bytes=${downloaded.length()};sha256=" + java.security.MessageDigest.getInstance("SHA-256").digest(downloaded.readBytes()).joinToString("") { "%02x".format(it) })
                    }
                    "widget-cleanup" -> {
                        android.appwidget.AppWidgetHost(this@RegressionProbeActivity, 3002).deleteHost()
                    }
                    "appearance-save" -> {
                        val saved = File(filesDir, "qa/appearance-original.json")
                        check(!saved.exists()) { "Restore the previous QA appearance first" }
                        saved.parentFile?.mkdirs()
                        saved.writeText(kotlinx.serialization.json.Json.encodeToString(
                            Settings.serializer(), NutriApp.repository.prefs.settings.value))
                    }
                    "appearance" -> {
                        val prefs = NutriApp.repository.prefs
                        val current = prefs.settings.value
                        prefs.setSettings(current.copy(
                            themeMode = intent.getStringExtra("theme")?.let { ThemeMode.valueOf(it) } ?: current.themeMode,
                            textSize = intent.getStringExtra("textSize")?.let { app.superhealth.nutrition.domain.TextSize.valueOf(it) } ?: current.textSize,
                            language = intent.getStringExtra("language")?.let { Language.valueOf(it) } ?: current.language))
                    }
                    "appearance-restore" -> {
                        val saved = File(filesDir, "qa/appearance-original.json")
                        // Changer la langue peut recréer cette activité et rejouer le même Intent.
                        if (saved.exists()) {
                            NutriApp.repository.prefs.setSettings(kotlinx.serialization.json.Json.decodeFromString(Settings.serializer(), saved.readText()))
                            saved.delete()
                        }
                    }
                    "seed" -> seed()
                    "relay-live-reset" -> {
                        app.superhealth.league.League.configure(this@RegressionProbeActivity) { it.copy(friends = emptyList(), customRelay = false, relayUrl = "", relayEnabled = true, syncsPerDay = 0, refreshOnOpen = false, syncError = null) }
                        listOf("friend_invitation_code", "friend_invitation_base", "friend_invitation_expiry", "friend_invitation_owner", "league_published_hash_v2", "league_published_at_v2").forEach {
                            app.superhealth.backup.Secrets.put(this@RegressionProbeActivity, it, "")
                        }
                    }
                    "quest-complete" -> app.superhealth.meditation.Meditation.record(this@RegressionProbeActivity, 5, "qa")
                    "quest-claim-again" -> {
                        val q = app.superhealth.league.League.state.value.questWeeks.lastOrNull()?.quests?.firstOrNull { !it.secondary }
                        if (q != null) app.superhealth.league.League.claimQuest(this@RegressionProbeActivity, q.metric)
                    }
                    "code-create" -> {
                        val result = app.superhealth.league.InvitationCodes.publish(this@RegressionProbeActivity, intent.getBooleanExtra("renew", false))
                        File(filesDir, "qa/code.txt").apply { parentFile?.mkdirs(); writeText(result.text) }
                    }
                    "code-lookup" -> {
                        app.superhealth.league.InvitationCodes.lookup(this@RegressionProbeActivity, intent.getStringExtra("code").orEmpty(), intent.getStringExtra("server"))
                        val card = app.superhealth.league.League.incoming.value ?: error("No invitation")
                        app.superhealth.league.InvitationCodes.confirm(this@RegressionProbeActivity, card)
                        app.superhealth.league.League.incoming.value = null
                    }
                    "equip" -> {
                        app.superhealth.league.ProgressRepository.compute(this@RegressionProbeActivity)
                        app.superhealth.league.League.configure(this@RegressionProbeActivity) { state ->
                            val unlocked = app.superhealth.league.Achievements.catalog.filter { it.id in state.earnedBadges }
                            state.copy(profileTitle = unlocked.lastOrNull { it.title != null }?.id, profileBadges = unlocked.take(3).map { it.id })
                        }
                    }
                    "sync" -> app.superhealth.league.Relay.sync(this@RegressionProbeActivity)
                    "code-poll" -> app.superhealth.league.InvitationCodes.collectResponses(this@RegressionProbeActivity)
                    "code-custom" -> {
                        app.superhealth.league.League.configure(this@RegressionProbeActivity) { it.copy(customRelay = true, relayUrl = intent.getStringExtra("server").orEmpty(), relayEnabled = true) }
                    }
                    "code-default" -> app.superhealth.league.League.configure(this@RegressionProbeActivity) { it.copy(customRelay = false, relayUrl = "", relayEnabled = true) }
                    "free-done" -> {
                        val db = WorkoutApp.db.first { it != null }!!
                        val e = db.exercises.first { it.usesWeight && it.usesReps }
                        val set = app.superhealth.training.domain.SetLog("qa-done-set", weight = 40.0, reps = 10, rir = 2, done = true)
                        WorkoutApp.store.update { it.copy(session = app.superhealth.training.domain.Session("qa-done", "Correction QA", System.currentTimeMillis(), listOf(app.superhealth.training.domain.SessionExercise("qa-done-ex", e.id, listOf(set))), freeForm = true)) }
                    }
                    "ranking-fixture", "ranking-cleanup" -> {
                        val score = League.compute(this@RegressionProbeActivity)
                        val now = System.currentTimeMillis() / 1000
                        // Identités locales sans adresse, retirables sans toucher aux vrais amis QA.
                        val fixtures = if (intent.getStringExtra("op") == "ranking-cleanup") emptyList() else
                            listOf(980, 980, 920, 910, 999).mapIndexed { i, points ->
                                val id = "qa-v3-ranking-$i"
                                val name = listOf("Zoé", "Léon", "Charlie", "Camille du club du lundi", "Carte expirée")[i]
                                League.Friend(id, id, name, score.week, if (i == 4) now - 49 * 3600 else now,
                                    points, model = HealthIndex.MODEL, ready = true, endDay = score.endDay, coverage = score.coverage)
                            }
                        League.configure(this@RegressionProbeActivity) { it.copy(friends =
                            it.friends.filterNot { f -> f.id.startsWith("qa-v3-ranking-") } + fixtures) }
                    }
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
            .putString(nodomain.freeyourgadget.gadgetbridge.model.ActivityUser.PREF_USER_NAME, "QA Android").apply()
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
