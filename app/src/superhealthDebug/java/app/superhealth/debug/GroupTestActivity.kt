package app.superhealth.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.FileProvider
import app.superhealth.SuperPrefs
import app.superhealth.league.*
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.io.File

/** Synthetic integration harness; refuses every package except the isolated Group test application. */
class GroupTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageName != "app.superhealth.grouptest") { finish(); return }
        val operation = intent.getStringExtra("operation") ?: "status"
        val directory = File(filesDir, "group-test").apply { mkdirs() }
        File(directory, "result.json").delete()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = WorkoutApp.db.first { it != null }!!
                when (operation) {
                    "seed" -> {
                        val name = intent.getStringExtra("name")?.take(24) ?: "Test Groupe"
                        nodomain.freeyourgadget.gadgetbridge.GBApplication.getPrefs().preferences.edit()
                            .putString(nodomain.freeyourgadget.gadgetbridge.model.ActivityUser.PREF_USER_NAME, name).commit()
                        SuperPrefs.update { it.copy(onboarded = true, socialFeedIntroSeen = true, leagueEnabled = true,
                            socialFeedEnabled = true, watchModule = false, healthConnect = false, updateCheck = false,
                            features = setOf("NUTRITION", "TRAINING")) }
                        League.configure(this@GroupTestActivity) { it.copy(customRelay = true, relayUrl = "http://127.0.0.1:18787",
                            relayEnabled = true, profileTitle = "xp_100", profileBadges = listOf("xp_100", "strength_1"), earnedBadges = setOf("xp_100", "strength_1")) }
                        val exercise = db.exercises.first { it.usesWeight && it.usesReps && "aw" !in it.metrics }
                        val now = System.currentTimeMillis()
                        val workouts = listOf(80.0, 100.0).mapIndexed { i, weight -> Workout("group-test-$i", "Séance de test ${i + 1}",
                            now - (8 - i) * 86400000L, 1200, listOf(SessionExercise("e$i", exercise.id,
                                listOf(SetLog("s$i", weight = weight, reps = 5, done = true))))) }
                        WorkoutApp.store.update { it.copy(history = workouts) }
                        val bitmap = android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888)
                        android.graphics.Canvas(bitmap).apply {
                            drawColor(android.graphics.Color.rgb(45, 100, 180))
                            drawText(name.take(1), 65f, 130f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 110f })
                        }
                        app.superhealth.ui.ProfilePhoto.file(this@GroupTestActivity).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                        bitmap.recycle(); app.superhealth.ui.ProfilePhoto.load(this@GroupTestActivity)
                        Relay.sync(this@GroupTestActivity)
                    }
                    "add" -> { check(League.receive(this@GroupTestActivity, intent.getStringExtra("card")!!));
                        League.incoming.value?.let { League.accept(this@GroupTestActivity, it); League.incoming.value = null }; Relay.sync(this@GroupTestActivity) }
                    "publish" -> {
                        val video = File(cacheDir, "social/group-test.mp4")
                        val uri = video.takeIf { it.isFile }?.let { FileProvider.getUriForFile(this@GroupTestActivity, "$packageName.files", it) }
                        val record = GroupRecords.recent().first { it.metric == "WEIGHT" }
                        val published = SocialPosts.publish(this@GroupTestActivity, "Un PR avec sa vidéo de test", GroupRecords.draft(record), uri)
                        check(published.sent)
                    }
                    "reply" -> {
                        Relay.sync(this@GroupTestActivity)
                        val (post, friend) = GroupHistory.posts(this@GroupTestActivity).first { it.second != null && it.first.record != null }
                        check(GroupHistory.reply(this@GroupTestActivity, friend!!.id, post, "Bravo pour ce record !"))
                    }
                    "issues" -> {
                        val repo = app.superhealth.nutrition.NutriApp.repository
                        val day = java.time.LocalDate.now().toEpochDay()
                        repo.entries.allFlow().first().filter { it.name.startsWith("QA ") }.forEach { repo.entries.delete(it.id) }
                        listOf(Triple("QA Lait", 300.0, 180.0), Triple("QA Banane", 100.0, 90.0), Triple("QA Miel", 10.0, 30.0)).forEach { (name, grams, kcal) ->
                            repo.entries.insert(app.superhealth.nutrition.data.FoodEntryEntity(epochDay = day, meal = 0,
                                name = name, grams = grams, kcal = kcal, protein = 3.0, fat = 2.0, carbs = 10.0, minuteOfDay = 480))
                        }
                        val root = File(filesDir, "modules").apply { mkdirs() }
                        val folder = File(root, "qa-stretch").apply { mkdirs() }
                        val exercises = listOf(
                            app.superhealth.stretch.Stretch.Ex("qa-shoulders", "QA Épaules", "ActiveStretch", zones = listOf("Shoulders"), daily = true, pre = true, post = true),
                            app.superhealth.stretch.Stretch.Ex("qa-thighs", "QA Ischio-jambiers", "PassiveStretch", zones = listOf("Hamstrings"), daily = true, pre = true, post = true))
                        File(folder, "exercises.json").writeText(Json.encodeToString(app.superhealth.stretch.Stretch.Catalog.serializer(), app.superhealth.stretch.Stretch.Catalog(exercises = exercises)))
                        val installed = app.superhealth.modules.Modules.installed.value.filterNot { it.id == "qa-stretch" } +
                            app.superhealth.modules.Modules.Installed("qa-stretch", "stretch", "QA Étirements", "test", "local", false)
                        File(root, "installed.json").writeText(Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(app.superhealth.modules.Modules.Installed.serializer()), installed))
                        app.superhealth.modules.Modules.init(this@GroupTestActivity)
                        app.superhealth.stretch.Stretch.invalidate()
                        app.superhealth.stretch.Stretch.prepare(app.superhealth.stretch.Stretch.Routine("QA Mobilité", app.superhealth.stretch.Stretch.Kind.DAILY, exercises))
                    }
                    "archive" -> {
                        repeat(25) { i -> GroupHistory.record(this@GroupTestActivity, SocialPosts.Post(
                            java.util.UUID.randomUUID().toString().replace("-", ""),
                            System.currentTimeMillis() - (1100L + i) * 86400000, "Souvenir de séance $i")) }
                        Relay.publishOnly(this@GroupTestActivity)
                    }
                    "react" -> {
                        val (post, friend) = GroupHistory.posts(this@GroupTestActivity).first { it.second != null && it.first.record != null }
                        check(SocialReactions.react(this@GroupTestActivity, friend!!.id, post, SocialReactions.customKind("💪🏽")!!))
                    }
                    "sync" -> Relay.sync(this@GroupTestActivity)
                    "rotate" -> Relay.rotate(this@GroupTestActivity)
                    "recover" -> GroupRecovery.recover(this@GroupTestActivity, intent.getStringExtra("code")!!, "http://127.0.0.1:18787")
                    "download" -> {
                        val (post, friend) = GroupHistory.posts(this@GroupTestActivity).first { it.first.media?.mime?.startsWith("video/") == true }
                        val video = SocialPosts.download(this@GroupTestActivity, post, friend)
                        check(video.length() > 0)
                        File(directory, "video.sha256").writeText(java.security.MessageDigest.getInstance("SHA-256").digest(video.readBytes()).joinToString("") { "%02x".format(it) })
                    }
                }
                File(directory, "invitation.txt").writeText(League.inviteCard(this@GroupTestActivity))
                File(directory, "recovery.txt").writeText(app.superhealth.backup.Secrets.backupCode(this@GroupTestActivity))
                val posts = GroupHistory.posts(this@GroupTestActivity)
                File(directory, "result.json").writeText(buildJsonObject {
                    put("ok", true); put("operation", operation); put("friends", League.state.value.friends.size)
                    put("posts", posts.size); put("pr", posts.count { it.first.record != null }); put("video", posts.count { it.first.media?.mime?.startsWith("video/") == true })
                    put("replies", GroupHistory.state.value.events.count { !it.deleted && it.reply != null }); put("pending", GroupHistory.state.value.pending.size)
                    put("archived", GroupHistory.state.value.events.count { it.post != null && it.key == League.publicKey() });
                    put("reactionEvents", GroupHistory.state.value.events.count { it.reaction != null });
                    put("public", League.publicKey()); put("profiles", League.state.value.friends.count { it.avatar != null && it.profileBadges.isNotEmpty() })
                    put("syncError", League.state.value.syncError)
                }.toString())
            } catch (e: Exception) {
                File(directory, "result.json").writeText(buildJsonObject { put("ok", false); put("operation", operation);
                    put("error", e.javaClass.simpleName); put("detail", e.message.orEmpty().replace(Regex("https?://[^ ]+"), "[server]")) }.toString())
            } finally {
                withContext(Dispatchers.Main) {
                    if (intent.getBooleanExtra("open", false)) startActivity(Intent(this@GroupTestActivity, app.superhealth.MainActivity::class.java).putExtra("route", "feed"))
                    finish()
                }
            }
        }
    }
}
