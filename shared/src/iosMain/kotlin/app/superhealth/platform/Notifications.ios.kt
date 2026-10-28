package app.superhealth.platform

import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * Notifications locales : bandeau et son même app ouverte (fin du repos), boutons « Pris » / « Plus tard » du rappel
 * médication, ouverture de l'écran concerné au toucher.
 */
private class NotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(center: UNUserNotificationCenter, willPresentNotification: UNNotification,
                                        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit) {
        val id = willPresentNotification.request.identifier
        // fin du repos app ouverte : son et vibration de l'app, sans bandeau par-dessus la séance
        if (id == "rest") { app.superhealth.training.ui.RestTimer.notifyDone(appContext, app.superhealth.training.WorkoutApp.store.state.value.settings.restSound); withCompletionHandler(0u); return }
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound)
    }

    override fun userNotificationCenter(center: UNUserNotificationCenter, didReceiveNotificationResponse: UNNotificationResponse,
                                        withCompletionHandler: () -> Unit) {
        val id = didReceiveNotificationResponse.notification.request.identifier
        when (val action = didReceiveNotificationResponse.actionIdentifier) {
            app.superhealth.meds.Meds.ACTION_TAKEN, app.superhealth.meds.Meds.ACTION_SNOOZE -> app.superhealth.meds.Meds.onAction(appContext, id, action)
            else -> app.superhealth.SuperHealth.openRoute(when {
                id.startsWith("med|") -> "meds"
                id.startsWith("rem|") -> app.superhealth.wellbeing.Reminders.route(id)
                id == "rest" -> "training:session"
                else -> null
            })
        }
        withCompletionHandler()
    }
}

/** Le centre de notifications ne garde qu'une référence faible au délégué : il est gardé en vie ici. */
private val notificationDelegate by lazy { NotificationDelegate() }

fun installNotificationDelegate() {
    val c = UNUserNotificationCenter.currentNotificationCenter()
    c.delegate = notificationDelegate
    val taken = UNNotificationAction.actionWithIdentifier(app.superhealth.meds.Meds.ACTION_TAKEN, app.superhealth.ui.tr("Pris"), UNNotificationActionOptionNone)
    val later = UNNotificationAction.actionWithIdentifier(app.superhealth.meds.Meds.ACTION_SNOOZE, app.superhealth.ui.tr("Plus tard"), UNNotificationActionOptionNone)
    val meds = UNNotificationCategory.categoryWithIdentifier("meds", listOf(taken, later), emptyList<String>(), UNNotificationCategoryOptionNone)
    c.setNotificationCategories(setOf(meds))
}
