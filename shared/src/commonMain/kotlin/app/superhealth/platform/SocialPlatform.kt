package app.superhealth.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Identité EC P-256 ; seule la clé publique X.509/SPKI quitte le trousseau. */
data class SigningIdentity(val privateKey: ByteArray, val publicKey: ByteArray)
expect fun generateSigningIdentity(): SigningIdentity
expect fun signIdentity(privateKey: ByteArray, message: ByteArray): ByteArray
expect fun verifyIdentity(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean
expect fun qrBitmap(text: String, pixels: Int = 720): Bitmap
expect fun clipboardText(): String
expect fun copyText(text: String)

/** Vidéo native en flux ; aucun fichier de catalogue n'est exécuté. */
@Composable
expect fun NativeVideo(url: String, playing: Boolean, controls: Boolean = true, modifier: Modifier = Modifier)
expect class LocalAudio() {
    val isPlaying: Boolean
    var volume: Float
    var looping: Boolean
    fun load(path: String)
    fun play()
    fun pause()
    fun release()
}

object MessageDigest {
    fun getInstance(algorithm: String): MessageDigest {
        require(algorithm.replace("-", "").equals("SHA256", true))
        return this
    }
    fun digest(bytes: ByteArray) = Sha256.digest(bytes)
}

enum class TimeUnit(private val factor: Long) {
    MILLISECONDS(1), SECONDS(1000), MINUTES(60_000), HOURS(3_600_000), DAYS(86_400_000);
    fun toMillis(value: Long) = value * factor
}
object Collections {
    fun <T> swap(list: MutableList<T>, a: Int, b: Int) { val t = list[a]; list[a] = list[b]; list[b] = t }
}
val String.Companion.CASE_INSENSITIVE_ORDER: Comparator<String> get() = compareBy { it.lowercase() }

/** URL d'invitation : décodage de paramètres uniquement, sans navigation ni exécution. */
class Uri private constructor(private val raw: String) {
    val scheme: String? get()=runCatching{io.ktor.http.Url(raw).protocol.name}.getOrNull()
    val host: String? get()=runCatching{io.ktor.http.Url(raw).host}.getOrNull()
    fun getQueryParameter(name: String): String?=runCatching{io.ktor.http.Url(raw).parameters[name]}.getOrNull()
    companion object { fun parse(raw: String)=Uri(raw) }
}
