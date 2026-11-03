package app.superhealth.league

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Local notification history, independent of Android's notification permission. */
object SocialInbox {
    @Serializable data class Item(val id: String, val title: String, val text: String, val route: String,
        val time: Long, val read: Boolean = false)
    val items = MutableStateFlow<List<Item>>(emptyList())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(ctx: Context) = AtomicFile(File(ctx.filesDir, "league/notifications.json").apply { parentFile?.mkdirs() })
    fun load(ctx: Context) {
        items.value = runCatching { file(ctx).openRead().bufferedReader().use {
            json.decodeFromString(ListSerializer(Item.serializer()), it.readText())
        } }.getOrDefault(emptyList()).take(200)
    }
    internal fun merge(old: List<Item>, next: Item): List<Item> =
        if (old.any { it.id == next.id }) old else (listOf(next) + old).sortedByDescending { it.time }.take(200)
    @Synchronized private fun update(ctx: Context, change: (List<Item>) -> List<Item>) {
        val next = change(items.value)
        val f = file(ctx); val stream = f.startWrite()
        try { stream.write(json.encodeToString(ListSerializer(Item.serializer()), next).toByteArray()); f.finishWrite(stream) }
        catch (e: Exception) { f.failWrite(stream); throw e }
        items.value = next
    }
    fun add(ctx: Context, id: String, title: String, text: String, route: String) =
        update(ctx) { merge(it, Item(id, title, text, route, System.currentTimeMillis())) }
    fun read(ctx: Context, id: String? = null) = update(ctx) { list -> list.map { if (id == null || it.id == id) it.copy(read = true) else it } }
    fun clear(ctx: Context) = update(ctx) { emptyList() }
}
