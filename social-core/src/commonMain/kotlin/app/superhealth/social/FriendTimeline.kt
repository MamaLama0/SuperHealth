package app.superhealth.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import app.superhealth.league.GroupRecords
import app.superhealth.league.SharedActivity

/** DTOs match the shipped Android SHG1 protocol. Activity snapshots remain explicitly selected payloads. */
@Serializable data class FriendMedia(val id: String, val key: String, val mime: String, val hash: String, val bytes: Int)
@Serializable data class FriendPost(val id: String, val created: Long, val text: String = "", val kind: String = "TEXT", val title: String = "",
    val detail: String = "", val media: FriendMedia? = null, val record: JsonObject? = null, val workout: JsonObject? = null, val activity: JsonObject? = null)
@Serializable data class FriendReplyRef(val author: String, val id: String)
@Serializable data class FriendReply(val author: String, val post: String, val text: String, val created: Long, val to: FriendReplyRef? = null)
@Serializable data class FriendReaction(val author: String, val post: String, val kind: String, val at: Long)
@Serializable data class FriendProfile(val title: String? = null, val badges: List<String> = emptyList(), val avatar: FriendMedia? = null)
@Serializable data class FriendForward(val post: String, val key: String, val signed: String, val ownerRoot: String? = null, val ownerEndorsement: String? = null)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class FriendEvent(val id: String, val key: String, val name: String, val revision: Long, val post: FriendPost? = null,
    val reply: FriendReply? = null, val deleted: Boolean = false, val profile: FriendProfile? = null, val reaction: FriendReaction? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val forward: FriendForward? = null)
@Serializable data class TimelinePeer(val id: String, val key: String, val name: String, val endpoint: String, val aes: String, val posts: List<FriendPost> = emptyList(), val root: String?=null, val endorsement: String?=null)
@Serializable data class TimelineStore(val events: List<FriendEvent> = emptyList(), val pending: Set<String> = emptySet(),
    val cursors: Map<String, String?> = emptyMap(), val proofs: Map<String, String> = emptyMap(), val baseline: Boolean = false,
    val removals: List<FriendMedia> = emptyList(), val hidden: Set<String> = emptySet(), val muted: Set<String> = emptySet(), val silent: Set<String> = emptySet(), val paging:Map<String,PageProgress> = emptyMap(), val error: String? = null)
interface TimelineHost {
    fun now(): Long
    fun randomId(): String
    fun own(): TimelinePeer
    fun peers(): List<TimelinePeer>
    fun repliesClosed(author: String, post: String): Boolean = false
    fun replyHidden(author: String, post: String, reply: String): Boolean = false
    fun blocked(key: String): Boolean
    fun hash(text: String): String
    fun identityId(key: String): String
    fun encode(text: String): String
    fun decode(text: String): String
    fun sign(text: String): String
    fun verify(key: String, text: String, signature: String): Boolean
    fun validEndorsement(root: String?, key: String, signature: String?): Boolean
    fun encrypt(text: String, aes: String, path: String, revision: Long): String
    fun decrypt(text: String, aes: String, path: String): String
    fun readTimeline(): String?
    fun writeTimeline(text: String)
    suspend fun prepareMailbox()
    suspend fun get(url: String): String
    suspend fun put(url: String, body: String)
    suspend fun upload(media: FriendMedia)
    suspend fun removeMedia(media: FriendMedia)
}
object FriendTimelineRules {
    fun valid(post: FriendPost, now: Long) = post.id.matches(Regex("[a-f0-9]{32}")) && post.created in 1..(now + 300000) &&
        post.text.length <= 500 && post.title.length <= 100 && post.detail.length <= 1500 && post.kind in setOf("TEXT","PHOTO","VIDEO","WORKOUT","RUN","STREAK","PR") &&
        (post.kind != "PR" || post.record != null) && (post.media == null || valid(post.media)) &&
        runCatching {
            require(Json.encodeToString(FriendPost.serializer(),post).encodeToByteArray().size<=11000)
            post.record?.let { require(GroupRecords.valid(Json.decodeFromJsonElement(GroupRecords.Record.serializer(),it),now)) }
            post.workout?.let { require(SharedActivity.valid(Json.decodeFromJsonElement(SharedActivity.Strength.serializer(),it),now)) }
            post.activity?.let { require(SharedActivity.valid(Json.decodeFromJsonElement(SharedActivity.Outdoor.serializer(),it),now)) }
            true
        }.getOrDefault(false)
    fun valid(media: FriendMedia) = media.id.matches(Regex("[a-f0-9]{64}")) && media.hash.matches(Regex("[a-f0-9]{64}")) &&
        media.key.length in 40..48 && media.bytes in 29..(50 * 1024 * 1024 + 28) && media.mime in setOf("image/jpeg","image/png","image/webp","video/mp4","video/webm","video/3gpp")
    fun validEvent(event:FriendEvent,now:Long):Boolean = runCatching {
        require(event.id.matches(Regex("[a-f0-9]{32}")) && event.key.length in 32..512 && event.name.length<=24 && event.revision in 1..(now+300000))
        val fields=listOf(event.post,event.reply,event.profile,event.reaction,event.forward).count{it!=null}
        require(if(event.deleted)fields==0 else fields==1)
        event.post?.let{require(it.id==event.id && valid(it,now))}
        event.reply?.let{reply->require(reply.author.matches(Regex("[a-f0-9]{16}")) && reply.post.matches(Regex("[a-f0-9]{32}")) && reply.text.isNotBlank() && reply.text.length<=1000 && reply.created in 1..(now+300000));reply.to?.let{require(it.author.matches(Regex("[a-f0-9]{16}")) && it.id.matches(Regex("[a-f0-9]{32}")) && it.id!=event.id)}}
        event.reaction?.let{require(it.author.matches(Regex("[a-f0-9]{16}")) && it.post.matches(Regex("[a-f0-9]{32}")) && it.kind in setOf("LIKE","DISLIKE","HEART","FIRE","CLAP","LAUGH") && it.at in 1..(now+300000))}
        event.profile?.let{require(app.superhealth.league.Achievements.validDecor(it.title,it.badges) && (it.avatar==null || valid(it.avatar)))}
        event.forward?.let{require(it.post.matches(Regex("[a-f0-9]{32}")) && it.key.length in 32..512 && it.signed.length<=14000)}
        true
    }.getOrDefault(false)
    fun merge(events: List<FriendEvent>, event: FriendEvent): List<FriendEvent> {
        val previous = events.firstOrNull { it.key == event.key && it.id == event.id }
        if (previous != null && previous.revision >= event.revision) return events
        return events.filterNot { it.key == event.key && it.id == event.id } + event
    }
}

/** Portable reader/writer for the original friend feed, including owner-endorsed relayed comments. */
class FriendTimeline(private val host: TimelineHost) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val lock = Mutex()
    val state = MutableStateFlow(host.readTimeline()?.let { json.decodeFromString(TimelineStore.serializer(),it) } ?: TimelineStore())
    private fun save(next: TimelineStore) { host.writeTimeline(json.encodeToString(TimelineStore.serializer(),next)); state.value=next }
    private fun path(url: String) = "/" + url.substringAfter("://").substringAfter('/')
    private fun signed(event: FriendEvent): String {
        val payload=json.encodeToString(FriendEvent.serializer(),event)
        require(payload.encodeToByteArray().size<=12000)
        return "SHG1.${host.encode(payload)}.${host.sign("SuperHealth group v1:"+payload)}"
    }
    private fun verified(raw: String,key: String): FriendEvent {
        require(raw.length<=20000)
        val parts=raw.split('.');require(parts.size==3 && parts[0]=="SHG1")
        val payload=host.decode(parts[1]);require(payload.encodeToByteArray().size<=12000 && host.verify(key,"SuperHealth group v1:"+payload,parts[2]))
        val event=json.decodeFromString(FriendEvent.serializer(),payload)
        require(event.key==key && event.id.matches(Regex("[a-f0-9]{32}")) && event.revision in 1..(host.now()+300000) && event.name.length<=24)
        require(FriendTimelineRules.validEvent(event,host.now()))
        val fields=listOf(event.post,event.reply,event.profile,event.reaction,event.forward).count { it!=null }
        require(if(event.deleted) fields==0 else fields==1)
        event.post?.let { require(it.id==event.id && FriendTimelineRules.valid(it,host.now())) }
        event.reply?.let { require(it.author.matches(Regex("[a-f0-9]{16}")) && it.post.matches(Regex("[a-f0-9]{32}")) && it.text.length in 1..1000 && it.created in 1..(host.now()+300000)) }
        event.reaction?.let { require(it.author.matches(Regex("[a-f0-9]{16}")) && it.post.matches(Regex("[a-f0-9]{32}")) && it.kind.length in 1..32 && it.at in 1..(host.now()+300000)) }
        return event
    }
    private fun ownEvent(id: String, post: FriendPost?=null, reply: FriendReply?=null, reaction: FriendReaction?=null, deleted:Boolean=false):FriendEvent {
        val me=host.own();val previous=state.value.events.firstOrNull { it.key==me.key && it.id==id }
        return FriendEvent(id,me.key,me.name,maxOf(host.now(),(previous?.revision?:0)+1),post,reply,deleted,reaction=reaction)
    }
    private fun enqueue(event:FriendEvent) = save(state.value.copy(events=FriendTimelineRules.merge(state.value.events,event),pending=state.value.pending+event.id))
    suspend fun publish(post:FriendPost) = lock.withLock {
        require(FriendTimelineRules.valid(post,host.now()))
        enqueue(ownEvent(post.id,post=post))
    }
    suspend fun edit(post:FriendPost,text:String) = lock.withLock {
        require(state.value.events.any { it.key==host.own().key && it.post?.id==post.id })
        val updated=post.copy(text=text.trim().take(500));require(FriendTimelineRules.valid(updated,host.now()))
        enqueue(ownEvent(post.id,post=updated))
    }
    suspend fun delete(post:FriendPost) = lock.withLock {
        require(state.value.events.any { it.key==host.own().key && it.post?.id==post.id })
        val event = ownEvent(post.id, deleted = true)
        save(state.value.copy(events = FriendTimelineRules.merge(state.value.events, event), pending = state.value.pending + event.id,
            removals = (state.value.removals + listOfNotNull(post.media)).distinctBy { it.id }))
    }
    suspend fun reply(author:String,post:String,text:String,parent:FriendEvent?=null) = lock.withLock {
        require(!host.repliesClosed(author,post)) { "Les réponses sont fermées." }
        require(text.trim().length in 1..1000 && posts().any { it.first.id==post && it.second.id==author })
        require(parent == null || replies(author, post).any { it.key == parent.key && it.id == parent.id })
        enqueue(ownEvent(host.randomId(),reply=FriendReply(author,post,text.trim(),host.now(),parent?.let { FriendReplyRef(host.identityId(it.key),it.id) })))
    }
    suspend fun deleteReply(event:FriendEvent) = lock.withLock {
        require(event.key==host.own().key);enqueue(ownEvent(event.id,deleted=true))
    }
    suspend fun react(author:String,post:String,kind:String) = lock.withLock {
        require(kind in setOf("LIKE","DISLIKE","HEART","FIRE","CLAP","LAUGH"))
        val id=host.hash("SuperHealth reaction v1:$author:$post").take(32)
        val old=state.value.events.firstOrNull { it.key==host.own().key && it.id==id }
        enqueue(if(old?.reaction?.kind==kind) ownEvent(id,deleted=true) else ownEvent(id,reaction=FriendReaction(author,post,kind,host.now())))
    }
    suspend fun hide(author:String,post:String) = lock.withLock { save(state.value.copy(hidden=state.value.hidden+"$author/$post")) }
    suspend fun restoreHidden() = lock.withLock { save(state.value.copy(hidden=emptySet())) }
    suspend fun restoreSilent()=lock.withLock { save(state.value.copy(silent=emptySet())) }
    suspend fun silence(author:String,post:String,on:Boolean)=lock.withLock { val id="$author/$post";save(state.value.copy(silent=if(on)state.value.silent+id else state.value.silent-id)) }
    suspend fun mute(author:String,on:Boolean) = lock.withLock { save(state.value.copy(muted=if(on) state.value.muted+author else state.value.muted-author)) }
    fun isOwn(event: FriendEvent) = event.key == host.own().key
    fun posts():List<Pair<FriendPost,TimelinePeer>> {
        val peers=(listOf(host.own())+host.peers()).associateBy { it.key }
        val archived=state.value.events.filter { !it.deleted && it.post!=null && it.key in peers }.map { it.post!! to peers.getValue(it.key) }
        val legacy=peers.values.flatMap { peer->peer.posts.map { it to peer } }
        val deleted=state.value.events.filter { it.deleted }.map { it.key to it.id }.toSet()
        return (archived+legacy).filter { (post,peer)->FriendTimelineRules.valid(post,host.now()) && post.created>=host.now()-1830L*86400000 &&
            peer.key to post.id !in deleted && "${peer.id}/${post.id}" !in state.value.hidden && peer.id !in state.value.muted && !host.blocked(peer.key) }
            .distinctBy { it.second.id to it.first.id }.sortedByDescending { it.first.created }
    }
    private fun unwrap(event:FriendEvent):FriendEvent? = runCatching {
        val forward=event.forward?:return@runCatching event
        require(host.validEndorsement(forward.ownerRoot,event.key,forward.ownerEndorsement))
        val inner=verified(forward.signed,forward.key)
        require(inner.forward==null && (inner.reply!=null || inner.deleted) && event.id==host.hash("SuperHealth comment forward v1:"+inner.key+":"+inner.id).take(32))
        inner.reply?.let { require(it.author==host.identityId(forward.ownerRoot?:event.key) && it.post==forward.post) }
        inner
    }.getOrNull()
    fun resolvedEvents(store:TimelineStore=state.value):List<FriendEvent> = store.events.mapNotNull(::unwrap).groupBy{it.key to it.id}.values.map{versions->versions.maxBy{it.revision}}
    fun replies(author:String,post:String):List<FriendEvent> {
        val peers=(listOf(host.own())+host.peers());val owner=peers.firstOrNull { it.id==author }?:return emptyList()
        val trusted=peers.map { it.key }.toSet()
        return state.value.events.filter { (it.forward==null && it.key in trusted) || (it.key==owner.key && it.forward?.post==post) }
            .mapNotNull(::unwrap).groupBy { it.key to it.id }.values.map { versions->versions.maxBy { it.revision } }
            .filter { !it.deleted && it.reply?.author==author && it.reply.post==post && !host.blocked(it.key) && !host.replyHidden(author,post,it.id) }.sortedBy { it.reply!!.created }
    }
    fun reactions(author:String,post:String):Map<String,Int> = state.value.events.filter { !it.deleted && it.reaction?.author==author && it.reaction.post==post && !host.blocked(it.key) }
        .distinctBy { it.key }.groupingBy { it.reaction!!.kind }.eachCount()
    private suspend fun flush() {
        host.prepareMailbox();val me=host.own()
        val ownPosts=state.value.events.filter { it.key==me.key && it.post!=null && !it.deleted }.map { it.id }.toSet()
        for((proofId,raw) in state.value.proofs) {
            val key=proofId.substringBefore(':');val inner=verified(raw,key)
            val id=host.hash("SuperHealth comment forward v1:"+key+":"+inner.id).take(32)
            val old=state.value.events.firstOrNull { it.key==me.key && it.id==id }
            val post=inner.reply?.takeIf { it.author==me.id }?.post?:old?.forward?.post?:continue
            if(post !in ownPosts || old?.forward?.signed==raw) continue
            val event=ownEvent(id).copy(forward=FriendForward(post,key,raw,me.root,me.endorsement))
            enqueue(event)
        }
        for(id in state.value.pending.toList()) {
            val current=state.value.events.first { it.key==me.key && it.id==id }
            current.post?.media?.let { host.upload(it) }
            val event=current.copy(revision=maxOf(host.now(),current.revision+1));enqueue(event)
            val url=me.endpoint+"/social/"+id
            host.put(url,host.encrypt(signed(event),me.aes,path(url),event.revision))
            save(state.value.copy(pending=state.value.pending-id))
        }
        for (media in state.value.removals) {
            host.removeMedia(media)
            save(state.value.copy(removals = state.value.removals.filterNot { it.id == media.id }))
        }
        host.prepareMailbox()
    }
    suspend fun republishOwn() = lock.withLock {
        val own=host.own().key;val events=state.value.events.filter{it.key==own}
        events.forEach { enqueue(it.copy(revision=maxOf(host.now(),it.revision+1))) }
    }
    suspend fun publishPending() = lock.withLock { flush() }
    suspend fun sync(older:Boolean=false,ownOnly:Boolean=false) = lock.withLock {
        val failures=mutableListOf<String>()
        try { flush() } catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){failures+="Publications en attente d’envoi"}
        for(peer in (listOf(host.own()) + if(ownOnly) emptyList() else host.peers()).filterNot { host.blocked(it.key) }) try {
            val cursor=if(older)state.value.cursors[peer.key] else null
            if(older && cursor==null)continue
            val page=json.decodeFromString(SpacePage.serializer(),host.get(peer.endpoint+"/social"+(cursor?.let { "?before=$it" }?:"")))
            require(page.items.size<=20 && (page.next==null || page.next.matches(Regex("[0-9]{16}:[a-f0-9]{32}"))))
            for(item in page.items) try {
                val raw=host.decrypt(item.raw,peer.aes,path(peer.endpoint+"/social/"+item.id));val event=verified(raw,peer.key)
                require(event.id==item.id)
                save(state.value.copy(events=FriendTimelineRules.merge(state.value.events,event),proofs=if(event.forward==null && (event.reply!=null || event.deleted))state.value.proofs+("${event.key}:${event.id}" to raw)else state.value.proofs))
            }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){failures+="Un message a été refusé"}
            val paging=(state.value.paging[peer.key]?:PageProgress.legacy(state.value.cursors[peer.key])).advance(cursor,page.next)
            save(state.value.copy(cursors=state.value.cursors+(peer.key to paging.next),paging=state.value.paging+(peer.key to paging)))
        }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){failures+="Une partie du fil est indisponible"}
        try { flush() } catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){failures+="Publications en attente d’envoi"}
        save(state.value.copy(baseline=true,error=failures.distinct().joinToString(" · ").ifBlank { null }))
    }
}
