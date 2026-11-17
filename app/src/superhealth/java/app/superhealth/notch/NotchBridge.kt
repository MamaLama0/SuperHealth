package app.superhealth.notch

import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.graphics.toArgb
import app.superhealth.MainActivity
import app.superhealth.SuperPrefs
import app.superhealth.bridge.LiveHeart
import app.superhealth.hiking.HikeGeometry
import app.superhealth.hiking.Hiking
import app.superhealth.meds.Meds
import app.superhealth.training.WorkoutApp
import app.superhealth.training.data.AppState
import app.superhealth.training.domain.SetType
import app.superhealth.training.ui.SessionActionReceiver
import app.superhealth.training.ui.SessionNotification
import app.superhealth.training.ui.clean
import app.superhealth.training.ui.w
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.CaloriesColor
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.ProteinColor
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Montre la séance guidée, la randonnée suivie au GPS, la méditation et les rappels de médicaments dans l'île de
 * Notch (app.notch), par son « Live Activity API » public : des diffusions explicites vers son récepteur, réservées aux
 * apps qui déclarent app.notch.permission.POST_LIVE_ACTIVITY (docs/LIVE_ACTIVITY_API.md du dépôt Notch).
 *
 * Rien n'est envoyé si Notch n'est pas installé ou si le réglage « Afficher dans l'île Notch » est coupé. Les
 * notifications de SuperHealth restent inchangées : Notch masque lui-même leur doublon tant que l'activité est en direct.
 *
 * Les clés « sh.* » sont celles des modèles santé de Notch (HealthRenderer.Keys) : les renommer casse l'affichage.
 */
object NotchBridge {
    const val NOTCH_PACKAGE = "app.notch"
    const val PERMISSION = "app.notch.permission.POST_LIVE_ACTIVITY"
    private const val RECEIVER = "app.notch.api.LiveActivityReceiver"
    private const val ACTION = "app.notch.action.LIVE_ACTIVITY"

    // region Contrat du Live Activity API (LiveActivityContract de Notch)
    private const val X = "app.notch.extra."
    private const val EXTRA_VERSION = X + "VERSION"
    private const val EXTRA_OP = X + "OP"
    private const val EXTRA_ID = X + "ID"
    private const val EXTRA_CONTENT_INTENT = X + "CONTENT_INTENT"
    private const val EXTRA_KIND = X + "KIND"
    private const val EXTRA_TITLE = X + "TITLE"
    private const val EXTRA_SUBTITLE = X + "SUBTITLE"
    private const val EXTRA_SHORT_TEXT = X + "SHORT_TEXT"
    private const val EXTRA_PROGRESS = X + "PROGRESS"
    private const val EXTRA_CHRONOMETER_BASE = X + "CHRONOMETER_BASE"
    private const val EXTRA_COUNTDOWN = X + "COUNTDOWN"
    private const val EXTRA_DURATION_MS = X + "DURATION_MS"
    private const val EXTRA_PAUSED = X + "PAUSED"
    private const val EXTRA_ACCENT = X + "ACCENT"
    private const val EXTRA_RINGS = X + "RINGS"
    private const val EXTRA_RING_COLORS = X + "RING_COLORS"
    private const val EXTRA_RING_LABELS = X + "RING_LABELS"
    private const val EXTRA_TIMEOUT_SEC = X + "TIMEOUT_SEC"
    private const val EXTRA_ALERT = X + "ALERT"
    private const val EXTRA_DATA = X + "DATA"
    private fun actionLabel(n: Int) = X + "ACTION_${n}_LABEL"
    private fun actionIntent(n: Int) = X + "ACTION_${n}_INTENT"
    private fun actionRole(n: Int) = X + "ACTION_${n}_ROLE"
    private const val OP_START = "START"
    private const val OP_UPDATE = "UPDATE"
    private const val OP_END = "END"
    // endregion

    // region Modèles santé de Notch (HealthRenderer.Keys)
    private const val K_TYPE = "sh.type"
    private const val K_EXERCISE = "sh.exercise"
    private const val K_SET = "sh.set"
    private const val K_LOAD = "sh.load"
    private const val K_DONE = "sh.done"
    private const val K_TOTAL = "sh.total"
    private const val K_RESTING = "sh.resting"
    private const val K_HEART_RATE = "sh.hr"
    private const val K_DISTANCE = "sh.distance"
    private const val K_PACE = "sh.pace"
    private const val K_ELEVATION = "sh.elevation"
    private const val K_ROUTE_PROGRESS = "sh.route_progress"
    private const val K_TIME = "sh.time"
    private const val K_LABEL_DISTANCE = "sh.label.distance"
    private const val K_LABEL_PACE = "sh.label.pace"
    private const val K_LABEL_TIME = "sh.label.time"
    private const val K_LABEL_ELEVATION = "sh.label.elevation"
    private const val K_LABEL_REST = "sh.label.rest"
    private const val K_LABEL_INHALE = "sh.label.inhale"
    private const val K_LABEL_EXHALE = "sh.label.exhale"
    private const val K_LABEL_PAUSED = "sh.label.paused"
    // endregion

    private const val WORKOUT = "workout"
    private const val HIKE = "hike"
    private const val MEDITATION = "meditation"
    private const val RINGS = "rings"

    /** Mises à jour GPS : une toutes les ~5 s suffit, l'île fait tourner le chrono elle-même. */
    private const val HIKE_INTERVAL_MS = 5_000L
    /** Un START renvoyé de temps en temps fait réapparaître l'activité si Notch a redémarré entre-temps. */
    private const val RESTART_MS = 60_000L
    private const val HEART_INTERVAL_MS = 5_000L
    /** Pouls plus ancien : la montre ne l'envoie plus (app en arrière-plan), on ne l'affiche pas figé. */
    private const val HEART_FRESH_MS = 30_000L
    private const val INSTALL_CHECK_MS = 30_000L
    private const val MEDITATION_HUE = 0xFF7B8CDE.toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Un seul fil pour les envois calculés à part : Notch les reçoit dans l'ordre. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val ready = CountDownLatch(1)

    /** Activités envoyées à Notch et pas encore terminées, avec l'heure de leur dernier START. */
    private val live = HashMap<String, Long>()
    private var lastHikeAt = 0L
    private var lastHikePaused: Boolean? = null
    private var heartJob: Job? = null
    @Volatile private var bpm: Int? = null
    @Volatile private var bpmAt = 0L
    private var lastHeartPush = 0L
    private var installedAt = 0L
    private var installed = false

    /**
     * Fin de SuperHealthApp.onCreate : le fournisseur de résumé attend ce signal avant de lire les dépôts.
     * Rien d'autre à relancer : une séance en cours revient par SessionNotification.update (appelé au démarrage par
     * WorkoutApp), une randonnée quand son suivi GPS repart (une sortie oubliée ne réapparaît pas à chaque démarrage).
     */
    fun init() {
        ready.countDown()
    }

    fun awaitReady(timeoutMs: Long): Boolean = ready.await(timeoutMs, TimeUnit.MILLISECONDS)

    @Synchronized
    fun notchInstalled(ctx: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (installedAt == 0L || now - installedAt > INSTALL_CHECK_MS) {
            installed = runCatching { ctx.packageManager.getPackageInfo(NOTCH_PACKAGE, 0); true }.getOrDefault(false)
            installedAt = now
        }
        return installed
    }

    /**
     * Réglage actif, Notch installé, et permission accordée : Android ne l'accorde que si Notch était installé avant
     * SuperHealth (ou à la mise à jour suivante de SuperHealth).
     */
    private fun enabled(ctx: Context): Boolean =
        SuperPrefs.settings.value.notchIsland && notchInstalled(ctx) &&
            ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Réglage changé : tout retirer de l'île, ou y remettre ce qui est en cours. */
    fun onSettingChanged(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            if (!SuperPrefs.settings.value.notchIsland) endAll(app)
            else {
                runCatching { onWorkout(app, WorkoutApp.store.state.value) }
                runCatching { onHike(app, Hiking.session.value) }
            }
        }
    }

    @Synchronized
    private fun endAll(ctx: Context) {
        live.keys.toList().forEach { end(ctx, it) }
        heartJob?.cancel(); heartJob = null
    }

    // region Séance guidée

    /**
     * Appelé à chaque changement de séance (SessionNotification.update) : exercice et série à faire, compte à rebours
     * du repos avec « +15 s » et « Passer », chronomètre de séance, pouls en direct de la montre.
     */
    @Synchronized
    fun onWorkout(ctx: Context, st: AppState) {
        val s = st.session
        if (s == null || !enabled(ctx)) {
            heartJob?.cancel(); heartJob = null
            if (WORKOUT in live) {
                end(ctx, WORKOUT)
                // Séance finie (et non réglage coupé) : les anneaux du jour un instant.
                if (s == null) showRings(ctx)
            }
            return
        }
        listenHeart(ctx)
        val now = System.currentTimeMillis()
        val done = s.exercises.sumOf { e -> e.sets.count { it.done } }
        val total = s.exercises.sumOf { it.sets.size }
        // même série « à faire » que la notification de séance : à partir de l'exercice affiché
        val next = (s.exercises.indices.drop(s.current) + s.exercises.indices.take(s.current)).firstNotNullOfOrNull { i ->
            s.exercises[i].sets.indexOfFirst { !it.done }.takeIf { it >= 0 }?.let { i to it }
        }
        var exercise = ""
        var setLabel = tr("Toutes les séries sont faites")
        var load = ""
        if (next != null) {
            val ex = s.exercises[next.first]
            val set = ex.sets[next.second]
            val work = ex.sets.filter { it.type != SetType.WARMUP }
            exercise = WorkoutApp.exerciseName(ex.exerciseId)
            setLabel = if (set.type == SetType.WARMUP) tr("Échauffement") else trf("Série %1\$s/%2\$s", work.indexOf(set) + 1, work.size)
            val wgt = set.weight ?: set.autoWeight
            val reps = set.reps ?: set.autoReps
            load = listOfNotNull(wgt?.let { "${clean(st.settings.w(it))} ${st.settings.unit.label.tr()}" }, reps?.let { trf("× %1\$s", it) }).joinToString(" ")
        }
        val paused = s.pausedAt != null
        val restEnd = s.restEndsAt
        val resting = restEnd != null && restEnd > now && !paused
        val elapsed = s.elapsedSec(now)
        val heart = bpm?.takeIf { SystemClock.elapsedRealtime() - bpmAt < HEART_FRESH_MS }
        val data = Bundle().apply {
            putString(K_TYPE, WORKOUT)
            putString(K_EXERCISE, exercise.ifBlank { s.name })
            putString(K_SET, setLabel)
            if (load.isNotBlank()) putString(K_LOAD, load)
            putInt(K_DONE, done)
            putInt(K_TOTAL, total)
            putBoolean(K_RESTING, resting)
            heart?.let { putInt(K_HEART_RATE, it) }
            putString(K_LABEL_REST, tr("Repos"))
            putString(K_LABEL_PAUSED, tr("En pause"))
        }
        send(ctx, opFor(WORKOUT), WORKOUT, "training:session") { i ->
            i.putExtra(EXTRA_KIND, "workout")
            i.putExtra(EXTRA_TITLE, s.name)
            i.putExtra(EXTRA_SUBTITLE, listOf(exercise, setLabel).filter { it.isNotBlank() }.joinToString(" · "))
            // Texte figé du chrono de séance : affiché tel quel pendant une pause.
            i.putExtra(EXTRA_SHORT_TEXT, clock(elapsed))
            i.putExtra(EXTRA_PROGRESS, if (total > 0) done.toFloat() / total else 0f)
            i.putExtra(EXTRA_PAUSED, paused)
            if (resting) {
                i.putExtra(EXTRA_CHRONOMETER_BASE, restEnd)
                i.putExtra(EXTRA_COUNTDOWN, true)
                i.putExtra(EXTRA_DURATION_MS, s.restDuration.coerceAtLeast(1) * 1000L)
                // Mêmes actions que la notification de séance (SessionActionReceiver modifie la séance directement).
                actions(i, tr("+15 s") to sessionAction(ctx, SessionNotification.ACTION_ADD), tr("Passer") to sessionAction(ctx, SessionNotification.ACTION_SKIP))
            } else {
                // Chrono de séance, pauses déduites : base telle que maintenant − base = durée effective.
                i.putExtra(EXTRA_CHRONOMETER_BASE, now - elapsed * 1000)
                i.putExtra(EXTRA_COUNTDOWN, false)
                actions(i)
            }
            i.putExtra(EXTRA_DATA, data)
        }
    }

    private fun sessionAction(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, action.hashCode(), Intent(ctx, SessionActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Pouls de la montre pendant la séance. LiveHeart garde sa règle d'économie : mode direct de la montre seulement
     * quand SuperHealth est au premier plan ; en arrière-plan, l'île n'affiche simplement pas de pouls.
     */
    private fun listenHeart(ctx: Context) {
        if (heartJob?.isActive == true || !runCatching { LiveHeart.available() }.getOrDefault(false)) return
        val app = ctx.applicationContext
        heartJob = scope.launch {
            LiveHeart.samples.collect { sample ->
                val value = sample.bpm ?: return@collect
                bpm = value; bpmAt = SystemClock.elapsedRealtime()
                val push = synchronized(this@NotchBridge) {
                    (bpmAt - lastHeartPush >= HEART_INTERVAL_MS).also { if (it) lastHeartPush = bpmAt }
                }
                if (push) runCatching { onWorkout(app, WorkoutApp.store.state.value) }
            }
        }
    }

    // endregion

    // region Randonnée (suivi GPS)

    /** Appelé à chaque changement de la sortie suivie (Hiking.setSession) et quand le suivi GPS démarre ou s'arrête. */
    @Synchronized
    fun onHike(ctx: Context, s: Hiking.Session?) {
        if (s == null || !enabled(ctx)) {
            lastHikePaused = null
            if (HIKE in live) {
                end(ctx, HIKE)
                if (s == null) showRings(ctx)
            }
            return
        }
        val paused = s.paused || !Hiking.tracking.value
        val clockNow = SystemClock.elapsedRealtime()
        // Un point GPS toutes les ~3 s : on n'envoie qu'environ toutes les 5 s, sauf pause / reprise.
        if (HIKE in live && paused == lastHikePaused && clockNow - lastHikeAt < HIKE_INTERVAL_MS) return
        lastHikeAt = clockNow
        lastHikePaused = paused
        val op = opFor(HIKE)
        val gps = Hiking.gpsError.value.orEmpty()
        val app = ctx.applicationContext
        // Appelé sur le fil principal (GPS, sous le verrou de Hiking) : la géométrie (jusqu'à 50 000 points, et le
        // parcours) se calcule à part, dans l'ordre des appels.
        scope.launch(serial) { runCatching { sendHike(app, s, paused, op, gps) } }
    }

    private fun sendHike(ctx: Context, s: Hiking.Session, paused: Boolean, op: String, gps: String) {
        val now = System.currentTimeMillis()
        val elapsed = Hiking.elapsed(s, now)
        val meters = HikeGeometry.length(s.points)
        val km = meters / 1000.0
        val pace = if (km >= 0.05) (elapsed / km).toLong() else null
        val ascent = HikeGeometry.ascent(s.points)
        val route = s.route
        val last = s.points.lastOrNull()
        val routeProgress = if (route != null && last != null) {
            HikeGeometry.position(route.points, last)?.let { p ->
                val length = p.progress + p.remaining
                if (length > 0) (p.progress / length).toFloat() else null
            }
        } else null
        val data = Bundle().apply {
            putString(K_TYPE, HIKE)
            putString(K_DISTANCE, "${formatNumber(km, if (km < 10) 2 else 1)} km")
            pace?.let { putString(K_PACE, "${clock(it)} /km") }
            ascent?.let { putString(K_ELEVATION, "+${formatNumber(it)} m") }
            routeProgress?.let { putFloat(K_ROUTE_PROGRESS, it) }
            putString(K_LABEL_DISTANCE, tr("Distance"))
            putString(K_LABEL_PACE, tr("Allure"))
            putString(K_LABEL_TIME, tr("Durée"))
            putString(K_LABEL_ELEVATION, tr("Dénivelé"))
            putString(K_LABEL_PAUSED, tr("En pause"))
        }
        // Sortie terminée pendant le calcul : ne pas la faire revenir dans l'île.
        synchronized(this) {
            if (HIKE !in live) return
            sendHikeNow(ctx, op, route?.name, gps, elapsed, now, paused, routeProgress, data)
        }
    }

    private fun sendHikeNow(ctx: Context, op: String, name: String?, gps: String, elapsed: Long, now: Long, paused: Boolean, routeProgress: Float?, data: Bundle) {
        send(ctx, op, HIKE, "hiking/live") { i ->
            i.putExtra(EXTRA_KIND, "workout")
            i.putExtra(EXTRA_TITLE, name ?: tr("Ma randonnée"))
            i.putExtra(EXTRA_SUBTITLE, gps)
            i.putExtra(EXTRA_SHORT_TEXT, clock(elapsed))
            i.putExtra(EXTRA_CHRONOMETER_BASE, now - elapsed * 1000)
            i.putExtra(EXTRA_COUNTDOWN, false)
            i.putExtra(EXTRA_PAUSED, paused)
            routeProgress?.let { i.putExtra(EXTRA_PROGRESS, it) }
            i.putExtra(EXTRA_DATA, data)
        }
    }

    // endregion

    // region Méditation

    /** Début, pause, reprise et fin de la séance (MeditationPlayScreen) : orbe de respiration et temps restant. */
    @Synchronized
    fun onMeditation(ctx: Context, title: String, totalSec: Int, elapsedSec: Int, running: Boolean, finished: Boolean) {
        if (finished || !enabled(ctx)) {
            if (MEDITATION in live) {
                end(ctx, MEDITATION)
                if (finished) showRings(ctx)
            }
            return
        }
        val left = (totalSec - elapsedSec).coerceAtLeast(0)
        val now = System.currentTimeMillis()
        val data = Bundle().apply {
            putString(K_TYPE, MEDITATION)
            putString(K_LABEL_INHALE, tr("Inspirez"))
            putString(K_LABEL_EXHALE, tr("Expirez"))
            putString(K_LABEL_PAUSED, tr("En pause"))
        }
        send(ctx, opFor(MEDITATION), MEDITATION, "home") { i ->
            i.putExtra(EXTRA_KIND, "timer")
            i.putExtra(EXTRA_TITLE, title)
            i.putExtra(EXTRA_SUBTITLE, tr("Méditation"))
            i.putExtra(EXTRA_SHORT_TEXT, clock(left.toLong()))
            i.putExtra(EXTRA_CHRONOMETER_BASE, now + left * 1000L)
            i.putExtra(EXTRA_COUNTDOWN, true)
            i.putExtra(EXTRA_DURATION_MS, totalSec.coerceAtLeast(1) * 1000L)
            i.putExtra(EXTRA_PAUSED, !running)
            i.putExtra(EXTRA_PROGRESS, if (totalSec > 0) elapsedSec.toFloat() / totalSec else 0f)
            i.putExtra(EXTRA_ACCENT, MEDITATION_HUE)
            // Une séance abandonnée sans fermer l'écran ne reste pas dans l'île au-delà de sa durée.
            i.putExtra(EXTRA_TIMEOUT_SEC, left + 10 * 60)
            i.putExtra(EXTRA_DATA, data)
        }
    }

    /** L'écran de méditation se ferme (fin, retour, abandon). */
    @Synchronized
    fun onMeditationClosed(ctx: Context) {
        if (MEDITATION in live) end(ctx, MEDITATION)
    }

    // endregion

    // region Rappel médication

    /** Prise à l'heure : bandeau de l'île avec « Pris » et « Plus tard » (les mêmes actions que la notification). */
    fun onMedDue(ctx: Context, d: Meds.Dose, day: Long, taken: PendingIntent, later: PendingIntent) {
        if (!enabled(ctx)) return
        val time = "%02d:%02d".format(d.minute / 60, d.minute % 60)
        val data = Bundle().apply {
            putString(K_TYPE, "meds")
            putString(K_TIME, time)
        }
        send(ctx, OP_START, medId(d.key, day), "meds") { i ->
            i.putExtra(EXTRA_KIND, "custom")
            i.putExtra(EXTRA_TITLE, d.med.name)
            i.putExtra(EXTRA_SUBTITLE, d.med.dose.ifBlank { tr("C'est l'heure") })
            i.putExtra(EXTRA_SHORT_TEXT, time)
            i.putExtra(EXTRA_ALERT, true)
            // « Plus tard » renotifie au bout du délai du plugin : l'île n'a pas à garder le rappel plus longtemps.
            i.putExtra(EXTRA_TIMEOUT_SEC, 30 * 60)
            actions(i, tr("Pris") to taken, tr("Plus tard") to later, positiveFirst = true)
            i.putExtra(EXTRA_DATA, data)
        }
    }

    /** Prise cochée ou repoussée : le rappel quitte l'île. */
    fun onMedHandled(ctx: Context, key: String, day: Long) {
        if (!notchInstalled(ctx)) return
        send(ctx, OP_END, medId(key, day), "meds")
    }

    /** Identifiant stable et court par prise et par jour ([A-Za-z0-9._-], 64 caractères au plus). */
    private fun medId(key: String, day: Long) = "med-$day-" + Integer.toHexString(key.hashCode())

    // endregion

    // region Anneaux du jour

    /** Après une séance, une sortie ou une méditation : calories, pas et protéines du jour, quelques secondes. */
    private fun showRings(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            if (!enabled(app)) return@launch
            val t = runCatching { NotchSummary.today(app) }.getOrNull() ?: return@launch
            fun frac(v: Long?, goal: Long?) = if (v != null && goal != null && goal > 0) (v.toFloat() / goal).coerceIn(0f, 1f) else 0f
            val kcal = t[NotchSummary.KCAL_IN] as? Long
            val steps = t[NotchSummary.STEPS] as? Long
            val protein = t[NotchSummary.PROTEIN_G] as? Long
            send(app, OP_START, RINGS, "home") { i ->
                i.putExtra(EXTRA_KIND, "custom")
                i.putExtra(EXTRA_TITLE, tr("Ma journée"))
                steps?.let { i.putExtra(EXTRA_SHORT_TEXT, formatNumber(it.toDouble())) }
                i.putExtra(EXTRA_RINGS, floatArrayOf(
                    frac(kcal, t[NotchSummary.KCAL_TARGET] as? Long),
                    frac(steps, t[NotchSummary.STEP_GOAL] as? Long),
                    frac(protein, t[NotchSummary.PROTEIN_TARGET_G] as? Long),
                ))
                i.putExtra(EXTRA_RING_COLORS, intArrayOf(
                    CaloriesColor.toArgb(),
                    HealthHues.Activity.toArgb(),
                    ProteinColor.toArgb(),
                ))
                i.putExtra(EXTRA_RING_LABELS, arrayOf(
                    "${formatNumber((kcal ?: 0L).toDouble())} kcal",
                    trf("%1\$s pas", formatNumber((steps ?: 0L).toDouble())),
                    trf("%1\$s g prot.", formatNumber((protein ?: 0L).toDouble())),
                ))
                i.putExtra(EXTRA_TIMEOUT_SEC, 20)
                i.putExtra(EXTRA_DATA, Bundle().apply { putString(K_TYPE, RINGS) })
            }
        }
    }

    // endregion

    // region Envoi

    /** START la première fois (ou après [RESTART_MS], si Notch a redémarré), UPDATE ensuite. */
    private fun opFor(id: String): String {
        val now = SystemClock.elapsedRealtime()
        val started = live[id]
        return if (started == null || now - started > RESTART_MS) { live[id] = now; OP_START } else OP_UPDATE
    }

    private fun end(ctx: Context, id: String) {
        live.remove(id)
        send(ctx, OP_END, id, "home")
    }

    /**
     * Jusqu'à trois boutons ; sans boutons, un libellé vide efface ceux de la mise à jour précédente (les boutons du repos).
     */
    private fun actions(i: Intent, vararg buttons: Pair<String, PendingIntent>, positiveFirst: Boolean = false) {
        if (buttons.isEmpty()) { i.putExtra(actionLabel(1), ""); return }
        buttons.take(3).forEachIndexed { n, (label, pi) ->
            i.putExtra(actionLabel(n + 1), label)
            i.putExtra(actionIntent(n + 1), pi)
            i.putExtra(actionRole(n + 1), if (positiveFirst && n == 0) "positive" else "normal")
        }
    }

    /** Écran de SuperHealth ouvert au toucher de l'île ; son créateur (SuperHealth) est aussi notre identité pour Notch. */
    private fun open(ctx: Context, route: String): PendingIntent = PendingIntent.getActivity(
        ctx, REQUEST_BASE + (route.hashCode() and 0xFF),
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun send(ctx: Context, op: String, id: String, route: String, fill: (Intent) -> Unit = {}) {
        val intent = Intent(ACTION).setComponent(ComponentName(NOTCH_PACKAGE, RECEIVER))
            .putExtra(EXTRA_VERSION, 1)
            .putExtra(EXTRA_OP, op)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_CONTENT_INTENT, open(ctx, route))
        fill(intent)
        runCatching {
            // Android 14+ : Notch vérifie alors que l'expéditeur est bien l'app nommée par le PendingIntent.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ctx.sendBroadcast(intent, null, BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle())
            } else {
                ctx.sendBroadcast(intent)
            }
        }
    }

    /** 75 → « 1:15 », 4 000 → « 1:06:40 ». */
    private fun clock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    private const val REQUEST_BASE = 0x4E00

    // endregion
}
