package app.superhealth.league

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Image ou GIF joint à une réponse du Fil. Les versions 3.6 refusent tout événement portant un champ inconnu (et avec lui
 * l'historique entier de l'ami) : le média voyage donc dans le texte de la réponse, sur une dernière ligne
 * « 🖼 … ⟦sh:…⟧ » que les versions récentes remplacent par l'image. Le média est chiffré par sa propre clé et rangé dans
 * la boîte de l'auteur, sur le même serveur que le lecteur : une adresse d'un autre serveur est ignorée.
 */
object ReplyMedia {
    @Serializable data class Attached(val m: SocialPosts.Media, val e: String)
    private val json = Json { ignoreUnknownKeys = true }
    private val line = Regex("""\n?[^\n]*⟦sh:([A-Za-z0-9_-]{40,1200})⟧\s*$""")

    /** Texte publié : la légende, puis la ligne du média. */
    fun encode(caption: String, media: SocialPosts.Media, endpoint: String): String {
        val token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.encodeToString(Attached.serializer(), Attached(media, endpoint)).toByteArray())
        val label = (if (media.mime == "image/gif") "🖼 GIF" else "🖼 Photo") + " ⟦sh:$token⟧"
        val room = 1000 - label.length - 1
        require(room >= 0)
        return caption.trim().take(room).trim().let { if (it.isEmpty()) label else it + "\n" + label }
    }

    /** Légende sans la ligne du média. */
    fun caption(text: String) = text.replace(line, "").trim()

    /** Média joint, s'il est valable et rangé sur le même serveur que le lecteur. */
    fun parse(text: String, base: String? = Relay.base()): Attached? {
        val token = line.find(text)?.groupValues?.get(1) ?: return null
        val attached = runCatching { json.decodeFromString(Attached.serializer(), String(java.util.Base64.getUrlDecoder().decode(token))) }.getOrNull() ?: return null
        if (!SocialPosts.validMedia(attached.m) || !attached.m.mime.startsWith("image/")) return null
        if (base == null || !Relay.encryptedEndpoint(attached.e) || !attached.e.startsWith("$base/v2/cards/")) return null
        return attached
    }
}
