package app.superhealth.nutrition.data

import java.io.InputStream
import java.net.URI

/** Garde-fous réseau : réponses de taille bornée, clé API jamais envoyée en clair sur Internet. */
object Net {
    const val MAX_RESPONSE = 4 * 1024 * 1024

    /** Lit au plus [max] octets (une réponse plus longue est une erreur, pas une app qui sature sa mémoire). */
    fun readCapped(input: InputStream, max: Int = MAX_RESPONSE): String {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            if (buf.size() + n > max) throw java.io.IOException("Réponse trop volumineuse")
            buf.write(chunk, 0, n)
        }
        return buf.toString(Charsets.UTF_8.name())
    }

    /**
     * Une clé ne part qu'en HTTPS, ou en HTTP vers une machine du réseau local (Ollama sur le PC, par exemple) :
     * localhost, 127/8, 10/8, 172.16/12, 192.168/16, *.local.
     */
    fun canSendSecret(url: String): Boolean {
        val u = runCatching { URI(url) }.getOrNull() ?: return false
        val host = u.host?.lowercase()?.trim('[', ']') ?: return false
        return when (u.scheme?.lowercase()) {
            "https" -> true
            "http" -> isLocal(host)
            else -> false
        }
    }

    fun isLocal(host: String): Boolean {
        if (host == "localhost" || host == "::1" || host.endsWith(".local")) return true
        val p = host.split('.').map { it.toIntOrNull() ?: return false }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 127 || p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }
}
