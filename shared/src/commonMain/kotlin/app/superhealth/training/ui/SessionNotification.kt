package app.superhealth.training.ui

import app.superhealth.platform.*

import app.superhealth.training.data.AppState

/**
 * Séance en cours hors de l'app : sur Android, notification permanente avec chronomètre. Sur iOS ce rôle revient à une
 * Live Activity (extension WidgetKit à venir) ; en attendant, seul le minuteur de repos notifie.
 */
object SessionNotification {
    fun update(ctx: Context, st: AppState) = Unit
}
