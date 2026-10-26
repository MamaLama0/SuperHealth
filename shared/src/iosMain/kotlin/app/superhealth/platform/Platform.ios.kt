package app.superhealth.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.preferredLanguages
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import platform.posix.memcpy

actual fun appDataDir(): String {
    val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
    return "$base/SuperHealth"
}

actual fun appCacheDir(): String =
    (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String) + "/SuperHealth"

private val assetRoot: String get() = (NSBundle.mainBundle.resourcePath ?: "") + "/assets"

actual fun readAsset(path: String): ByteArray? = NSData.dataWithContentsOfFile("$assetRoot/$path")?.toByteArray()

actual fun listAssets(dir: String): List<String> =
    NSFileManager.defaultManager.contentsOfDirectoryAtPath("$assetRoot/$dir", null)?.map { it as String }.orEmpty()

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual fun openUrl(url: String) {
    val u = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(u, emptyMap<Any?, Any>(), null)
}

internal fun topController(): UIViewController? {
    val window = UIApplication.sharedApplication.windows.firstOrNull { (it as UIWindow).isKeyWindow() } as? UIWindow
        ?: UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
    var vc = window?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

private fun present(items: List<Any>) {
    val vc = topController() ?: return
    val sheet = UIActivityViewController(items, null)
    sheet.popoverPresentationController?.sourceView = vc.view
    vc.presentViewController(sheet, true, null)
}

actual fun shareText(text: String) = present(listOf(text))

actual fun shareFile(name: String, mime: String, bytes: ByteArray) {
    val path = NSTemporaryDirectory() + name
    val url = NSURL.fileURLWithPath(path)
    bytes.toNSData().writeToURL(url, true)
    present(listOf(url))
}

actual fun haptic(strong: Boolean) {
    UIImpactFeedbackGenerator(if (strong) UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium else UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
}

actual fun deviceDescription(): String = UIDevice.currentDevice.model + " · iOS " + UIDevice.currentDevice.systemVersion

actual fun systemLanguage(): String = (NSLocale.preferredLanguages.firstOrNull() as? String)?.take(2) ?: "fr"

val globalLock = NSRecursiveLock()

actual inline fun <T> synchronized(lock: Any, block: () -> T): T {
    globalLock.lock()
    try { return block() } finally { globalLock.unlock() }
}

fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

fun ByteArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned {
    platform.Foundation.NSData.create(bytes = it.addressOf(0), length = size.toULong())
}
