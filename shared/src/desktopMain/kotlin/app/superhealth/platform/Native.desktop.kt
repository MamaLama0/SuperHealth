package app.superhealth.platform

actual fun inflateRaw(data: ByteArray, size: Int): ByteArray {
    val inf = java.util.zip.Inflater(true)
    inf.setInput(data)
    val out = java.io.ByteArrayOutputStream(if (size > 0) size else data.size * 3)
    val buf = ByteArray(64 * 1024)
    while (!inf.finished()) {
        val n = inf.inflate(buf)
        if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
        out.write(buf, 0, n)
    }
    inf.end()
    return out.toByteArray()
}

actual fun secureRandomBytes(n: Int): ByteArray = ByteArray(n).also { java.security.SecureRandom().nextBytes(it) }

actual fun appVersion(): String = "1.7.9"

actual fun scheduleNotification(id: String, title: String, body: String, atMs: Long, sound: Boolean, category: String?) {
    println("notification $id à $atMs : $title — $body")
}

actual fun cancelNotification(id: String) = Unit
actual fun cancelNotificationsWithPrefix(prefix: String) = Unit
actual fun requestNotificationPermission() = Unit
actual fun playBeep() { java.awt.Toolkit.getDefaultToolkit().beep() }

private val secrets get() = SharedPreferences.open("secrets_preview")
actual fun keychainGet(name: String): String? = secrets.getString(name, null)
actual fun keychainSet(name: String, value: String) { secrets.edit().putString(name, value).apply() }
actual fun keychainDelete(name: String) { secrets.edit().remove(name).apply() }
actual fun deviceIdentifier(): String? = "preview-" + java.lang.System.getProperty("user.name")

actual fun keepScreenOn(on: Boolean) = Unit

actual fun writeWidgetSnapshot(json: String) { File(appDataDir(), "widget.json").writeText(json) }
actual fun showSessionActivity(s: SessionActivity?) = Unit
actual fun setAppIcon(name: String?) = Unit

actual fun setWindowAppearance(dark: Boolean?) = Unit

actual fun deviceAuthAvailable(): Boolean = false

actual fun deviceAuthenticate(reason: String, onDone: (Boolean) -> Unit) = onDone(true)
