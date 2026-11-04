package app.superhealth.league

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object GroupDraft {
    @Serializable data class Draft(val text: String = "", val source: String = "", val uri: String? = null, val shareRoute: Boolean = false, val shareDetails: Boolean = true, val publicationId: String = java.util.UUID.randomUUID().toString().replace("-", ""))
    private fun file(ctx: Context) = AtomicFile(File(ctx.filesDir, "league/group-draft.json").apply { parentFile?.mkdirs() })
    @Synchronized fun load(ctx: Context): Draft = runCatching { file(ctx).openRead().bufferedReader().use { Json.decodeFromString(Draft.serializer(), it.readText()) } }.getOrDefault(Draft())
    @Synchronized fun save(ctx: Context, draft: Draft) {
        val atomic = file(ctx); val out = atomic.startWrite()
        try { out.write(Json.encodeToString(Draft.serializer(), draft).toByteArray()); atomic.finishWrite(out) }
        catch (e: Exception) { atomic.failWrite(out); throw e }
    }
    @Synchronized fun clear(ctx: Context) = file(ctx).delete()
}
