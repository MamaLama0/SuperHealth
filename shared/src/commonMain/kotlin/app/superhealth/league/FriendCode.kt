package app.superhealth.league

import app.superhealth.platform.*


object FriendCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun create(): String = SecureRandom().let { random -> (1..12).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("") }
    fun normalize(raw: String): String? = raw.uppercase(app.superhealth.platform.Locale.ROOT).filterNot { it == '-' || it.isWhitespace() }
        .takeIf { it.length == 12 && it.all { c -> c in ALPHABET } }
    fun display(code: String) = requireNotNull(normalize(code)).chunked(4).joinToString("-")
    private fun digest(domain: String, code: String) = MessageDigest.getInstance("SHA-256")
        .digest(("SuperHealth friend code v1:" + domain + ":" + requireNotNull(normalize(code))).toByteArray())
    fun path(code: String) = "/v2/invites/" + digest("lookup", code).joinToString("") { "%02x".format(it) }
    fun key(code: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(digest("encryption", code))
}
