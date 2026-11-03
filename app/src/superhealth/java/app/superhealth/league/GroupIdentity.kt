package app.superhealth.league

import android.content.Context
import app.superhealth.backup.Secrets
import java.security.*
import java.security.spec.*
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Recoverable P-256 identity, encrypted at rest by Android Keystore. A legacy key endorses its successor. */
object GroupIdentity {
    @Serializable data class Identity(val private: String, val public: String, val previous: String? = null, val endorsement: String? = null)
    private const val SECRET = "group_identity_v1"
    @Volatile private var cached: Identity? = null
    private lateinit var context: Context
    fun init(ctx: Context) { context = ctx.applicationContext; cached = null }
    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun bytes(text: String) = Base64.getUrlDecoder().decode(text)
    private fun message(public: String) = ("SuperHealth identity successor v1:" + public).toByteArray()
    @Synchronized fun current(): Identity {
        cached?.let { return it }
        Secrets.get(context, SECRET)?.let { raw -> return Json.decodeFromString(Identity.serializer(), raw).also { cached = it } }
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val public = b64(pair.public.encoded)
        val legacy = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val previous = legacy.getCertificate("superhealth_league")?.publicKey?.encoded?.let(::b64)
        val endorsement = previous?.let {
            b64(Signature.getInstance("SHA256withECDSA").run {
                initSign(legacy.getKey("superhealth_league", null) as PrivateKey); update(message(public)); sign()
            })
        }
        return Identity(b64(pair.private.encoded), public, previous, endorsement).also { restore(it) }
    }
    fun publicKey() = current().public
    fun privateKey(): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes(current().private)))
    fun root() = current().previous ?: current().public
    fun validEndorsement(previous: String?, public: String, endorsement: String?): Boolean = runCatching {
        if (previous == null) return endorsement == null
        require(previous.length <= 512 && endorsement != null && endorsement.length <= 200)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes(previous)))
        Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(message(public)); verify(bytes(endorsement)) }
    }.getOrDefault(false)
    fun validate(identity: Identity) {
        require(validEndorsement(identity.previous, identity.public, identity.endorsement))
        val factory = KeyFactory.getInstance("EC")
        val private = factory.generatePrivate(PKCS8EncodedKeySpec(bytes(identity.private)))
        val public = factory.generatePublic(X509EncodedKeySpec(bytes(identity.public)))
        val challenge = "SuperHealth recovery identity check".toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").run { initSign(private); update(challenge); sign() }
        require(Signature.getInstance("SHA256withECDSA").run { initVerify(public); update(challenge); verify(signature) })
    }
    @Synchronized fun restore(identity: Identity) {
        validate(identity)
        val encoded = Json.encodeToString(Identity.serializer(), identity)
        Secrets.put(context, SECRET, encoded)
        check(Secrets.get(context, SECRET) == encoded) { "Enregistrement de l'identité impossible." }
        cached = identity
    }
}
