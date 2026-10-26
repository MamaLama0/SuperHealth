package app.superhealth.platform

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

actual fun inflateRaw(data: ByteArray, size: Int): ByteArray {
    var cap = if (size > 0) size else maxOf(1024, data.size * 4)
    while (true) {
        val out = ByteArray(cap)
        val result = memScoped {
            val strm = alloc<z_stream>()
            check(inflateInit2_(strm.ptr, -15, zlibVersion()?.toKString() ?: "1.2.11", sizeOf<z_stream>().convert()) == Z_OK) { "zlib" }
            try {
                data.usePinned { src ->
                    out.usePinned { dst ->
                        strm.next_in = if (data.isEmpty()) null else src.addressOf(0).reinterpret<UByteVar>()
                        strm.avail_in = data.size.convert()
                        strm.next_out = dst.addressOf(0).reinterpret<UByteVar>()
                        strm.avail_out = cap.convert()
                        val r = inflate(strm.ptr, Z_FINISH)
                        if (r == Z_STREAM_END) strm.total_out.toInt() else -1
                    }
                }
            } finally { inflateEnd(strm.ptr) }
        }
        if (result >= 0) return out.copyOf(result)
        require(cap < 512 * 1024 * 1024) { "archive trop volumineuse" }
        cap *= 2
    }
}

actual fun secureRandomBytes(n: Int): ByteArray {
    val b = ByteArray(n)
    if (n > 0) b.usePinned { SecRandomCopyBytes(kSecRandomDefault, n.convert(), it.addressOf(0)) }
    return b
}

actual fun appVersion(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "1.0"

actual fun scheduleNotification(id: String, title: String, body: String, atMs: Long, sound: Boolean, category: String?) {
    val content = UNMutableNotificationContent()
    content.setTitle(title)
    content.setBody(body)
    if (sound) content.setSound(UNNotificationSound.defaultSound)
    category?.let { content.setCategoryIdentifier(it) }
    val delay = ((atMs - nowMs()) / 1000.0).coerceAtLeast(1.0)
    val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(delay, false)
    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(
        UNNotificationRequest.requestWithIdentifier(id, content, trigger), null)
}

actual fun cancelNotification(id: String) {
    val c = UNUserNotificationCenter.currentNotificationCenter()
    c.removePendingNotificationRequestsWithIdentifiers(listOf(id))
    c.removeDeliveredNotificationsWithIdentifiers(listOf(id))
}

actual fun cancelNotificationsWithPrefix(prefix: String) {
    val c = UNUserNotificationCenter.currentNotificationCenter()
    c.getPendingNotificationRequestsWithCompletionHandler { list ->
        val ids = list.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }.filter { it.startsWith(prefix) }
        if (ids.isNotEmpty()) c.removePendingNotificationRequestsWithIdentifiers(ids)
    }
}

actual fun requestNotificationPermission() {
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { _, _ -> }
}

/** Son « Tink » du système (1057), proche du bip de fin de repos de l'app Android. */
actual fun playBeep() = AudioServicesPlaySystemSound(1057u)

actual fun keepScreenOn(on: Boolean) { platform.UIKit.UIApplication.sharedApplication.idleTimerDisabled = on }

/** Les widgets WidgetKit (extension à venir) relisent le groupe d'apps ; rien à faire tant qu'ils n'existent pas. */
actual fun refreshWidgets() = Unit

actual fun setWindowAppearance(dark: Boolean?) {
    val style = when (dark) {
        null -> platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        true -> platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleDark
        false -> platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleLight
    }
    platform.UIKit.UIApplication.sharedApplication.windows.forEach { (it as? platform.UIKit.UIWindow)?.overrideUserInterfaceStyle = style }
}
