package app.superhealth.league

import app.superhealth.platform.*
import app.superhealth.backup.Secrets
import app.superhealth.social.*
import kotlinx.serialization.json.Json

/** Portable SHG1 adapter. Video and photo bytes enter the durable encrypted queue before network access. */
object FriendSocial {
    private val json=Json { encodeDefaults=true }
    private var timeline:FriendTimeline?=null
    private val http=SocialHttpClient.Builder().connectTimeout(8000,TimeUnit.MILLISECONDS).callTimeout(5,TimeUnit.MINUTES).build()
    fun validEndorsement(root:String?,key:String,signature:String?):Boolean = runCatching {
        if(root==null)return@runCatching signature==null
        require(root.length<=512 && signature!=null && signature.length<=200)
        verifyIdentity(Base64.getUrlDecoder().decode(root),("SuperHealth identity successor v1:"+key).toByteArray(),Base64.getUrlDecoder().decode(signature))
    }.getOrDefault(false)
    fun repository(ctx:Context):FriendTimeline = timeline?:FriendTimeline(AppleTimelineHost(ctx)).also { timeline=it }
    fun pendingMedia(ctx:Context,id:String) = encrypted(ctx,id).isFile && !marker(ctx,id).isFile
    suspend fun ensureMailbox(ctx:Context) = AppleTimelineHost(ctx).prepareMailbox()
    suspend fun loadOwnHistory(ctx:Context) {
        val repository=repository(ctx);repository.sync(ownOnly=true)
        while(repository.state.value.cursors[League.publicKey()]!=null)repository.sync(older=true,ownOnly=true)
    }
    fun resetCache(){timeline=null}
    fun resetHistory(ctx:Context,reading:SocialReading=SocialReading()) {
        val store=TimelineStore(hidden=reading.hidden,muted=reading.muted,silent=reading.silent)
        val f=AtomicFile(File(ctx.filesDir,"league/friend-history.json").also { it.parentFile?.mkdirs() });val out=f.startWrite()
        try{out.write(json.encodeToString(TimelineStore.serializer(),store).toByteArray());f.finishWrite(out)}catch(e:Exception){f.failWrite(out);throw e}
        timeline=null
    }
    fun sentPosts():List<FriendPost> {
        val current=timeline?.state?.value?:return League.state.value.ownPosts
        return current.events.filter { it.key==League.publicKey() && !it.deleted && it.post!=null && it.id !in current.pending }
            .map { it.post!! }.sortedByDescending { it.created }.take(12)
    }
    suspend fun prepareAvatar(ctx:Context) {
        if(!League.state.value.shareProfilePhoto)return
        val file=app.superhealth.ui.ProfilePhoto.file(ctx)
        if(!file.isFile)return
        val bytes=file.readBytes();val fingerprint=digest(bytes)
        if(Secrets.get(ctx,"social_avatar_source")==fingerprint && League.state.value.profileAvatar!=null)return
        val media=prepareMedia(ctx,PickedFile("avatar.jpg",downscaleJpeg(bytes,192,85)))
        AppleTimelineHost(ctx).upload(media)
        League.configure(ctx){it.copy(profileAvatar=media)}
        Secrets.put(ctx,"social_avatar_source",fingerprint)
    }
    private fun token(ctx:Context,name:String):String = Secrets.get(ctx,name)?:SecureRandom().generateSeed(32).joinToString(""){"%02x".format(it)}.also { Secrets.put(ctx,name,it) }
    private fun directory(ctx:Context)=File(ctx.filesDir,"league/ios-media").also { it.mkdirs() }
    private fun encrypted(ctx:Context,id:String)=File(directory(ctx),id+".enc")
    private fun marker(ctx:Context,id:String,endpoint:String?=Relay.myEndpoint(ctx))=File(directory(ctx),id+"."+digest(endpoint.orEmpty().toByteArray()).take(16)+".uploaded")
    private fun digest(bytes:ByteArray)=Sha256.digest(bytes).joinToString(""){"%02x".format(it)}
    fun prepareMedia(ctx:Context,picked:PickedFile):FriendMedia {
        val extension=picked.name.substringAfterLast('.').lowercase()
        val video=extension in setOf("mp4","webm","3gp")
        require(picked.bytes.size <= if(video)50*1024*1024 else 15*1024*1024)
        val files=directory(ctx).listFiles().orEmpty().filter { it.extension=="enc" }
        require(files.count { !marker(ctx,it.name.substringBefore('.')).isFile }<20 && files.filter { !marker(ctx,it.name.substringBefore('.')).isFile }.sumOf { it.length() }+picked.bytes.size<=250L*1024*1024){"La file de médias est pleine"}
        val bytes=if(video)picked.bytes else downscaleJpeg(picked.bytes,2048,88)
        val mime=if(video)when(extension){"webm"->"video/webm";"3gp"->"video/3gpp";else->"video/mp4"}else "image/jpeg"
        val id=digest(secureRandomBytes(32));val key=RelayCrypto.newKey();val nonce=secureRandomBytes(12)
        val raw=nonce+AesGcm(Base64.getUrlDecoder().decode(key)).encrypt(nonce,bytes,("SuperHealth media v1:"+id).toByteArray())
        val target=AtomicFile(encrypted(ctx,id));val out=target.startWrite()
        try{out.write(raw);target.finishWrite(out)}catch(e:Exception){target.failWrite(out);throw e}
        return FriendMedia(id,key,mime,digest(raw),raw.size)
    }
    suspend fun download(ctx:Context,media:FriendMedia,endpoint:String):File {
        require(FriendTimelineRules.valid(media) && Relay.encryptedEndpoint(endpoint))
        val cached=File(ctx.cacheDir,"social-"+media.hash+if(media.mime.startsWith("video/"))".mp4" else ".jpg")
        if(cached.isFile)return cached
        val local=encrypted(ctx,media.id)
        val raw=if(local.isFile)local.readBytes() else http.newCall(SocialRequest.Builder().url(endpoint+"/media/"+media.id).maxResponseBytes(51*1024*1024).get().build()).execute().use {
            check(it.isSuccessful){"Média indisponible"};it.body.bytes
        }
        require(raw.size==media.bytes && digest(raw)==media.hash)
        val plain=AesGcm(Base64.getUrlDecoder().decode(media.key)).decrypt(raw.copyOfRange(0,12),raw.copyOfRange(12,raw.size),("SuperHealth media v1:"+media.id).toByteArray())
        cached.writeBytes(plain);return cached
    }
    suspend fun sendEncryptedCopy(ctx:Context,endpoint:String,media:FriendMedia,file:File,writer:String) {
        val bytes=file.readBytes();require(bytes.size==media.bytes && digest(bytes)==media.hash)
        http.newCall(SocialRequest.Builder().url(endpoint+"/media/"+media.id).header("Authorization","Bearer "+Relay.publicationKey(ctx)).header("X-Write-Token",writer).header("X-Content-SHA256",media.hash)
            .put(SocialBody(bytes,"application/octet-stream")).build()).execute().use{response->
            if(response.code==409){val existing=http.newCall(SocialRequest.Builder().url(endpoint+"/media/"+media.id).maxResponseBytes(51*1024*1024).get().build()).execute().use{check(it.isSuccessful);it.body.bytes};require(existing.size==media.bytes && digest(existing)==media.hash)}else check(response.isSuccessful)
        }
        marker(ctx,media.id,endpoint).writeText(endpoint)
    }
    suspend fun sendSpaceMedia(ctx:Context,media:SpaceAttachment) {
        require(media.endpoint==SocialSpaces.contact()?.endpoint)
        sendMedia(ctx,FriendMedia(media.id,media.key,media.mime,media.hash,media.bytes),media.endpoint,Secrets.get(ctx,"social_space_writer")?:error("Clé du flux absente"))
    }
    private suspend fun sendMedia(ctx:Context,media:FriendMedia,endpoint:String,writer:String) {
        if(marker(ctx,media.id,endpoint).isFile)return
        val file=encrypted(ctx,media.id)
        val raw=if(file.isFile)file.readBytes() else {
            val previous=directory(ctx).listFiles().orEmpty().filter{it.name.startsWith(media.id+".") && it.name.endsWith(".uploaded")}.sortedByDescending{it.lastModified()}.map{it.readText()}.firstOrNull(Relay::encryptedEndpoint)?:error("Pièce jointe locale indisponible")
            http.newCall(SocialRequest.Builder().url(previous+"/media/"+media.id).maxResponseBytes(51*1024*1024).get().build()).execute().use{check(it.isSuccessful);it.body.bytes}
        }
        require(raw.size==media.bytes && digest(raw)==media.hash)
        http.newCall(SocialRequest.Builder().url(endpoint+"/media/"+media.id).header("X-Content-SHA256",media.hash).header("Authorization","Bearer "+Relay.publicationKey(ctx)).header("X-Write-Token",writer)
            .put(SocialBody(raw,"application/octet-stream")).build()).execute().use { response ->
            if(response.code==409) {
                val existing=http.newCall(SocialRequest.Builder().url(endpoint+"/media/"+media.id).maxResponseBytes(51*1024*1024).get().build()).execute().use { check(it.isSuccessful);it.body.bytes }
                require(existing.size==media.bytes && digest(existing)==media.hash)
            } else check(response.isSuccessful){"Média en attente d’envoi"}
        }
        marker(ctx,media.id,endpoint).writeText(endpoint);file.delete()
    }
    private class AppleTimelineHost(val ctx:Context):TimelineHost {
        private fun file()=AtomicFile(File(ctx.filesDir,"league/friend-history.json").also { it.parentFile?.mkdirs() })
        override fun now()=System.currentTimeMillis()
        override fun randomId()=UUID.randomUUID().toString().replace("-","")
        override fun own()=TimelinePeer(League.myId(),League.publicKey(),(app.superhealth.ui.userName()?:"?").take(24),Relay.myEndpoint(ctx)?:error("Activez le serveur du Groupe"),Relay.readKey(ctx),League.state.value.ownPosts,GroupIdentity.current().previous,GroupIdentity.current().endorsement)
        override fun peers()=League.state.value.friends.mapNotNull { friend->
            val endpoint=friend.remote?.takeIf(Relay::encryptedEndpoint)?:return@mapNotNull null
            val aes=friend.relayKey?.takeIf(RelayCrypto::validKey)?:return@mapNotNull null
            TimelinePeer(friend.id,friend.key,friend.shown,endpoint,aes,friend.posts)
        }
        override fun repliesClosed(author:String,post:String)=SocialSpaces.repository(ctx).legacyClosed(author,post)
        override fun replyHidden(author:String,post:String,reply:String)=SocialSpaces.repository(ctx).legacyHidden(author,post,reply)
        override fun blocked(key:String)=SocialPrivacy.blocked(key)
        override fun hash(text:String)=digest(text.toByteArray())
        override fun identityId(key:String)=Sha256.digest(Base64.getUrlDecoder().decode(key)).take(8).joinToString(""){"%02x".format(it)}
        override fun encode(text:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
        override fun decode(text:String)=Base64.getUrlDecoder().decode(text).decodeToString()
        override fun sign(text:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(signIdentity(League.socialIdentity().privateKey,text.toByteArray()))
        override fun verify(key:String,text:String,signature:String)=verifyIdentity(Base64.getUrlDecoder().decode(key),text.toByteArray(),Base64.getUrlDecoder().decode(signature))
        override fun validEndorsement(root:String?,key:String,signature:String?)=FriendSocial.validEndorsement(root,key,signature)
        override fun encrypt(text:String,aes:String,path:String,revision:Long)=RelayCrypto.encrypt(text,aes,path,revision)
        override fun decrypt(text:String,aes:String,path:String)=RelayCrypto.decrypt(text,aes,path)
        override fun readTimeline():String?=File(ctx.filesDir,"league/friend-history.json").takeIf { it.isFile }?.readText()
        override fun writeTimeline(text:String){val f=file();val out=f.startWrite();try{out.write(text.toByteArray());f.finishWrite(out)}catch(e:Exception){f.failWrite(out);throw e}}
        override suspend fun prepareMailbox(){
            League.configure(ctx){it.copy(ownPosts=sentPosts())}
            val raw=League.myCard(ctx,detailed=true,includeRelayKey=false);val card=League.parse(raw)?:error("Carte non valable")
            val me=own();put(me.endpoint,encrypt(raw,me.aes,"/"+me.endpoint.substringAfter("://").substringAfter('/'),card.revision))
        }
        override suspend fun get(url:String):String=http.newCall(SocialRequest.Builder().url(url).maxResponseBytes(1024*1024).get().build()).execute().use {
            check(it.isSuccessful){"Lecture du fil indisponible"};it.body.bytes.decodeToString()
        }
        override suspend fun put(url:String,body:String){
            http.newCall(SocialRequest.Builder().url(url).header("Authorization","Bearer "+Relay.publicationKey(ctx)).header("X-Write-Token",token(ctx,"league_owner_token_v2"))
                .put(body.toRequestBody("application/json")).build()).execute().use { check(it.isSuccessful){"Publication en attente d’envoi"} }
        }
        override suspend fun upload(media:FriendMedia) = sendMedia(ctx,media,own().endpoint,token(ctx,"league_owner_token_v2"))
        override suspend fun removeMedia(media:FriendMedia){
            http.newCall(SocialRequest.Builder().url(own().endpoint+"/media/"+media.id).header("Authorization","Bearer "+Relay.publicationKey(ctx)).header("X-Write-Token",token(ctx,"league_owner_token_v2"))
                .delete().build()).execute().use{check(it.isSuccessful||it.code==404)}
            marker(ctx,media.id).delete();encrypted(ctx,media.id).delete()
        }
    }
}
