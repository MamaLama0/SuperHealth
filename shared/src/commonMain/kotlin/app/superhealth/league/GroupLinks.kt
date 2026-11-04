package app.superhealth.league

import app.superhealth.platform.*
import app.superhealth.backup.Secrets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object GroupLinks {
    @Serializable data class Link(val key:String,val previous:String?,val endorsement:String?,val endpoint:String,val aes:String)
    private val http=SocialHttpClient.Builder().callTimeout(20,TimeUnit.SECONDS).followRedirects(false).build()
    private fun recipient(key:String)=Sha256.digest(key.toByteArray()).take(16).joinToString(""){"%02x".format(it)}
    private fun shared(key:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(Sha256.digest("SuperHealth group handoff v1:".toByteArray()+identitySharedSecret(League.socialIdentity().privateKey,Base64.getUrlDecoder().decode(key))))
    fun publish(ctx:Context,old:String,owner:String,publishKey:String,endpoint:String,aes:String,excluded:Set<String>) {
        val identity=GroupIdentity.current()
        val raw=League.signSocial(Json.encodeToString(Link.serializer(),Link(identity.public,identity.previous,identity.endorsement,endpoint,aes)))
        for(friend in League.state.value.friends.filterNot { it.id in excluded }) {
            val url=old+"/handoffs/"+recipient(friend.key)
            val encrypted=RelayCrypto.encrypt(raw,shared(friend.key),url.toHttpUrlOrNull()!!.encodedPath,System.currentTimeMillis())
            http.newCall(SocialRequest.Builder().url(url).header("X-Write-Token",owner).header("Authorization","Bearer "+publishKey)
                .put(encrypted.toRequestBody("application/json")).build()).execute().use{check(it.isSuccessful)}
        }
    }
    fun resolve(ctx:Context,friend:League.Friend):Boolean {
        val old=friend.remote?:return false;val url=old+"/handoffs/"+recipient(League.publicKey())
        val encrypted=http.newCall(SocialRequest.Builder().url(url).get().build()).execute().use{if(!it.isSuccessful)return false;it.body.bytes.decodeToString()}
        val signed=RelayCrypto.decrypt(encrypted,shared(friend.key),url.toHttpUrlOrNull()!!.encodedPath)
        val payload=League.verifySocial(signed,friend.key)?:return false
        val link=Json.decodeFromString(Link.serializer(),payload)
        require(link.key==friend.key && Relay.encryptedEndpoint(link.endpoint)&&RelayCrypto.validKey(link.aes))
        League.configure(ctx){it.copy(friends=it.friends.map{peer->if(peer.id==friend.id)peer.copy(remote=link.endpoint,relayKey=link.aes,relayEtag=null)else peer})}
        return true
    }
}
