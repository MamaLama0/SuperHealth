package app.superhealth.league

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object GroupDraft {
    @Serializable data class Draft(val text: String = "", val source: String = "", val uri: String? = null)
    private fun file(ctx: Context) = AtomicFile(File(ctx.filesDir, "league/group-draft.json").apply { parentFile?.mkdirs() })
    fun load(ctx: Context): Draft = runCatching { file(ctx).openRead().bufferedReader().use { Json.decodeFromString(Draft.serializer(), it.readText()) } }.getOrDefault(Draft())
    fun save(ctx: Context, draft: Draft) {
        val atomic = file(ctx); val out = atomic.startWrite()
        try { out.write(Json.encodeToString(Draft.serializer(), draft).toByteArray()); atomic.finishWrite(out) }
        catch (e: Exception) { atomic.failWrite(out); throw e }
    }
    fun clear(ctx: Context) = file(ctx).delete()
}
