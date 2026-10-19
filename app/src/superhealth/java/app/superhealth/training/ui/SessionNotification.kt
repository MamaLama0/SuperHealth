package app.superhealth.training.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import app.superhealth.MainActivity
import nodomain.freeyourgadget.gadgetbridge.R
import app.superhealth.training.WorkoutApp
import app.superhealth.training.data.AppState
import app.superhealth.training.domain.SetType

/**
 * Séance en cours dans le tiroir de notifications (équivalent Android des Live Activities de MacroFactor Workouts) :
 * exercice et série à faire, progression des séries, chronomètre de séance ou compte à rebours du repos,
 * actions « −15 s / +15 s / Passer » pendant le repos.
 */
object SessionNotification {
    private const val CHANNEL = "workout_session"
    private const val ID = 8
    const val ACTION_SKIP = "app.superhealth.training.SKIP_REST"
    const val ACTION_ADD = "app.superhealth.training.ADD_REST"
    const val ACTION_SUB = "app.superhealth.training.SUB_REST"

    fun update(ctx: Context, st: AppState) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val s = st.session ?: run { nm.cancel(ID); return }
        nm.deleteNotificationChannel("session")
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Séance en cours"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            setShowBadge(false); setSound(null, null); enableVibration(false)
        })
        val now = System.currentTimeMillis()
        val app = WorkoutApp
        val done = s.exercises.sumOf { e -> e.sets.count { it.done } }
        val total = s.exercises.sumOf { it.sets.size }
        // prochaine série à faire à partir de l'exercice affiché
        val next = (s.exercises.indices.drop(s.current) + s.exercises.indices.take(s.current)).firstNotNullOfOrNull { i ->
            s.exercises[i].sets.indexOfFirst { !it.done }.takeIf { it >= 0 }?.let { i to it }
        }
        val text = next?.let { (i, si) ->
            val ex = s.exercises[i]
            val set = ex.sets[si]
            val work = ex.sets.filter { it.type != SetType.WARMUP }
            val label = if (set.type == SetType.WARMUP) tr("Échauffement") else trf("Série %1\$s/%2\$s", (work.indexOf(set) + 1), work.size)
            val wgt = set.weight ?: set.autoWeight
            val reps = set.reps ?: set.autoReps
            val load = listOfNotNull(wgt?.let { "${clean(st.settings.w(it))} ${st.settings.unit.label.tr()}" }, reps?.let { trf("× %1\$s", it) }).joinToString(" ")
            listOf("${app.exerciseName(ex.exerciseId)} · $label", load).filter { it.isNotBlank() }.joinToString(" — ")
        } ?: tr("Toutes les séries sont faites")
        val resting = s.restEndsAt != null && s.restEndsAt > now && s.pausedAt == null
        val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "training:session")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(if (resting) tr("Repos") + " · " + s.name else s.name).setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
        // vue personnalisée : gros chronomètre (séance) ou compte à rebours (repos)
        val v = RemoteViews(ctx.packageName, R.layout.notif_session)
        val base = { wall: Long -> SystemClock.elapsedRealtime() + (wall - now) }
        v.setTextViewText(R.id.n_count, "$done/$total")
        v.setTextViewText(R.id.n_text, text)
        v.setProgressBar(R.id.n_progress, total.coerceAtLeast(1), done, false)
        when {
            s.pausedAt != null -> {
                v.setTextViewText(R.id.n_label, tr("En pause") + " · " + s.name)
                v.setChronometer(R.id.n_chrono, base(now - s.elapsedSec(now) * 1000), null, false)
                b.setUsesChronometer(false).setShowWhen(false).setContentTitle(tr("En pause") + " · " + s.name)
            }
            resting -> {
                v.setTextViewText(R.id.n_label, tr("Repos") + " · " + s.name)
                v.setChronometer(R.id.n_chrono, base(s.restEndsAt!!), null, true)
                v.setChronometerCountDown(R.id.n_chrono, true)
                b.setShowWhen(false)
                b.addAction(0, "−15 s", action(ctx, ACTION_SUB)).addAction(0, "+15 s", action(ctx, ACTION_ADD)).addAction(0, tr("Passer"), action(ctx, ACTION_SKIP))
            }
            // chronomètre de séance, pauses déduites
            else -> {
                v.setTextViewText(R.id.n_label, s.name)
                v.setChronometer(R.id.n_chrono, base(s.startTime + s.pausedMs), null, true)
                b.setShowWhen(false)
            }
        }
        b.setCustomContentView(v).setCustomBigContentView(v).setStyle(NotificationCompat.DecoratedCustomViewStyle())
        runCatching { nm.notify(ID, b.build()) }
    }

    private fun action(ctx: Context, a: String) = PendingIntent.getBroadcast(ctx, a.hashCode(),
        Intent(ctx, SessionActionReceiver::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** Actions du minuteur depuis la notification ; l'état est modifié directement dans le Store. */
class SessionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = WorkoutApp
        val now = System.currentTimeMillis()
        val delta = when (intent.action) { SessionNotification.ACTION_ADD -> 15; SessionNotification.ACTION_SUB -> -15; else -> null }
        app.store.update { st ->
            val s = st.session ?: return@update st
            val end = s.restEndsAt ?: return@update st
            val ne = if (delta == null) now else end + delta * 1000L
            if (ne <= now) { RestTimer.cancel(context); st.copy(session = s.copy(restEndsAt = null)) }
            else { RestTimer.schedule(context, ne, st.settings.restSound); st.copy(session = s.copy(restEndsAt = ne, restDuration = s.restDuration + (delta ?: 0))) }
        }
        SessionNotification.update(context, app.store.state.value)
    }
}
