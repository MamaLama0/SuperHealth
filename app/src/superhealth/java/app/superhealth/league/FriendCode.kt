package app.superhealth.league

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object FriendCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun create(): String = SecureRandom().let { random -> (1..12).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("") }
    fun normalize(raw: String): String? = raw.uppercase(java.util.Locale.ROOT).filterNot { it == '-' || it.isWhitespace() }
        .takeIf { it.length == 12 && it.all { c -> c in ALPHABET } }
    /** Accepte aussi le message partagé entier, sans confondre deux invitations. */
    fun fromText(raw: String): String? {
        if (raw.length > 4096) return null
        normalize(raw)?.let { return it }
        val candidates = Regex("(?<![A-Z0-9])[$ALPHABET]{4}(?:[- ][$ALPHABET]{4}){2}(?![A-Z0-9])", RegexOption.IGNORE_CASE)
            .findAll(raw).mapNotNull { normalize(it.value) }.distinct().toList()
        return candidates.singleOrNull()
    }

    /** Le serveur facultatif est explicitement libellé, jamais déduit d'un lien quelconque. */
    fun serverFromText(raw: String): String? = raw.takeIf { it.length <= 4096 }?.let {
        Regex("^\\s*(?:Serveur|Server)\\s*:\\s*(https://\\S+)\\s*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            .findAll(it).map { m -> m.groupValues[1] }.distinct().toList().singleOrNull()
    }
    fun display(code: String) = requireNotNull(normalize(code)).chunked(4).joinToString("-")
    private fun digest(domain: String, code: String) = MessageDigest.getInstance("SHA-256")
        .digest(("SuperHealth friend code v1:" + domain + ":" + requireNotNull(normalize(code))).toByteArray())
    fun path(code: String) = "/v2/invites/" + digest("lookup", code).joinToString("") { "%02x".format(it) }
    fun key(code: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(digest("encryption", code))
}
