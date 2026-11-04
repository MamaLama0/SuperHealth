package app.superhealth.league

import app.superhealth.platform.*
import app.superhealth.backup.Secrets
import app.superhealth.social.P256Encoding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Canonical recoverable identity compatible with Android, normalized only at the platform boundary. */
object GroupIdentity {
    @Serializable data class Identity(val private: String, val public: String, val previous: String? = null, val endorsement: String? = null)
    private fun encode(bytes:ByteArray)=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun decode(text:String)=Base64.getUrlDecoder().decode(text)
    fun current():Identity {
        val ctx=appContext
        Secrets.get(ctx,"group_identity_v1")?.let { return Json.decodeFromString(Identity.serializer(),it) }
        val oldPrivate=Secrets.get(ctx,"league_signing_private");val oldPublic=Secrets.get(ctx,"league_signing_public")
        val native=if(oldPrivate!=null && oldPublic!=null)SigningIdentity(decode(oldPrivate),decode(oldPublic)) else generateSigningIdentity()
        return Identity(encode(P256Encoding.pkcs8(native.privateKey,native.publicKey)),encode(native.publicKey)).also { restore(it) }
    }
    fun signingIdentity():SigningIdentity {
        val identity=current()
        return normalizeSigningIdentity(SigningIdentity(decode(identity.private),decode(identity.public)))
    }
    fun root()=current().previous?:current().public
    fun validEndorsement(previous:String?,public:String,signature:String?):Boolean=FriendSocial.validEndorsement(previous,public,signature)
    fun validate(identity:Identity) {
        require(validEndorsement(identity.previous,identity.public,identity.endorsement))
        val native=normalizeSigningIdentity(SigningIdentity(decode(identity.private),decode(identity.public)))
        val message="SuperHealth recovery identity check".toByteArray()
        require(verifyIdentity(native.publicKey,message,signIdentity(native.privateKey,message)))
    }
    fun restore(identity:Identity) {
        validate(identity)
        Secrets.put(appContext,"group_identity_v1",Json.encodeToString(Identity.serializer(),identity))
    }
}
