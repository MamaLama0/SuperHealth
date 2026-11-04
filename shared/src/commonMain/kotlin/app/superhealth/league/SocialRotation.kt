package app.superhealth.league

import app.superhealth.platform.*
import app.superhealth.backup.Secrets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Resumable mailbox migration retains published encrypted media before revocation. */
object SocialRotation {
    @Serializable private data class Rotation(val old:String,val owner:String,val publication:String,val mailbox:String,val read:String,val aes:String,val writer:String,
        val excluded:Set<String>,val media:Set<String> = emptySet(),val switched:Boolean=false,val prepared:Boolean=false)
    private val json=Json{encodeDefaults=true}
    private val http=SocialHttpClient.Builder().callTimeout(5,TimeUnit.MINUTES).followRedirects(false).build()
    private fun token()=secureRandomBytes(32).joinToString(""){"%02x".format(it)}
    suspend fun run(ctx:Context,excluded:Set<String>) {
        var operation=Secrets.get(ctx,"group_rotation_pending")?.let{json.decodeFromString(Rotation.serializer(),it)}?:Rotation(
            Relay.myEndpoint(ctx)?:error("Activez le serveur du Groupe"),Secrets.get(ctx,"league_owner_token_v2")?:error("Boîte absente"),Relay.publicationKey(ctx),token(),token(),RelayCrypto.newKey(),token(),excluded)
        fun save(){Secrets.put(ctx,"group_rotation_pending",json.encodeToString(Rotation.serializer(),operation))}
        save()
        val endpoint=operation.old.substringBefore("/v2/cards/")+"/v2/cards/"+operation.mailbox+"/"+operation.read
        fun builder(url:String)=SocialRequest.Builder().url(url).header("Authorization","Bearer "+operation.publication).header("X-Write-Token",operation.writer)
        if(!operation.switched) {
            FriendSocial.loadOwnHistory(ctx)
            val raw=League.card(app.superhealth.ui.userName()?:"?",League.compute(ctx),endpoint)
            val parsed=League.parse(raw)?:error("Carte non valable")
            val encrypted=RelayCrypto.encrypt(raw,operation.aes,endpoint.toHttpUrlOrNull()!!.encodedPath,parsed.revision)
            http.newCall(builder(endpoint).put(encrypted.toRequestBody("application/json")).build()).execute().use{check(it.isSuccessful)}
            val media=(FriendSocial.repository(ctx).posts().filter{it.second.id==League.myId()}.mapNotNull{it.first.media}+listOfNotNull(League.state.value.profileAvatar)).distinctBy{it.id}
            for(item in media) {
                if(item.id in operation.media || FriendSocial.pendingMedia(ctx,item.id))continue
                val bytes=http.newCall(SocialRequest.Builder().url(operation.old+"/media/"+item.id).maxResponseBytes(51*1024*1024).get().build()).execute().use{check(it.isSuccessful);it.body.bytes}
                require(bytes.size==item.bytes&&Sha256.digest(bytes).joinToString(""){"%02x".format(it)}==item.hash)
                http.newCall(builder(endpoint+"/media/"+item.id).header("X-Content-SHA256",item.hash).put(SocialBody(bytes,"application/octet-stream")).build()).execute().use{check(it.isSuccessful||it.code==409)}
                operation=operation.copy(media=operation.media+item.id);save()
            }
            GroupLinks.publish(ctx,operation.old,operation.owner,operation.publication,endpoint,operation.aes,operation.excluded)
            Secrets.putAll(ctx,mapOf("league_mailbox_v2" to operation.mailbox,"league_read_capability_v2" to operation.read,"league_aes_read_key" to operation.aes,
                "league_owner_token_v2" to operation.writer,"league_published_hash_v2" to null,"league_published_at_v2" to null))
            operation=operation.copy(switched=true);save()
        }
        if(!operation.prepared){FriendSocial.repository(ctx).republishOwn();operation=operation.copy(prepared=true);save()}
        FriendSocial.repository(ctx).publishPending()
        http.newCall(SocialRequest.Builder().url(operation.old).header("Authorization","Bearer "+operation.publication).header("X-Write-Token",operation.owner).delete().build()).execute().use{check(it.isSuccessful||it.code==404)}
        InvitationCodes.revokeCurrent(ctx)
        League.configure(ctx){it.copy(friends=it.friends.filterNot{peer->peer.id in operation.excluded})}
        Secrets.put(ctx,"group_rotation_pending",null)
        GroupRecovery.upload(ctx)
    }
}
