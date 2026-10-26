package app.superhealth.platform

/**
 * Lecteur XML « pull » réduit (org.xmlpull de l'API Android) : balises ouvrantes et fermantes, attributs, texte, entités
 * courantes. Suffisant pour GPX et TCX ; pas de DTD ni d'espaces de noms (préfixes gardés dans le nom, comme
 * FEATURE_PROCESS_NAMESPACES = false).
 */
class XmlPullParser(private val s: String) {
    companion object {
        const val START_DOCUMENT = 0
        const val END_DOCUMENT = 1
        const val START_TAG = 2
        const val END_TAG = 3
        const val TEXT = 4
        const val FEATURE_PROCESS_NAMESPACES = "http://xmlpull.org/v1/doc/features.html#process-namespaces"
    }

    private var pos = 0
    var eventType = START_DOCUMENT; private set
    var name: String = ""; private set
    var text: String = ""; private set
    private var attrs: Map<String, String> = emptyMap()
    private var pendingEnd: String? = null

    fun setFeature(f: String, v: Boolean) = Unit
    fun getAttributeValue(ns: String?, n: String): String? = attrs[n]

    fun next(): Int {
        pendingEnd?.let { pendingEnd = null; name = it; attrs = emptyMap(); eventType = END_TAG; return eventType }
        while (true) {
            if (pos >= s.length) { eventType = END_DOCUMENT; return eventType }
            if (s[pos] != '<') {
                val end = s.indexOf('<', pos).let { if (it < 0) s.length else it }
                text = unescape(s.substring(pos, end)); pos = end
                eventType = TEXT; return eventType
            }
            when {
                s.startsWith("<?", pos) -> pos = s.indexOf("?>", pos).let { if (it < 0) s.length else it + 2 }
                s.startsWith("<!--", pos) -> pos = s.indexOf("-->", pos).let { if (it < 0) s.length else it + 3 }
                s.startsWith("<![CDATA[", pos) -> {
                    val end = s.indexOf("]]>", pos).let { if (it < 0) s.length else it }
                    text = s.substring(pos + 9, end); pos = end + 3; eventType = TEXT; return eventType
                }
                s.startsWith("<!", pos) -> pos = s.indexOf('>', pos).let { if (it < 0) s.length else it + 1 }
                s.startsWith("</", pos) -> {
                    val end = s.indexOf('>', pos).let { if (it < 0) s.length else it }
                    name = s.substring(pos + 2, end).trim(); attrs = emptyMap(); pos = end + 1
                    eventType = END_TAG; return eventType
                }
                else -> {
                    val end = tagEnd(pos)
                    var body = s.substring(pos + 1, end)
                    val selfClosing = body.endsWith("/")
                    if (selfClosing) body = body.dropLast(1)
                    val n = body.indexOfFirst { it.isWhitespace() }.let { if (it < 0) body.length else it }
                    name = body.substring(0, n)
                    attrs = parseAttrs(body.substring(n))
                    pos = end + 1
                    if (selfClosing) pendingEnd = name
                    eventType = START_TAG; return eventType
                }
            }
        }
    }

    /** Fin de balise en sautant les « > » dans les valeurs d'attributs. */
    private fun tagEnd(from: Int): Int {
        var i = from; var q: Char? = null
        while (i < s.length) {
            val c = s[i]
            if (q != null) { if (c == q) q = null } else if (c == '"' || c == '\'') q = c else if (c == '>') return i
            i++
        }
        return s.length
    }

    private fun parseAttrs(t: String): Map<String, String> =
        Regex("([\\w:.-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')").findAll(t).associate { m ->
            m.groupValues[1] to unescape(m.groupValues[3].ifEmpty { m.groupValues[4] })
        }

    /** Texte d'un élément simple, curseur laissé sur sa balise fermante. */
    fun nextText(): String {
        val sb = StringBuilder()
        while (true) {
            when (next()) {
                TEXT -> sb.append(text)
                END_TAG, END_DOCUMENT -> return sb.toString()
                START_TAG -> Unit
            }
        }
    }

    private fun unescape(t: String) = if ('&' !in t) t else t.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace(Regex("&#(x?)([0-9A-Fa-f]+);")) { m ->
            val code = m.groupValues[2].toInt(if (m.groupValues[1].isNotEmpty()) 16 else 10)
            if (code < 0x10000) code.toChar().toString() else charArrayOf(Char(0xD800 + ((code - 0x10000) shr 10)), Char(0xDC00 + ((code - 0x10000) and 0x3FF))).concatToString()
        }.replace("&amp;", "&")
}
