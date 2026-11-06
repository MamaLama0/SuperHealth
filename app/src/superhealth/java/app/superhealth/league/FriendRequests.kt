package app.superhealth.league

import android.content.Context
import app.superhealth.backup.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Demande d'ami envoyée depuis un commentaire, à quelqu'un qu'on n'a pas en ami. Elle contient mon lien d'invitation habituel,
 * chiffré pour la seule clé de groupe du destinataire (ECDH P-256 + AES-GCM) : le serveur ne voit qu'une boîte de chiffrés signés.
 * Accepter revient à ouvrir ce lien, puis la confirmation habituelle rend l'amitié réciproque.
 */
object FriendRequests {
    data class Incoming(val key: String, val id: String, val name: String, val link: String, val at: Long)
    val inbox = MutableStateFlow<List<Incoming>>(emptyList())
    /** Destinataires (identifiant d'auteur) déjà sollicités, avec la date d'envoi. */
    val sent = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val json = Json { ignoreUnknownKeys = true }
    private const val VALID_MS = 23 * 3_600_000L
    private const val INFO = "SuperHealth friend request v1:"

    fun load(ctx: Context) {
        sent.value = Secrets.get(ctx, "friend_requests_sent")?.let { raw ->
            runCatching { json.parseToJsonElement(raw).jsonObject.mapValues { it.value.jsonPrimitive.long } }.getOrNull()
        }.orEmpty().filterValues { System.currentTimeMillis() - it < VALID_MS }
    }

    fun pending(key: String) = sent.value[Faces.id(key)]?.let { System.currentTimeMillis() - it < VALID_MS } == true

    suspend fun send(ctx: Context, targetKey: String) = withContext(Dispatchers.IO) {
        val base = Relay.base() ?: error("Activez le serveur dans les réglages des amis.")
        val to = Faces.id(targetKey)
        require(to != Faces.id(League.publicKey()))
        val link = InvitationCodes.publish(ctx).link
        val plain = JsonObject(mapOf("link" to JsonPrimitive(link), "name" to JsonPrimitive((app.superhealth.ui.userName() ?: "?").take(24)))).toString()
        val now = System.currentTimeMillis()
        val payload = JsonObject(mapOf("t" to JsonPrimitive("friend"), "to" to JsonPrimitive(to), "at" to JsonPrimitive(now), "data" to JsonPrimitive(seal(plain, targetKey, to)))).toString()
        val body = JsonObject(mapOf("key" to JsonPrimitive(League.publicKey()), "signed" to JsonPrimitive(League.signSocial(payload)))).toString()
        Faces.http.newCall(Request.Builder().url("$base/v2/faces/$to/requests").post(body.toRequestBody("application/json".toMediaType())).build()).execute().use {
            check(it.isSuccessful) { when (it.code) { 404 -> "Mettez à jour le serveur pour envoyer des demandes d'ami."; 429 -> "Trop de demandes en attente pour cette personne."; else -> "Envoi impossible. Réessayez." } }
        }
        load(ctx)
        val updated = sent.value + (to to now)
        Secrets.put(ctx, "friend_requests_sent", JsonObject(updated.mapValues { JsonPrimitive(it.value) }).toString())
        sent.value = updated
    }

    /** Relève ma boîte : seules les demandes signées, récentes, lisibles avec ma clé et venant de non-amis restent. */
    suspend fun refresh(ctx: Context) = withContext(Dispatchers.IO) {
        val base = Relay.base() ?: return@withContext
        val me = Faces.id(League.publicKey())
        val raw = Faces.http.newCall(Request.Builder().url("$base/v2/faces/$me/requests").get().build()).execute().use {
            if (!it.isSuccessful) return@withContext
            it.peekBody(256_000).string()
        }
        val friends = League.state.value.friends
        val received = json.parseToJsonElement(raw).jsonObject["items"]?.jsonArray.orEmpty().mapNotNull { item ->
            runCatching {
                val key = item.jsonObject["key"]!!.jsonPrimitive.content
                val payload = json.parseToJsonElement(League.verifySocial(item.jsonObject["signed"]!!.jsonPrimitive.content, key)!!).jsonObject
                val at = payload["at"]!!.jsonPrimitive.long
                check(payload["t"]?.jsonPrimitive?.content == "friend" && payload["to"]?.jsonPrimitive?.content == me && System.currentTimeMillis() - at < VALID_MS)
                val content = json.parseToJsonElement(open(payload["data"]!!.jsonPrimitive.content, me)).jsonObject
                val link = content["link"]!!.jsonPrimitive.content
                check(link.startsWith("superhealth://league?"))
                Incoming(key, Faces.id(key), content["name"]?.jsonPrimitive?.content.orEmpty().take(24).ifBlank { "?" }, link, at)
            }.getOrNull()
        }
        // Une demande d'une personne déjà en ami n'a plus d'objet : on la retire de la boîte.
        val already = received.filter { r -> friends.any { it.key == r.key } }
        already.forEach { runCatching { clear(ctx, it.id) } }
        inbox.value = (received - already.toSet()).sortedByDescending { it.at }
    }

    /** Ouvre l'invitation : la boîte de dialogue d'ajout habituelle prend le relais. */
    suspend fun accept(ctx: Context, request: Incoming) {
        InvitationCodes.receive(ctx, request.link)
        dismiss(ctx, request)
    }

    suspend fun dismiss(ctx: Context, request: Incoming) {
        inbox.value = inbox.value - request
        runCatching { clear(ctx, request.id) }
    }

    private suspend fun clear(ctx: Context, from: String) = withContext(Dispatchers.IO) {
        val base = Relay.base() ?: return@withContext
        val me = Faces.id(League.publicKey())
        val payload = JsonObject(mapOf("t" to JsonPrimitive("clear"), "id" to JsonPrimitive(me), "at" to JsonPrimitive(System.currentTimeMillis()), "from" to JsonPrimitive(from))).toString()
        val body = JsonObject(mapOf("key" to JsonPrimitive(League.publicKey()), "signed" to JsonPrimitive(League.signSocial(payload)))).toString()
        Faces.http.newCall(Request.Builder().url("$base/v2/faces/$me/requests").delete(body.toRequestBody("application/json".toMediaType())).build()).execute().use { }
    }

    // ---- Chiffrement pour une seule clé : clé éphémère + ECDH, clé AES dérivée par SHA-256 -------------------------------
    private fun aesKey(secret: ByteArray, ephemeral: ByteArray, recipient: String) = SecretKeySpec(MessageDigest.getInstance("SHA-256")
        .digest(INFO.toByteArray() + secret + ephemeral + recipient.toByteArray()), "AES")

    private fun seal(plain: String, recipientKey: String, recipient: String): String {
        val public: PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(java.util.Base64.getUrlDecoder().decode(recipientKey)))
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val secret = KeyAgreement.getInstance("ECDH").run { init(pair.private); doPhase(public, true); generateSecret() }
        val ephemeral = pair.public.encoded
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, aesKey(secret, ephemeral, recipient), GCMParameterSpec(128, nonce)); updateAAD(recipient.toByteArray()) }
        val sealed = byteArrayOf((ephemeral.size shr 8).toByte(), ephemeral.size.toByte()) + ephemeral + nonce + cipher.doFinal(plain.toByteArray())
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sealed)
    }

    private fun open(data: String, recipient: String): String {
        val bytes = java.util.Base64.getUrlDecoder().decode(data)
        val size = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        require(size in 60..200 && bytes.size > 2 + size + 12 + 16)
        val ephemeral = bytes.copyOfRange(2, 2 + size)
        val nonce = bytes.copyOfRange(2 + size, 14 + size)
        val public = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(ephemeral))
        val secret = KeyAgreement.getInstance("ECDH").run { init(GroupIdentity.privateKey()); doPhase(public, true); generateSecret() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, aesKey(secret, ephemeral, recipient), GCMParameterSpec(128, nonce)); updateAAD(recipient.toByteArray()) }
        return cipher.doFinal(bytes.copyOfRange(14 + size, bytes.size)).decodeToString()
    }
}
