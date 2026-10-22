package app.superhealth.ui

import android.content.Context

/**
 * Versions low-poly des images d'assets (assets/lowpoly/<même chemin>), générées depuis les originaux MacroFactor
 * par tools/lowpoly_assets.mjs ; elles remplacent l'original quand elles existent.
 */
object LowPoly {
    @Volatile private var files: Set<String>? = null

    fun path(ctx: Context, path: String): String {
        val set = files ?: buildSet { walk(ctx, "lowpoly", this) }.also { files = it }
        return if ("lowpoly/$path" in set) "lowpoly/$path" else path
    }

    private fun walk(ctx: Context, dir: String, out: MutableSet<String>) {
        val kids = ctx.assets.list(dir).orEmpty()
        for (k in kids) {
            val p = "$dir/$k"
            if ('.' in k) out += p else walk(ctx, p, out)
        }
    }
}
