package app.superhealth.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Aperçu sur ordinateur : données dans ~/.superhealth-preview, assets lus dans le dossier « assets » du projet. */
actual fun appDataDir(): String = java.lang.System.getProperty("superhealth.data") ?: (java.lang.System.getProperty("user.home") + "/.superhealth-preview")

actual fun appCacheDir(): String = appDataDir() + "/cache"

private val assetRoot: java.io.File by lazy {
    java.lang.System.getProperty("superhealth.assets")?.let { java.io.File(it) }
        ?: generateSequence(java.io.File("").absoluteFile) { it.parentFile }.map { java.io.File(it, "assets") }.firstOrNull { java.io.File(it, "db").isDirectory }
        ?: java.io.File("assets")
}

actual fun readAsset(path: String): ByteArray? = java.io.File(assetRoot, path).takeIf { it.isFile }?.readBytes()

actual fun listAssets(dir: String): List<String> = java.io.File(assetRoot, dir).list()?.toList().orEmpty()

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual fun openUrl(url: String) { println("openUrl: $url") }

actual fun shareText(text: String) { println("share: $text") }

actual fun shareFile(name: String, mime: String, bytes: ByteArray) {
    val f = java.io.File(appDataDir(), "exports/$name"); f.parentFile.mkdirs(); f.writeBytes(bytes); println("export: $f")
}

actual fun haptic(strong: Boolean) = Unit

actual fun deviceDescription(): String = "Aperçu ordinateur · " + java.lang.System.getProperty("os.name")

actual fun systemLanguage(): String = java.lang.System.getProperty("superhealth.lang") ?: java.util.Locale.getDefault().language

actual inline fun <T> synchronized(lock: Any, block: () -> T): T = kotlin.synchronized(lock, block)
