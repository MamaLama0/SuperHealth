package app.superhealth.platform

import app.superhealth.platform.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull

/*
 * Ce que l'app Android demandait au système (Context, SharedPreferences, File, Toast…), en commun pour iOS et l'aperçu
 * sur ordinateur. Les différences de plateforme sont dans les « expect » en bas de fichier.
 */

/** Dossier privé de l'app (Documents/Application Support sur iOS, ~/.superhealth-preview sur ordinateur). */
expect fun appDataDir(): String
expect fun appCacheDir(): String

/** Contenu d'un fichier des assets de l'app Android (dossier « assets » du paquet iOS). */
expect fun readAsset(path: String): ByteArray?
expect fun listAssets(dir: String): List<String>

expect val ioDispatcher: CoroutineDispatcher

/** Ouvre un lien dans le navigateur (Safari sur iOS). */
expect fun openUrl(url: String)

/** Feuille de partage du système avec un texte ou un fichier. */
expect fun shareText(text: String)
expect fun shareFile(name: String, mime: String, bytes: ByteArray)

/** Retour haptique léger (validation d'une série, minuteur). */
expect fun haptic(strong: Boolean = false)

/** Nom de l'appareil et version du système, pour l'écran « À propos ». */
expect fun deviceDescription(): String

val Dispatchers.IO: CoroutineDispatcher get() = ioDispatcher

// ---- Context ------------------------------------------------------------------------------------

/** Tient lieu de Context : un seul, l'app. */
open class Context {
    val applicationContext: Context get() = this
    val filesDir: File get() = File(appDataDir()).also { it.mkdirs() }
    val cacheDir: File get() = File(appCacheDir()).also { it.mkdirs() }
    val packageName: String get() = "app.superhealth"
    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = SharedPreferences.open(name)

    companion object { const val MODE_PRIVATE = 0 }
}

val appContext = Context()

val LocalContext = staticCompositionLocalOf { appContext }

// ---- SharedPreferences --------------------------------------------------------------------------

/** Préférences clé/valeur gardées en JSON dans le dossier de l'app ; apply() écrit en arrière-plan (la dernière version l'emporte). */
class SharedPreferences private constructor(private val file: File) {
    private val values: MutableMap<String, Any?> = load()
    private val pending = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.CONFLATED)

    init {
        // écriture hors du fil principal : l'interface ne s'arrête plus à chaque réglage touché
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            for (text in pending) runCatching { file.writeText(text) }
        }
    }

    companion object {
        private val opened = mutableMapOf<String, SharedPreferences>()
        fun open(name: String): SharedPreferences = synchronized(opened) {
            opened.getOrPut(name) { SharedPreferences(File(File(appDataDir(), "prefs").also { it.mkdirs() }, "$name.json")) }
        }
    }

    private fun load(): MutableMap<String, Any?> {
        val text = runCatching { file.readText() }.getOrNull() ?: return mutableMapOf()
        val obj = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return mutableMapOf()
        return obj.mapValuesTo(mutableMapOf()) { (_, v) -> decode(v) }
    }

    private fun decode(v: JsonElement): Any? = when (v) {
        is JsonPrimitive -> when {
            v.isString -> v.content
            v.booleanOrNull != null -> v.booleanOrNull
            v.longOrNull != null -> v.longOrNull
            else -> v.doubleOrNull
        }
        else -> runCatching { v.jsonArray.map { (it as JsonPrimitive).content }.toSet() }.getOrNull()
    }

    private fun encode(): String {
        val obj = JsonObject(values.mapValues { (_, v) ->
            when (v) {
                null -> kotlinx.serialization.json.JsonNull
                is String -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                is Set<*> -> kotlinx.serialization.json.JsonArray(v.map { JsonPrimitive(it.toString()) })
                else -> JsonPrimitive(v.toString())
            }
        })
        return obj.toString()
    }

    fun getString(key: String, def: String?): String? = synchronized(this) { values[key] as? String ?: def }
    fun getBoolean(key: String, def: Boolean): Boolean = synchronized(this) { values[key] as? Boolean ?: def }
    fun getLong(key: String, def: Long): Long = synchronized(this) { (values[key] as? Number)?.toLong() ?: def }
    fun getInt(key: String, def: Int): Int = synchronized(this) { (values[key] as? Number)?.toInt() ?: def }
    fun getFloat(key: String, def: Float): Float = synchronized(this) { (values[key] as? Number)?.toFloat() ?: def }
    @Suppress("UNCHECKED_CAST")
    fun getStringSet(key: String, def: Set<String>?): Set<String>? = synchronized(this) { values[key] as? Set<String> ?: def }
    fun contains(key: String): Boolean = synchronized(this) { key in values }
    val all: Map<String, *> get() = synchronized(this) { values.toMap() }

    fun edit(): Editor = Editor()

    inner class Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false
        fun putString(k: String, v: String?) = apply { changes[k] = v }
        fun putBoolean(k: String, v: Boolean) = apply { changes[k] = v }
        fun putLong(k: String, v: Long) = apply { changes[k] = v }
        fun putInt(k: String, v: Int) = apply { changes[k] = v.toLong() }
        fun putFloat(k: String, v: Float) = apply { changes[k] = v.toDouble() }
        fun putStringSet(k: String, v: Set<String>?) = apply { changes[k] = v }
        fun remove(k: String) = apply { removed += k }
        fun clear() = apply { clear = true }
        fun apply() { pending.trySend(merge()) }
        fun commit(): Boolean {
            val text = merge()
            pending.trySend(text) // une écriture d'apply() encore en file ne repasse pas après celle-ci
            return runCatching { file.writeText(text) }.isSuccess
        }

        private fun merge(): String = synchronized(this@SharedPreferences) {
            if (clear) values.clear()
            removed.forEach { values.remove(it) }
            changes.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            encode()
        }
    }
}

/** Écriture groupée façon androidx.core.content.edit { }. */
inline fun SharedPreferences.edit(commit: Boolean = false, block: SharedPreferences.Editor.() -> Unit) {
    val e = edit(); e.block(); if (commit) e.commit() else e.apply()
}

// ---- File ---------------------------------------------------------------------------------------

/** File réduit, sur kotlinx-io. */
class File(val path: String) {
    constructor(parent: File, child: String) : this(parent.path.trimEnd('/') + "/" + child)
    constructor(parent: String, child: String) : this(parent.trimEnd('/') + "/" + child)

    private val p get() = Path(path)
    val name: String get() = path.trimEnd('/').substringAfterLast('/')
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")
    val absolutePath: String get() = path
    val parentFile: File? get() = path.trimEnd('/').substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let(::File)
    val isDirectory: Boolean get() = SystemFileSystem.metadataOrNull(p)?.isDirectory == true
    val isFile: Boolean get() = SystemFileSystem.metadataOrNull(p)?.isRegularFile == true

    fun exists() = SystemFileSystem.exists(p)
    fun length(): Long = SystemFileSystem.metadataOrNull(p)?.size ?: 0L
    fun mkdirs(): Boolean = runCatching { SystemFileSystem.createDirectories(p) }.isSuccess
    fun delete(): Boolean = runCatching { SystemFileSystem.delete(p, mustExist = false) }.isSuccess
    fun deleteRecursively(): Boolean { if (isDirectory) listFiles()?.forEach { it.deleteRecursively() }; return delete() }
    fun listFiles(): Array<File>? = if (!isDirectory) null else runCatching { SystemFileSystem.list(p).map { File(it.toString()) }.toTypedArray() }.getOrNull()
    fun list(): Array<String>? = listFiles()?.map { it.name }?.toTypedArray()
    fun readBytes(): ByteArray = SystemFileSystem.source(p).buffered().use { it.readByteArray() }
    fun readText(): String = readBytes().decodeToString()
    fun writeBytes(b: ByteArray) {
        parentFile?.mkdirs()
        SystemFileSystem.sink(p).buffered().use { it.write(b) }
    }
    fun writeText(t: String) = writeBytes(t.encodeToByteArray())
    fun appendText(t: String) {
        parentFile?.mkdirs()
        SystemFileSystem.sink(p, append = true).buffered().use { it.write(t.encodeToByteArray()) }
    }
    fun renameTo(dest: File): Boolean = runCatching { SystemFileSystem.atomicMove(p, Path(dest.path)) }.isSuccess
    fun copyTo(dest: File, overwrite: Boolean = false): File { if (overwrite || !dest.exists()) dest.writeBytes(readBytes()); return dest }
    fun walk(): Sequence<File> = sequence {
        yield(this@File)
        listFiles()?.forEach { yieldAll(it.walk()) }
    }
    fun lastModified(): Long = 0L

    override fun equals(other: Any?) = other is File && other.path == path
    override fun hashCode() = path.hashCode()
    override fun toString() = path
}

// ---- Petits utilitaires de la JVM ---------------------------------------------------------------

object System {
    fun currentTimeMillis(): Long = nowMs()
    fun nanoTime(): Long = kotlin.time.TimeSource.Monotonic.markNow().let { (it - startMark).inWholeNanoseconds }
    private val startMark = kotlin.time.TimeSource.Monotonic.markNow()
}

@OptIn(ExperimentalUuidApi::class)
class UUID private constructor(private val v: String) {
    companion object {
        fun randomUUID(): UUID = UUID(Uuid.random().toString())
        fun nameUUIDFromBytes(b: ByteArray): UUID = UUID(Uuid.fromByteArray(b.copyOf(16)).toString())
        fun fromString(s: String) = UUID(s)
    }
    override fun toString() = v
}

/** Cache LRU borné par une taille calculée par [sizeOf] (1 par entrée par défaut). */
open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>()
    private var size = 0
    protected open fun sizeOf(key: K, value: V): Int = 1
    fun get(key: K): V? = synchronized(this) { map.remove(key)?.also { map[key] = it } }
    fun put(key: K, value: V): V? = synchronized(this) {
        val old = map.remove(key)
        if (old != null) size -= sizeOf(key, old)
        map[key] = value; size += sizeOf(key, value)
        while (size > maxSize && map.isNotEmpty()) {
            val k = map.keys.first(); val v = map.remove(k)!!; size -= sizeOf(k, v)
        }
        old
    }
    fun evictAll() = synchronized(this) { map.clear(); size = 0 }
    fun snapshot(): Map<K, V> = synchronized(this) { LinkedHashMap(map) }
}

typealias ConcurrentHashMap<K, V> = SyncMap<K, V>

/** HashMap protégée par un verrou (remplace ConcurrentHashMap). */
class SyncMap<K, V> : MutableMap<K, V> {
    private val m = HashMap<K, V>()
    override val size get() = synchronized(this) { m.size }
    override fun isEmpty() = synchronized(this) { m.isEmpty() }
    override fun containsKey(key: K) = synchronized(this) { m.containsKey(key) }
    override fun containsValue(value: V) = synchronized(this) { m.containsValue(value) }
    override fun get(key: K): V? = synchronized(this) { m[key] }
    override val keys: MutableSet<K> get() = synchronized(this) { m.keys.toMutableSet() }
    override val values: MutableCollection<V> get() = synchronized(this) { m.values.toMutableList() }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = synchronized(this) { HashMap(m).entries }
    override fun put(key: K, value: V): V? = synchronized(this) { m.put(key, value) }
    override fun remove(key: K): V? = synchronized(this) { m.remove(key) }
    override fun putAll(from: Map<out K, V>) = synchronized(this) { m.putAll(from) }
    override fun clear() = synchronized(this) { m.clear() }
    fun getOrPut(key: K, f: () -> V): V = synchronized(this) { m.getOrPut(key, f) }
    fun putIfAbsent(key: K, value: V): V? = synchronized(this) { m[key] ?: run { m[key] = value; null } }
}

/** Verrou réentrant global pour les anciens blocs synchronized(…) de la JVM. */
expect inline fun <T> synchronized(lock: Any, block: () -> T): T

/** Messages courts en bas de l'écran (remplace Toast). */
class Toast private constructor(private val text: String) {
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        val current = MutableStateFlow<Pair<String, Long>?>(null)
        fun makeText(ctx: Any?, text: CharSequence, length: Int): Toast = Toast(text.toString())
    }
    fun show() { current.value = text to nowMs() }
}

fun toast(text: String) = Toast.makeText(null, text, Toast.LENGTH_SHORT).show()

/** Journal minimal (remplace slf4j). */
class Logger(private val tag: String) {
    fun info(msg: String, vararg a: Any?) = println("I/$tag: $msg")
    fun warn(msg: String, vararg a: Any?) = println("W/$tag: $msg")
    fun error(msg: String, vararg a: Any?) = println("E/$tag: $msg ${a.lastOrNull() ?: ""}")
    fun debug(msg: String, vararg a: Any?) = Unit
}

object LoggerFactory { fun getLogger(c: Any?): Logger = Logger(c.toString().substringAfterLast('.')) }


typealias Application = Context

/** AndroidViewModel : ViewModel qui connaît l'app. */
open class AndroidViewModel(private val app: Context) : androidx.lifecycle.ViewModel() {
    @Suppress("UNCHECKED_CAST")
    fun <T : Context> getApplication(): T = app as T
}

// ---- Notifications locales et sons ---------------------------------------------------------------

/** Notification locale programmée (minuteur de repos, rappel de médicament) ; [atMs] ≤ maintenant : tout de suite. */
expect fun scheduleNotification(id: String, title: String, body: String, atMs: Long, sound: Boolean = true, category: String? = null)
expect fun cancelNotification(id: String)
expect fun cancelNotificationsWithPrefix(prefix: String)
/** Demande l'autorisation d'afficher des notifications (une seule fois, iOS la retient). */
expect fun requestNotificationPermission()
/** Bip de fin de repos, app ouverte. */
expect fun playBeep()

/** Version affichée de l'app (CFBundleShortVersionString sur iOS). */
expect fun appVersion(): String

// ---- Trousseau et identifiant -------------------------------------------------------------------

expect fun keychainGet(name: String): String?
expect fun keychainSet(name: String, value: String)
expect fun keychainDelete(name: String)
/** Identifiant stable de l'app sur cet appareil (identifierForVendor sur iOS). */
expect fun deviceIdentifier(): String?
