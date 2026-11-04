package app.superhealth.league

import app.superhealth.platform.*

import app.superhealth.backup.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object InvitationCodes {
    private data class Lookup(val base: String, val path: String, val until: Long)
    private val pending = app.superhealth.platform.ConcurrentHashMap<String, Lookup>()
    private val http = SocialHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private const val VALID_MS = 24 * 3_600_000L
    data class Invitation(val code: String, val base: String, val expires: Long) {
        val text get() = FriendCode.display(code)
        val link: String get() = "superhealth://league?code=" + URLEncoder.encode(text) +
            if(base!=Relay.defaultBase()) "&server="+URLEncoder.encode(base) else ""

    }
    suspend fun publish(ctx: Context, renew: Boolean = false): Invitation = withContext(Dispatchers.IO) {
        val base = Relay.base() ?: error("Activez le serveur dans les réglages des amis.")
        val saved = Secrets.get(ctx, "friend_invitation_code")?.let(FriendCode::normalize)
        val until = Secrets.get(ctx, "friend_invitation_expiry")?.toLongOrNull() ?: 0L
        if (!renew && saved != null && until > System.currentTimeMillis() + 60_000 && Secrets.get(ctx, "friend_invitation_base") == base)
            return@withContext Invitation(saved, base, until)
        if (renew && saved != null && until > System.currentTimeMillis()) collectResponses(ctx)
        val code = FriendCode.create()
        val path = FriendCode.path(code)
        val raw = League.inviteCard(ctx)
        val card = League.parse(raw) ?: error("Invitation indisponible.")
        val encrypted = RelayCrypto.encrypt(raw, FriendCode.key(code), path, card.revision)
        val owner = app.superhealth.platform.SecureRandom().generateSeed(32).joinToString("") { "%02x".format(it) }
        val request = SocialRequest.Builder().url(base + path).header("Authorization", "Bearer " + Relay.publicationKey(ctx))
            .header("X-Write-Token", owner).put(encrypted.toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { if (response.code == 404) "Mettez à jour le serveur pour utiliser les codes amis." else "Impossible de créer un code. Réessayez." }
        }
        // Revoke an older live code when renewing, using its independent owner capability.
        if (saved != null && until > System.currentTimeMillis()) {
            val oldBase = Secrets.get(ctx, "friend_invitation_base")
            val oldOwner = Secrets.get(ctx, "friend_invitation_owner")
            if (oldBase != null && Relay.validBase(oldBase) && oldOwner != null) runCatching {
                http.newCall(SocialRequest.Builder().url(oldBase + FriendCode.path(saved)).header("Authorization", "Bearer " + Relay.publicationKey(ctx))
                    .header("X-Write-Token", oldOwner).delete().build()).execute().use { }
            }
        }
        val expires = System.currentTimeMillis() + VALID_MS
        Secrets.put(ctx, "friend_invitation_code", code); Secrets.put(ctx, "friend_invitation_base", base)
        Secrets.put(ctx, "friend_invitation_expiry", expires.toString()); Secrets.put(ctx, "friend_invitation_owner", owner)
        Invitation(code, base, expires)
    }
    suspend fun lookup(ctx: Context, rawCode: String, customBase: String? = null): Boolean = withContext(Dispatchers.IO) {
        val code = FriendCode.normalize(rawCode) ?: error("Saisissez les 12 lettres et chiffres du code.")
        val base = (customBase ?: Relay.defaultBase())?.trimEnd('/')?.takeIf(Relay::validBase) ?: error("Saisissez une adresse de serveur HTTPS valable.")
        val path = FriendCode.path(code)
        val encrypted = http.newCall(SocialRequest.Builder().url(base + path).get().build()).execute().use { response ->
            check(response.isSuccessful) { if (response.code == 429) "Trop de tentatives. Réessayez dans une minute." else "Code introuvable ou expiré. Vérifiez le serveur choisi." }
            val input = response.body.byteStream()
            input.use { stream ->
                val out = app.superhealth.platform.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                while (true) { val n = stream.read(buffer); if (n < 0) break; check(out.size() + n <= 16384) { "Invitation non valable." }; out.write(buffer, 0, n) }
                out.toString("UTF-8")
            }
        }
        val raw = RelayCrypto.decrypt(encrypted, FriendCode.key(code), path)
        val card = League.parse(raw) ?: error("Invitation non valable.")
        check(card.invite && card.t * 1000 >= System.currentTimeMillis() - VALID_MS && card.t * 1000 <= System.currentTimeMillis() + 300_000) { "Code expiré. Demandez un nouveau code." }
        check(card.k != runCatching { // Own fingerprint is compared through the signed card; no personal name is used.
            League.parse(League.inviteCard(ctx))?.k
        }.getOrNull()) { "Ce code est le vôtre." }
        pending.clear()
        pending[card.k] = Lookup(base, path, card.t * 1000 + VALID_MS)
        League.incoming.value = card
        true
    }
    suspend fun confirm(ctx: Context, card: League.Card) = withContext(Dispatchers.IO) {
        pending[card.k]?.let { lookup ->
            check(lookup.until > System.currentTimeMillis()) { "Code expiré." }
            val key = card.relayKey ?: error("Invitation non valable.")
            val raw = League.inviteCard(ctx)
            val own = League.parse(raw) ?: error("Invitation non valable.")
            val senderId = MessageDigest.getInstance("SHA-256").digest(own.k.toByteArray()).joinToString("") { "%02x".format(it) }
            val path = lookup.path + "/responses/" + senderId
            val encrypted = RelayCrypto.encrypt(raw, key, path, own.revision)
            http.newCall(SocialRequest.Builder().url(lookup.base + path).post(encrypted.toRequestBody("application/json".toMediaType())).build()).execute().use {
                check(it.isSuccessful) { "Ajout impossible. Réessayez." }
            }
            pending.remove(card.k)
        }
        League.accept(ctx, card)
    }
    suspend fun revokeCurrent(ctx:Context) = withContext(Dispatchers.IO) {
        val code=Secrets.get(ctx,"friend_invitation_code")?.let(FriendCode::normalize)?:return@withContext
        val base=Secrets.get(ctx,"friend_invitation_base")?:return@withContext
        val owner=Secrets.get(ctx,"friend_invitation_owner")?:return@withContext
        http.newCall(SocialRequest.Builder().url(base+FriendCode.path(code)).header("Authorization","Bearer "+Relay.publicationKey(ctx)).header("X-Write-Token",owner).delete().build()).execute().use{check(it.isSuccessful||it.code==404)}
        Secrets.putAll(ctx,mapOf("friend_invitation_code" to null,"friend_invitation_owner" to null,"friend_invitation_expiry" to null,"friend_invitation_base" to null))
    }
    suspend fun collectResponses(ctx: Context) = withContext(Dispatchers.IO) {
        val code = Secrets.get(ctx, "friend_invitation_code")?.let(FriendCode::normalize) ?: return@withContext
        val base = Secrets.get(ctx, "friend_invitation_base")?.takeIf(Relay::validBase) ?: return@withContext
        val owner = Secrets.get(ctx, "friend_invitation_owner") ?: return@withContext
        val until = Secrets.get(ctx, "friend_invitation_expiry")?.toLongOrNull() ?: return@withContext
        if (until <= System.currentTimeMillis()) return@withContext
        val path = FriendCode.path(code) + "/responses"
        val body = http.newCall(SocialRequest.Builder().url(base + path).header("X-Write-Token", owner).get().build()).execute().use {
            if (!it.isSuccessful) return@withContext
            it.body.byteStream().use { stream ->
                val out = app.superhealth.platform.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = stream.read(buffer); if (n < 0) break; check(out.size() + n <= 16 * 16384 + 2048) { "Réponse trop volumineuse." }; out.write(buffer, 0, n) }
                out.toString("UTF-8")
            }
        }
        val responses = kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(body)
        check(responses.size <= 16)
        val ownKey = League.parse(League.inviteCard(ctx))?.k
        for ((senderId, envelope) in responses) {
            if (!senderId.matches(Regex("[a-f0-9]{64}"))) continue
            val responsePath = "$path/$senderId"
            val raw = runCatching { RelayCrypto.decrypt(envelope, Relay.readKey(ctx), responsePath) }.getOrNull()
            val card = raw?.let(League::parse)
            if (card?.invite == true && card.k != ownKey && card.t * 1000 >= System.currentTimeMillis() - VALID_MS) {
                val expected = MessageDigest.getInstance("SHA-256").digest(card.k.toByteArray()).joinToString("") { "%02x".format(it) }
                if (senderId == expected) League.accept(ctx, card)
            }
            http.newCall(SocialRequest.Builder().url(base + responsePath).header("X-Write-Token", owner).delete().build()).execute().use { }
        }
    }
    suspend fun receive(ctx: Context, text: String): Boolean {
        val link = Regex("superhealth://league\\?[^\\s]+", RegexOption.IGNORE_CASE).find(text.take(131072))?.value
        val uri = runCatching { Uri.parse(link ?: text.trim()) }.getOrNull()
        if (uri?.scheme == "superhealth" && uri.host == "league" && uri.getQueryParameter("code") != null)
            return lookup(ctx, uri.getQueryParameter("code").orEmpty(), uri.getQueryParameter("server"))
        if (FriendCode.normalize(text) != null) return lookup(ctx, text)
        return League.receive(ctx, text)
    }
}
