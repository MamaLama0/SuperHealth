package app.superhealth.platform

import app.superhealth.platform.*

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking

/*
 * Flux et réseau de la JVM (InputStream, HttpURLConnection, URI, Base64…) tels que l'app Android les emploie, posés sur
 * ktor (NSURLSession sur iOS). Les appels restent bloquants comme sur Android : ils tournent déjà hors du fil principal.
 */

open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

typealias Closeable = AutoCloseable

open class InputStream(private val data: ByteArray) : Closeable {
    private var pos = 0
    open fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
    open fun read(b: ByteArray): Int = read(b, 0, b.size)
    open fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= data.size) return -1
        val n = minOf(len, data.size - pos)
        data.copyInto(b, off, pos, pos + n); pos += n
        return n
    }
    fun readBytes(): ByteArray = data.copyOfRange(pos, data.size).also { pos = data.size }
    fun available(): Int = data.size - pos
    fun bufferedReader(): Reader = Reader(readBytes().decodeToString())
    fun reader(): Reader = bufferedReader()
    override fun close() = Unit
}

class ByteArrayInputStream(data: ByteArray) : InputStream(data)

class Reader(private val text: String) : Closeable {
    fun readText() = text
    fun readLines() = text.lines()
    fun lineSequence() = text.lineSequence()
    fun useLines(f: (Sequence<String>) -> Unit) = f(text.lineSequence())
    override fun close() = Unit
}

open class OutputStream : Closeable {
    protected val buf = ArrayList<Byte>()
    open fun write(b: ByteArray) { b.forEach { buf.add(it) } }
    open fun write(b: ByteArray, off: Int, len: Int) { for (i in off until off + len) buf.add(b[i]) }
    open fun write(b: Int) { buf.add(b.toByte()) }
    open fun flush() = Unit
    override fun close() = Unit
    fun bytes(): ByteArray = buf.toByteArray()
}

class ByteArrayOutputStream : OutputStream() {
    fun size() = buf.size
    fun toByteArray(): ByteArray = bytes()
    override fun toString(): String = bytes().decodeToString()
    fun toString(charset: String): String = bytes().decodeToString()
    fun reset() = buf.clear()
}

fun String.toByteArray(): ByteArray = encodeToByteArray()
fun String.toByteArray(charset: Any?): ByteArray = encodeToByteArray()
fun String(bytes: ByteArray, charset: Any? = null): String = bytes.decodeToString()

object Base64 {
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    class Encoder internal constructor(private val url: Boolean, private val pad: Boolean = true) {
        fun encodeToString(b: ByteArray): String =
            (if (url) kotlin.io.encoding.Base64.UrlSafe else kotlin.io.encoding.Base64.Default).encode(b).let { if (pad) it else it.trimEnd('=') }
        fun withoutPadding() = Encoder(url, false)
    }
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    class Decoder internal constructor(private val url: Boolean) {
        fun decode(s: String): ByteArray = (if (url) kotlin.io.encoding.Base64.UrlSafe else kotlin.io.encoding.Base64.Default)
            .withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT_OPTIONAL).decode(s)
    }
    fun getEncoder() = Encoder(false)
    fun getUrlEncoder() = Encoder(true)
    fun getDecoder() = Decoder(false)
    fun getUrlDecoder() = Decoder(true)
}

class URI(val raw: String) {
    private val m = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(?://(?:[^@/]*@)?(\\[[^\\]]*\\]|[^:/?#]*)(?::(\\d+))?)?([^?#]*)(?:\\?([^#]*))?").find(raw)
        ?: throw IllegalArgumentException("URI invalide : $raw")
    val scheme: String? get() = m.groupValues[1].ifEmpty { null }
    val host: String? get() = m.groupValues[2].ifEmpty { null }
    val port: Int get() = m.groupValues[3].toIntOrNull() ?: -1
    val path: String? get() = m.groupValues[4]
    val query: String? get() = m.groupValues[5].ifEmpty { null }
    override fun toString() = raw

    /** Adresse relative résolue par rapport à celle-ci (RFC 3986, cas courants). */
    fun resolve(ref: String): URI = URI(when {
        Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(ref) -> ref
        ref.startsWith("//") -> "$scheme:$ref"
        ref.startsWith("/") -> "$scheme://$host" + (if (port > 0) ":$port" else "") + ref
        else -> raw.substringBefore('?').substringBefore('#').substringBeforeLast('/') + "/" + ref.removePrefix("./")
    })
}

internal val httpClient: HttpClient by lazy { HttpClient { install(HttpTimeout); expectSuccess = false } }

class URL(val spec: String) {
    fun openConnection(): HttpURLConnection = HttpURLConnection(spec)
    val host: String? get() = runCatching { URI(spec).host }.getOrNull()
    override fun toString() = spec
}

/** HttpURLConnection : la requête part au premier accès à la réponse (code, flux d'entrée ou d'erreur). */
class HttpURLConnection(private val url: String) {
    var requestMethod = "GET"
    var doOutput = false
    var doInput = true
    var connectTimeout = 15_000
    var readTimeout = 60_000
    var instanceFollowRedirects = true
    var useCaches = false
    private val headers = linkedMapOf<String, String>()
    private val reqBody = OutputStream()
    private var result: Pair<Int, ByteArray>? = null
    private var responseHeaders: Map<String, List<String>> = emptyMap()

    fun setRequestProperty(k: String, v: String) { headers[k] = v }
    fun addRequestProperty(k: String, v: String) { headers[k] = v }
    val outputStream: OutputStream get() = reqBody

    private fun exec(): Pair<Int, ByteArray> = result ?: runBlocking {
        val r = httpClient.request(url) {
            method = HttpMethod.parse(requestMethod)
            timeout { connectTimeoutMillis = connectTimeout.toLong(); requestTimeoutMillis = (connectTimeout + readTimeout).toLong() }
            headers { this@HttpURLConnection.headers.forEach { (k, v) -> append(k, v) } }
            if (doOutput) setBody(reqBody.bytes())
        }
        responseHeaders = r.headers.entries().associate { it.key to it.value }
        r.status.value to r.readRawBytes()
    }.also { result = it }

    val responseCode: Int get() = runCatching { exec().first }.getOrElse { throw IOException(it.message, it) }
    val responseMessage: String get() = responseCode.toString()
    val inputStream: InputStream get() = exec().let { if (it.first >= 400) throw IOException("HTTP ${it.first}") else InputStream(it.second) }
    val errorStream: InputStream? get() = exec().let { if (it.first >= 400) InputStream(it.second) else null }
    val contentLengthLong: Long get() = exec().second.size.toLong()
    fun getHeaderField(name: String): String? = exec().let { responseHeaders.entries.firstOrNull { e -> e.key.equals(name, true) }?.value?.firstOrNull() }
    fun disconnect() = Unit
    fun connect() { exec() }
}

/** Requête simple (corps texte), pour remplacer OkHttp. */
suspend fun httpCall(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: ByteArray? = null,
                     timeoutMs: Long = 60_000): Pair<Int, ByteArray> {
    val r = httpClient.request(url) {
        this.method = HttpMethod.parse(method)
        timeout { requestTimeoutMillis = timeoutMs; connectTimeoutMillis = 20_000 }
        headers { headers.forEach { (k, v) -> append(k, v) } }
        if (body != null) setBody(body)
    }
    return r.status.value to r.readRawBytes()
}
