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
        if (packageName !in setOf("app.superhealth.grouptest", "app.superhealth.hikingtest")) { finish(); return }
        val operation = intent.getStringExtra("operation") ?: "status"
        val directory = File(filesDir, "group-test").apply { mkdirs() }
        File(directory, "result.json").delete()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = WorkoutApp.db.first { it != null }!!
                when (operation) {
                    "hc-permissions" -> {
                        val permissions = setOf(
                            androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.ExerciseSessionRecord::class),
                            androidx.health.connect.client.permission.HealthPermission.getWritePermission(androidx.health.connect.client.records.ExerciseSessionRecord::class),
                            androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.DistanceRecord::class),
                            androidx.health.connect.client.permission.HealthPermission.getWritePermission(androidx.health.connect.client.records.DistanceRecord::class))
                        withContext(Dispatchers.Main) {
                            if (android.os.Build.VERSION.SDK_INT >= 34) requestPermissions(permissions.toTypedArray(), 871)
                            else startActivityForResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract().createIntent(this@GroupTestActivity,permissions), 871)
                        }
                    }
                    "hc-probe" -> {
                        val client = androidx.health.connect.client.HealthConnectClient.getOrCreate(this@GroupTestActivity)
                        val now = System.currentTimeMillis()
                        val outdoor = app.superhealth.bridge.HcOutdoorSync.Outdoor("qa-roundtrip",app.superhealth.bridge.Bridge.Kind.RUN,"QA Course à pied — temporaire",now-900000,now-300000,2000.0)
                        val records = app.superhealth.bridge.HcOutdoorSync.records(listOf(outdoor),0,now,true,false)
                        try {
                            client.insertRecords(records)
                            client.insertRecords(records) // Same IDs must update, not duplicate.
                            val range = androidx.health.connect.client.time.TimeRangeFilter.between(java.time.Instant.ofEpochMilli(outdoor.start),java.time.Instant.ofEpochMilli(outdoor.end+1))
                            val origin = setOf(androidx.health.connect.client.records.metadata.DataOrigin(packageName))
                            val sessions = client.readRecords(androidx.health.connect.client.request.ReadRecordsRequest(androidx.health.connect.client.records.ExerciseSessionRecord::class,range,origin)).records
                                .filter { it.metadata.clientRecordId == "outdoor-session-qa-roundtrip" }
                            check(sessions.size==1 && sessions.single().exerciseType==androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_RUNNING)
                            val distances = client.readRecords(androidx.health.connect.client.request.ReadRecordsRequest(androidx.health.connect.client.records.DistanceRecord::class,range,origin)).records
                                .filter { it.metadata.clientRecordId == "outdoor-distance-qa-roundtrip" }
                            check(distances.size==1 && distances.single().distance.inMeters==2000.0)
                            File(directory,"hc-roundtrip.txt").writeText("running=1 distance=2000m duplicates=0")
                        } finally {
                            client.deleteRecords(androidx.health.connect.client.records.ExerciseSessionRecord::class,emptyList(),listOf("outdoor-session-qa-roundtrip"))
                            client.deleteRecords(androidx.health.connect.client.records.DistanceRecord::class,emptyList(),listOf("outdoor-distance-qa-roundtrip"))
                        }
                    }
                    "hiking" -> {
                        check(app.superhealth.modules.Modules.ensure(this@GroupTestActivity, app.superhealth.modules.Modules.HIKING))
                        check(app.superhealth.hiking.Hiking.catalog().size >= 8)
                        SuperPrefs.update { it.copy(onboarded = true, activityAsked = true, activitySections = listOf("HIKING"),
                            activityCurrent = "HIKING", watchModule = false, socialFeedIntroSeen = true) }
                    }
                    "hiking-reward" -> {
                        val original=app.superhealth.hiking.Hiking.catalog().first()
                        val now=System.currentTimeMillis();val start=now-3600000
                        val route=original.copy(id="qa-hike",name="QA randonnée synthétique",points=original.points.mapIndexed { i,p -> p.copy(time=start+i*1800000L/(original.points.size-1)) })
                        app.superhealth.hiking.Hiking.save(this@GroupTestActivity,route)
                        val previous=app.superhealth.hiking.Hiking.library.value
                        val log=app.superhealth.hiking.Hiking.Completed(route.id,start,app.superhealth.hiking.HikeGeometry.length(route.points),1800,end=start+1800000,name=route.name)
                        val next=previous.copy(completed=previous.completed.filterNot{it.route==route.id}+log)
                        File(filesDir,"hiking/library.json").writeText(Json.encodeToString(app.superhealth.hiking.Hiking.Library.serializer(),next))
                        app.superhealth.hiking.Hiking.load(this@GroupTestActivity)
                        League.configure(this@GroupTestActivity){it.copy(questFocus=setOf(WeeklyQuests.Focus.HIKING))}
                        val progress=ProgressRepository.compute(this@GroupTestActivity)
                        check((progress.summary.totals["hikes"] ?: 0)>=1)
                        check("hikes_1" in League.state.value.earnedBadges)
                        val week=League.state.value.questWeeks.first{it.start==app.superhealth.league.Progression.week(java.time.LocalDate.now().toEpochDay())}
                        check(week.quests.any{it.metric==WeeklyQuests.Metric.HIKE && it.complete})
                        League.claimQuest(this@GroupTestActivity,WeeklyQuests.Metric.HIKE)
                        val shared=SharedActivity.hikingDraft(route,log).shared(route=false,details=true)
                        check(shared.activity?.route==null)
                        check(SocialPosts.publish(this@GroupTestActivity,"Randonnée synthétique de test",shared,null).sent)
                        League.configure(this@GroupTestActivity){it.copy(profileTitle="hikes_1",profileBadges=listOf("hikes_1"))}
                        Relay.sync(this@GroupTestActivity)
                        File(directory,"hiking-rewards.txt").writeText("badge=hikes_1 quest=complete gpsShared=false")
                    }
                    "hiking-search" -> {
                        val results = app.superhealth.hiking.Hiking.search(intent.getStringExtra("place") ?: "Chamonix")
                        check(results.isNotEmpty())
                        File(directory, "search-count.txt").writeText(results.size.toString())
                    }
                    "hiking-start" -> {
                        val route = app.superhealth.hiking.Hiking.catalog().first()
                        app.superhealth.hiking.Hiking.setSession(this@GroupTestActivity, app.superhealth.hiking.Hiking.Session(route = route, started = System.currentTimeMillis()))
                        withContext(Dispatchers.Main) { app.superhealth.hiking.HikeTrackingService.start(this@GroupTestActivity) }
                    }
                    "hiking-stop" -> {
                        val current = app.superhealth.hiking.Hiking.session.value
                        File(directory, "gps-count.txt").writeText((current?.points?.size ?: 0).toString())
                        current?.let { app.superhealth.hiking.Hiking.setSession(this@GroupTestActivity,
                            it.copy(seconds = app.superhealth.hiking.Hiking.elapsed(it), paused = true)) }
                        app.superhealth.hiking.HikeTrackingService.stop(this@GroupTestActivity)
                        app.superhealth.hiking.Hiking.finish(this@GroupTestActivity)
                    }
                    "post-text" -> { check(SocialPosts.publish(this@GroupTestActivity, "Publication randonnée de test", null, null).sent) }
                    "comment" -> {
                        Relay.sync(this@GroupTestActivity)
                        val (post, friend) = GroupHistory.posts(this@GroupTestActivity).first { it.second != null }
                        check(GroupHistory.reply(this@GroupTestActivity, friend!!.id, post, intent.getStringExtra("text") ?: "Commentaire de test"))
                    }
                    "comment-reply" -> {
                        val (post, friend) = GroupHistory.posts(this@GroupTestActivity).first { it.second != null }
                        val comments = GroupHistory.replies(friend!!.id,post.id)
                        val parent = comments.first { it.reply?.text == "Commentaire du randonneur" }
                        check(GroupHistory.reply(this@GroupTestActivity,friend.id,post,"Réponse au randonneur",parent))
                    }
                    "inbox" -> app.superhealth.league.SocialInbox.add(this@GroupTestActivity,"test-comment","Nouveau commentaire","Un randonneur a répondu à votre commentaire.","feed")
                    "icon" -> app.superhealth.ui.LauncherIcon.apply(this@GroupTestActivity,
                        app.superhealth.nutrition.domain.AccentColor.valueOf(intent.getStringExtra("accent") ?: "BLUE"))
                    "notification" -> {
                        val device = nodomain.freeyourgadget.gadgetbridge.impl.GBDevice("00:11:22:33:44:55", "Montre de test", null, null,
                            nodomain.freeyourgadget.gadgetbridge.model.DeviceType.TEST)
                        device.setBatteryLevel(73, 0)
                        device.setState(nodomain.freeyourgadget.gadgetbridge.impl.GBDevice.State.INITIALIZED)
                        val notification = nodomain.freeyourgadget.gadgetbridge.util.GB.createNotification(listOf(device), this@GroupTestActivity)
                        check(notification.contentIntent.creatorPackage == packageName)
                        nodomain.freeyourgadget.gadgetbridge.util.GB.notify(9051, notification, this@GroupTestActivity)
                    }
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
                    "respond" -> {
                        val parent = GroupHistory.state.value.events.first { it.reply?.text == "Bravo pour ce record !" }
                        val post = GroupHistory.posts(this@GroupTestActivity).first { it.first.id == parent.reply!!.post }.first
                        check(GroupHistory.reply(this@GroupTestActivity, parent.reply!!.author, post, "Merci pour ton encouragement !", parent))
                    }
                    "visuals" -> {
                        val now = System.currentTimeMillis()
                        val route = SharedActivity.Route.encode((0..90).map { i ->
                            val t = i / 90.0 * Math.PI * 2
                            48.858 + kotlin.math.sin(t) * .006 to 2.294 + kotlin.math.cos(t) * .009
                        })
                        val activity = SharedActivity.Outdoor("RUN", now - 86400000, 1560.0, 5200.0, 420.0, 85.0, 148, 174, 172.0,
                            route, (0..60).map { 3.0 + kotlin.math.sin(it / 6.0) * .8 },
                            (0..60).map { 80.0 + kotlin.math.sin(it / 12.0) * 25 }, (0..60).map { 140 + it / 2 },
                            List(5) { SharedActivity.Split(1000.0, 290.0 + it * 12) })
                        check(SocialPosts.publish(this@GroupTestActivity, "Une sortie partagée avec son parcours et ses statistiques.",
                            SocialPosts.Draft("Boucle au parc", "5,20 km · 26 min", "RUN", activity = activity), null).sent)
                        val exercises = db.exercises.filter { it.usesWeight && it.usesReps && "aw" !in it.metrics }.take(4)
                        val workout = Workout("visual-test", "Séance du haut du corps", now - 86400000, 3600,
                            exercises.mapIndexed { i, ex -> SessionExercise("ex-$i", ex.id,
                                List(3) { j -> SetLog("set-$i-$j", weight = 60.0 + i * 10, reps = 8 + j, done = true) }) })
                        check(SocialPosts.publish(this@GroupTestActivity, "Une bonne séance, tous les détails sont dans le résumé.", SocialPosts.workoutDraft(workout), null).sent)
                        val record = GroupRecords.Record(exercises.first().id, exercises.first().name, "WEIGHT", 100.0, "kg", now,
                            90.0, listOf(60.0, 70.0, 80.0, 90.0, 100.0), exercises.first().muscleGroups.take(3).map { it.key })
                        check(SocialPosts.publish(this@GroupTestActivity, "Nouveau cap !", GroupRecords.draft(record), null).sent)
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
                    put("hikingRoutes", app.superhealth.hiking.Hiking.catalog().size)
                    put("visibleComments", posts.sumOf { (post, friend) -> GroupHistory.replies(friend?.id ?: League.myId(),post.id).size })
                    put("forwarded", GroupHistory.state.value.events.count { it.forward != null })
                    put("unread", SocialInbox.items.value.count { !it.read })
                    put("tracking", app.superhealth.hiking.Hiking.tracking.value)
                }.toString())
            } catch (e: Exception) {
                File(directory, "result.json").writeText(buildJsonObject { put("ok", false); put("operation", operation);
                    put("error", e.javaClass.simpleName); put("detail", e.message.orEmpty().replace(Regex("https?://[^ ]+"), "[server]")) }.toString())
            } finally {
                withContext(Dispatchers.Main) {
                    if (intent.getBooleanExtra("open", false)) startActivity(Intent(this@GroupTestActivity, app.superhealth.MainActivity::class.java).putExtra("route", intent.getStringExtra("route") ?: "feed"))
                    if (operation != "hc-permissions") finish()
                }
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode==871) finish()
    }
    @Deprecated("Test-only legacy permission result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==871) finish()
    }
}
