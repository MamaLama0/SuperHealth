package app.superhealth.training.ui

import app.superhealth.platform.*

/**
 * Fin du repos (RestTimerSoundChannel de MacroFactor Workouts) : notification locale programmée avec le son de l'app,
 * affichée même app ouverte (bandeau et son), comme l'alarme système d'Android.
 */
object RestTimer {
    private const val ID = "rest"

    fun schedule(ctx: Context, at: Long, sound: Boolean) {
        cancelNotification(ID)
        scheduleNotification(ID, tr("Repos terminé"), tr("Série suivante"), at, sound)
    }

    fun cancel(ctx: Context) = cancelNotification(ID)

    fun notifyDone(ctx: Context, sound: Boolean) {
        cancelNotification(ID)
        if (sound) beep(ctx)
        haptic(strong = true)
    }

    fun beep(ctx: Context) = playBeep()
}
