package app.superhealth.league

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Individually encrypted handoffs let retained friends follow a capability rotation. */
object GroupLinks {
    @Serializable data class Link(val key: String, val previous: String?, val endorsement: String?, val endpoint: String, val aes: String)
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private fun recipient(key: String) = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    private fun shared(key: String): String {
        val public = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getUrlDecoder().decode(key)))
        val agreement = KeyAgreement.getInstance("ECDH").apply { init(GroupIdentity.privateKey()); doPhase(public, true) }
        val secret = agreement.generateSecret()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
            .digest("SuperHealth group handoff v1:".toByteArray() + secret))
    }
    fun publish(ctx: Context, old: String, owner: String, publishKey: String, endpoint: String, aes: String, excluded: Set<String>) {
        val identity = GroupIdentity.current()
        val raw = League.signSocial(Json.encodeToString(Link.serializer(), Link(identity.public, identity.previous, identity.endorsement, endpoint, aes)))
        for (friend in League.state.value.friends.filterNot { it.id in excluded }) {
            val url = old + "/handoffs/" + recipient(friend.key)
            val encrypted = RelayCrypto.encrypt(raw, shared(friend.key), url.toHttpUrlOrNull()!!.encodedPath, System.currentTimeMillis())
            http.newCall(Request.Builder().url(url).header("X-Write-Token", owner).header("Authorization", "Bearer " + publishKey)
                .put(encrypted.toRequestBody("application/json".toMediaType())).build()).execute().use { check(it.isSuccessful) { "Renouvellement du Groupe à réessayer." } }
        }
    }
    fun resolve(ctx: Context, friend: League.Friend): Boolean {
        val old = friend.remote ?: return false
        val url = old + "/handoffs/" + recipient(League.publicKey())
        val encrypted = http.newCall(Request.Builder().url(url).get().build()).execute().use {
            if (!it.isSuccessful) return false
            val bytes = it.body.byteStream().use { stream -> stream.readNBytes(98305) }; require(bytes.size <= 98304); bytes.decodeToString()
        }
        val signed = RelayCrypto.decrypt(encrypted, shared(friend.key), url.toHttpUrlOrNull()!!.encodedPath)
        val raw = League.verifySocial(signed, friend.key) ?: return false
        val link = Json.decodeFromString(Link.serializer(), raw)
        require(link.key == friend.key && Relay.encryptedEndpoint(link.endpoint) && RelayCrypto.validKey(link.aes))
        League.configure(ctx) { s -> s.copy(friends = s.friends.map { if (it.id == friend.id) it.copy(remote = link.endpoint, relayKey = link.aes, relayEtag = null) else it }) }
        return true
    }
}
