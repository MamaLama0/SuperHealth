package app.superhealth.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire protocol 1 for isolated social streams. No health card or journal is accepted here. */
@Serializable data class SpaceContact(val id: String, val key: String, val name: String, val endpoint: String, val aes: String, val root: String? = null, val endorsement: String? = null)
@Serializable data class Circle(val id: String, val name: String, val owner: SpaceContact, val members: List<SpaceContact>, val epoch: Long,
    val closed: Boolean = false)
@Serializable data class CircleAccess(val circle: Circle, val keys: Map<Long, String>, val departed: Boolean = false, val removalConfirmed: Boolean = false)
@Serializable data class CircleInvitation(val circle: String, val name: String, val owner: SpaceContact, val accepted: Boolean = false)
@Serializable data class CircleGrant(val circle: Circle, val aes: String)
@Serializable data class SpaceAttachment(val id: String, val key: String, val mime: String, val hash: String, val bytes: Int, val endpoint: String)
@Serializable data class SpaceAction(val kind: String, val id: String, val created: Long, val text: String = "", val parent: String? = null,
    val source: String? = null, val target: Int = 0, val start: Long = 0, val end: Long = 0, val modified: Long? = null, val media: SpaceAttachment? = null, val deleted: Boolean = false, val snapshot: FriendPost? = null)
@Serializable data class CircleDraft(val id: String = "", val text: String = "", val reply: String? = null, val media: SpaceAttachment? = null, val snapshot: FriendPost? = null)
@Serializable data class SpaceRecord(val circle: String, val author: SpaceContact, val revision: Long, val epoch: Long, val action: SpaceAction)
@Serializable data class SpaceFrame(val v: Int = 1, val id: String, val key: String, val revision: Long, val type: String,
    val recipient: String = "", val circle: String = "", val epoch: Long = 0, val body: String)
@Serializable data class SignedSpaceFrame(val frame: SpaceFrame, val signature: String)
@Serializable data class SpaceMediaRemoval(val media: SpaceAttachment, val action: String)
@Serializable data class LegacyControl(val owner: String, val post: String, val kind: String, val target: String = "", val active: Boolean = true, val revision: Long = 0)
@Serializable data class SpaceStore(val own: SpaceContact? = null, val frames: List<SignedSpaceFrame> = emptyList(),
    val pending: Set<String> = emptySet(), val circles: List<CircleAccess> = emptyList(),
    val invitations: List<CircleInvitation> = emptyList(), val invited: Map<String, List<SpaceContact>> = emptyMap(),
    val records: List<SpaceRecord> = emptyList(), val seen: Map<String, Long> = emptyMap(),
    val cursors: Map<String, String?> = emptyMap(), val transport: Map<String, Long> = emptyMap(), val lastRevision: Long = 0, val withdrawals: Map<String, SpaceContact> = emptyMap(), val withdrawalPeers: List<SpaceContact> = emptyList(), val withdrawalAcks: Set<String> = emptySet(), val silentCircles: Set<String> = emptySet(), val removals: List<SpaceMediaRemoval> = emptyList(), val notificationBaseline: Boolean=false, val paging:Map<String,PageProgress> = emptyMap(), val deferredRecords:List<SpaceRecord> = emptyList(), val rejected: Set<String> = emptySet(), val legacy: List<LegacyControl> = emptyList(), val error: String? = null)
@Serializable data class SpaceRemoteItem(val id: String, val raw: String)
@Serializable data class SpacePage(val items: List<SpaceRemoteItem>, val next: String? = null)

/** Platform boundaries are explicit so Android, desktop tests and iOS use the same reducer and protocol. */
interface SpaceHost {
    fun now(): Long
    fun randomId(): String
    fun newKey(): String
    fun validContact(contact: SpaceContact): Boolean
    fun validKey(key: String): Boolean
    fun hash(text: String): String
    fun sign(text: String): String
    fun verify(key: String, text: String, signature: String): Boolean
    fun sharedKey(key: String): String
    fun encrypt(text: String, key: String, domain: String, revision: Long): String
    fun decrypt(text: String, key: String, domain: String): String
    fun readStore(): String?
    fun writeStore(text: String)
    fun readDraft(circle: String): String? = null
    fun writeDraft(circle: String, text: String) {}
    fun contacts(): List<SpaceContact>
    fun blocked(key: String): Boolean
    fun withdrawContact(contact: SpaceContact) {}
    suspend fun removeAttachment(media: SpaceAttachment) {}
    suspend fun sendAttachment(media: SpaceAttachment) { error("Media transfer is unavailable") }
    suspend fun createStream(): SpaceContact
    suspend fun get(url: String): String
    suspend fun put(url: String, body: String)
}

object SpaceRules {
    fun valid(action: SpaceAction, now: Long): Boolean = action.id.matches(Regex("[a-f0-9]{32}")) &&
        action.created in 1..(now + 300000) && action.text.length <= 1000 && (action.snapshot == null || (FriendTimelineRules.valid(action.snapshot, now) && action.snapshot.media == null && Json.encodeToString(FriendPost.serializer(),action.snapshot).encodeToByteArray().size <= 6000)) && (action.media == null ||
        (action.media.id.matches(Regex("[a-f0-9]{64}")) && action.media.hash.matches(Regex("[a-f0-9]{64}")) && action.media.key.length in 40..48 &&
            action.media.bytes in 29..(50 * 1024 * 1024 + 28) && action.media.endpoint.length <= 512)) && when (action.kind) {
            "POST" -> action.parent == null && (action.deleted || action.text.isNotBlank() || action.media != null || action.snapshot != null)
            "REPLY" -> action.parent?.matches(Regex("[a-f0-9]{32}")) == true && (action.deleted || action.text.isNotBlank() || action.media != null || action.snapshot != null)
            "REACTION" -> action.parent?.matches(Regex("[a-f0-9]{32}")) == true && (action.deleted || action.text in setOf("👏", "💪", "❤️", "🔥", "👍"))
            "CHALLENGE" -> action.parent == null && action.text.isNotBlank() && action.target in 1..1000 && action.start > 0 &&
                action.end > action.start && action.end - action.start <= 90L * 86400000
            "CONTRIBUTION" -> action.parent?.matches(Regex("[a-f0-9]{32}")) == true && action.source?.matches(Regex("[a-f0-9]{64}")) == true
            "CLOSE_REPLIES", "HIDE_REPLY" -> action.parent?.matches(Regex("[a-f0-9]{32}")) == true
            else -> false
        }
    fun latest(records: List<SpaceRecord>): List<SpaceRecord> = records.groupBy { Triple(it.circle, it.author.id, it.action.id) }
        .values.map { versions -> versions.maxBy { it.revision } }
    fun total(records: List<SpaceRecord>, challenge: SpaceRecord, now: Long): Int {
        if (challenge.action.deleted) return 0
        return latest(records).filter { record ->
            val action = record.action
            record.circle == challenge.circle && action.kind == "CONTRIBUTION" && action.parent == challenge.action.id &&
                !action.deleted && action.created in challenge.action.start..minOf(now, challenge.action.end)
        }.distinctBy { it.author.id to it.action.source }.size
    }
    fun validCircle(circle: Circle, host: SpaceHost) = circle.id.matches(Regex("[a-f0-9]{32}")) && circle.name.isNotBlank() &&
        circle.name.length <= 60 && circle.epoch in 1..1000000 && circle.members.size in 1..8 &&
        circle.members.distinctBy { it.id }.size == circle.members.size && circle.owner in circle.members &&
        circle.members.all(host::validContact)
}

/** All mutations, HTTP retries and durable commits share one mutex. Readers observe immutable snapshots. */
class SpaceRepository(private val host: SpaceHost) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val lock = Mutex()
    val state = MutableStateFlow(host.readStore()?.let { json.decodeFromString(SpaceStore.serializer(), it) } ?: SpaceStore())
    private fun save(next: SpaceStore) { host.writeStore(json.encodeToString(SpaceStore.serializer(), next)); state.value = next }
    private suspend fun own(): SpaceContact {
        state.value.own?.let { return it }
        val contact = host.createStream(); require(host.validContact(contact))
        save(state.value.copy(own = contact)); return contact
    }
    suspend fun initialize() = lock.withLock { own() }
    fun loadDraft(circle: String): CircleDraft = host.readDraft(circle)?.let { json.decodeFromString(CircleDraft.serializer(), it) } ?: CircleDraft()
    fun saveDraft(circle: String, draft: CircleDraft) { require(circle.matches(Regex("[a-f0-9]{32}"))); host.writeDraft(circle,json.encodeToString(CircleDraft.serializer(),draft)) }
    fun sourceToken(circle:String,source:String):String = host.hash("SuperHealth challenge source v1:"+host.sharedKey(state.value.own!!.key)+":"+circle+":"+source)
    fun actionId(): String = state.value.own!!.id + host.randomId().take(16)
    private fun revision(): Long = maxOf(host.now(), state.value.lastRevision + 1).also { save(state.value.copy(lastRevision = it)) }
    private fun privateDomain(frame: SpaceFrame) = "SuperHealth spaces private v1:${frame.key}:${frame.recipient}:${frame.id}:${frame.revision}"
    private fun circleDomain(frame: SpaceFrame) = "SuperHealth spaces circle v1:${frame.circle}:${frame.epoch}:${frame.id}:${frame.revision}"
    private fun persistFrame(frame: SpaceFrame) {
        val signed = SignedSpaceFrame(frame, host.sign(json.encodeToString(SpaceFrame.serializer(), frame)))
        save(state.value.copy(frames = state.value.frames.filterNot { it.frame.id == frame.id } + signed, pending = state.value.pending + frame.id))
    }
    private fun privateFrame(contact: SpaceContact, type: String, circle: String, body: String) {
        val me = state.value.own!!
        val id = if (type == "GRANT" || type == "REMOVED") host.hash("SuperHealth circle grant v1:$circle:${contact.key}").take(32) else host.randomId()
        val frame = SpaceFrame(id = id, key = me.key, revision = revision(), type = type, recipient = host.hash(contact.key), circle = circle, body = "")
        persistFrame(frame.copy(body = host.encrypt(body, host.sharedKey(contact.key), privateDomain(frame), frame.revision)))
    }
    suspend fun silence(circle:String,on:Boolean)=lock.withLock { save(state.value.copy(silentCircles=if(on)state.value.silentCircles+circle else state.value.silentCircles-circle)) }
    fun legacyClosed(owner: String, post: String) = state.value.legacy.any { it.owner==owner && it.post==post && it.kind=="CLOSE" && it.active }
    fun legacyHidden(owner: String, post: String, reply: String) = state.value.legacy.any { it.owner==owner && it.post==post && it.kind=="HIDE" && it.target==reply && it.active }
    fun legacyModified(owner: String, post: String) = state.value.legacy.any { it.owner==owner && it.post==post && it.kind=="EDIT" }
    private fun applyLegacy(control: LegacyControl) {
        val old=state.value.legacy.firstOrNull { it.owner==control.owner && it.post==control.post && it.kind==control.kind && it.target==control.target }
        if(old!=null && old.revision>=control.revision)return
        save(state.value.copy(legacy=state.value.legacy.filterNot { it==old }+control))
    }
    suspend fun moderateLegacy(post: String, kind: String, target: String="", active: Boolean=true) = lock.withLock {
        val me=own();require(post.matches(Regex("[a-f0-9]{32}")) && kind in setOf("CLOSE","HIDE","EDIT"))
        require(target.isEmpty() || target.matches(Regex("[a-f0-9]{32}")))
        val control=LegacyControl(me.id,post,kind,target,active,revision())
        val id=host.hash("SuperHealth legacy control v1:${me.key}:$post:$kind:$target").take(32)
        persistFrame(SpaceFrame(id=id,key=me.key,revision=control.revision,type="LEGACY",body=json.encodeToString(LegacyControl.serializer(),control)))
        applyLegacy(control)
    }
    suspend fun requestFriendWithdrawals() = lock.withLock {
        val me = own()
        val peers = host.contacts().filterNot { host.blocked(it.key) }
        save(state.value.copy(withdrawalPeers = (state.value.withdrawalPeers + peers).distinctBy { it.id }))
        peers.filterNot { it.id in state.value.withdrawalAcks }.forEach { privateFrame(it, "WITHDRAW_FRIEND", "", me.id) }
    }
    suspend fun acknowledgeWithdrawals(ids: Set<String>) = lock.withLock {
        ids.forEach { id -> state.value.withdrawals[id]?.let { peer -> privateFrame(peer, "WITHDRAWN_ACK", "", peer.id) } }
        save(state.value.copy(withdrawals = state.value.withdrawals - ids))
    }
    suspend fun createCircle(name: String) = lock.withLock {
        require(name.trim().length in 1..60 && state.value.circles.size < 12)
        val me = own(); val circle = Circle(host.randomId(), name.trim(), me, listOf(me), 1)
        save(state.value.copy(circles = state.value.circles + CircleAccess(circle, mapOf(1L to host.newKey()))))
        circle.id
    }
    suspend fun invite(circleId: String, contact: SpaceContact) = lock.withLock {
        require(host.validContact(contact) && !host.blocked(contact.key))
        val access = state.value.circles.first { it.circle.id == circleId }
        require(access.circle.owner.key == own().key && !access.circle.closed && access.circle.members.size + state.value.invited[circleId].orEmpty().size < 8)
        require(contact.id !in access.circle.members.map { it.id })
        val invited = (state.value.invited[circleId].orEmpty().filterNot { it.id == contact.id } + contact)
        save(state.value.copy(invited = state.value.invited + (circleId to invited)))
        privateFrame(contact, "INVITE", circleId, access.circle.name)
    }
    suspend fun cancelInvitation(circleId: String, memberId: String) = lock.withLock {
        val access = state.value.circles.first { it.circle.id == circleId }
        require(access.circle.owner.key == own().key)
        val contact = state.value.invited[circleId].orEmpty().first { it.id == memberId }
        privateFrame(contact, "CANCEL", circleId, "")
        save(state.value.copy(invited = state.value.invited + (circleId to state.value.invited[circleId].orEmpty().filterNot { it.id == memberId })))
    }
    suspend fun accept(circleId: String, accept: Boolean) = lock.withLock {
        own()
        val invite = state.value.invitations.first { it.circle == circleId }
        privateFrame(invite.owner, if (accept) "ACCEPT" else "DECLINE", circleId, "")
        save(state.value.copy(invitations = if (accept) state.value.invitations.map { if (it.circle == circleId) it.copy(accepted = true) else it }
            else state.value.invitations.filterNot { it.circle == circleId }))
    }
    private fun rotate(access: CircleAccess, members: List<SpaceContact>, closed: Boolean = false) {
        val circle = access.circle.copy(members = members, epoch = access.circle.epoch + 1, closed = closed)
        require(SpaceRules.validCircle(circle, host))
        val key = host.newKey()
        val epochRevision=revision()
        val updated = access.copy(circle = circle, keys = access.keys + (circle.epoch to key))
        save(state.value.copy(circles = state.value.circles.map { if (it.circle.id == circle.id) updated else it },seen=state.value.seen+("epoch:${circle.id}" to epochRevision)))
        members.filterNot { it.key == circle.owner.key }.forEach {
            privateFrame(it, "GRANT", circle.id, json.encodeToString(CircleGrant.serializer(), CircleGrant(circle, key)))
        }
    }
    suspend fun removeMember(circleId: String, memberId: String) = lock.withLock {
        val access = state.value.circles.first { it.circle.id == circleId }
        require(access.circle.owner.key == own().key && memberId != access.circle.owner.id)
        val removed = access.circle.members.first { it.id == memberId }
        rotate(access, access.circle.members.filterNot { it.id == memberId })
        privateFrame(removed, "REMOVED", circleId, "")
    }
    suspend fun closeCircle(circleId: String) = lock.withLock {
        val access = state.value.circles.first { it.circle.id == circleId }
        require(access.circle.owner.key == own().key)
        rotate(access, access.circle.members, closed = true)
    }
    suspend fun leave(circleId: String) = lock.withLock {
        val access = state.value.circles.first { it.circle.id == circleId }
        require(access.circle.owner.key != own().key)
        privateFrame(access.circle.owner, "LEAVE", circleId, "")
        save(state.value.copy(circles = state.value.circles.map { if (it.circle.id == circleId) it.copy(departed = true) else it }))
    }
    suspend fun publish(circleId: String, action: SpaceAction) = lock.withLock {
        val me = own(); val access = state.value.circles.first { it.circle.id == circleId }
        require(!access.departed && !access.circle.closed && access.circle.members.any { it.key == me.key })
        require(action.id.startsWith(me.id) && SpaceRules.valid(action, host.now()))
        val previous = state.value.records.firstOrNull { it.circle == circleId && it.author.id == me.id && it.action.id == action.id }
        require(previous == null || previous.action.kind == action.kind)
        require(previous?.action?.deleted != true || action.deleted || action.kind !in setOf("POST","REPLY","CHALLENGE")) { "Publication supprimée" }
        validateAction(access, me, action)
        val frame = SpaceFrame(id = action.id, key = me.key, revision = maxOf(revision(), (previous?.revision ?: 0) + 1), type = "CIRCLE",
            circle = circleId, epoch = access.circle.epoch, body = "")
        persistFrame(frame.copy(body = host.encrypt(json.encodeToString(SpaceAction.serializer(), action), access.keys.getValue(access.circle.epoch), circleDomain(frame), frame.revision)))
        val removal=previous?.action?.media?.takeIf{action.deleted}?.let{SpaceMediaRemoval(it,action.id)}
        save(state.value.copy(records = SpaceRules.latest(state.value.records + SpaceRecord(circleId, me, frame.revision, frame.epoch, action)),removals=(state.value.removals+listOfNotNull(removal)).distinctBy{it.media.id}))
    }
    private fun validateAction(access: CircleAccess, author: SpaceContact, action: SpaceAction, enforceDeadline: Boolean = true) {
        require(action.media == null || action.media.endpoint == author.endpoint)
        val records = SpaceRules.latest(state.value.records).filter { it.circle == access.circle.id }
        if (action.kind == "CONTRIBUTION") {
            val challenge = records.firstOrNull { it.action.id == action.parent && it.action.kind == "CHALLENGE" && !it.action.deleted } ?: error("Défi indisponible")
            require(action.deleted || action.created in challenge.action.start..challenge.action.end)
            require(action.deleted || !enforceDeadline || host.now() <= challenge.action.end)
        }
        if (action.kind == "CLOSE_REPLIES" || action.kind == "HIDE_REPLY") {
            val parent = records.firstOrNull { it.action.id == action.parent } ?: error("Publication indisponible")
            val post = if (parent.action.kind == "REPLY") records.firstOrNull { it.action.id == parent.action.parent } else parent
            require(post?.author?.id == author.id)
        }
        if (action.kind == "REPLY") {
            require(records.any { it.action.id == action.parent && it.action.kind == "POST" && !it.action.deleted })
            require(records.none { it.action.kind == "CLOSE_REPLIES" && it.action.parent == action.parent && !it.action.deleted && action.created >= it.action.created })
        }
    }
    private fun missingContext(record:SpaceRecord):Boolean {
        val action=record.action
        if(action.kind !in setOf("REPLY","CONTRIBUTION","CLOSE_REPLIES","HIDE_REPLY"))return false
        val records=state.value.records.filter{it.circle==record.circle}
        val parent=records.firstOrNull{it.action.id==action.parent}?:return true
        return action.kind=="HIDE_REPLY" && parent.action.kind=="REPLY" && records.none{it.action.id==parent.action.parent}
    }
    private fun retryDeferred() {
        var progress=true
        while(progress) {
            progress=false
            for(record in state.value.deferredRecords) {
                if(missingContext(record))continue
                val access=state.value.circles.firstOrNull{it.circle.id==record.circle}
                val accepted=access!=null && !host.blocked(record.author.key) && record.author.key in access.circle.members.map{it.key} &&
                    runCatching{validateAction(access,record.author,record.action,enforceDeadline=false)}.isSuccess
                save(state.value.copy(records=if(accepted)SpaceRules.latest(state.value.records+record)else state.value.records,deferredRecords=state.value.deferredRecords-record))
                progress=true
            }
        }
    }
    private fun receive(sender: SpaceContact, signed: SignedSpaceFrame) {
        val frame = signed.frame
        require(frame.v == 1 && frame.id.matches(Regex("[a-f0-9]{32}")) && frame.key == sender.key && frame.body.length <= 18000 &&
            frame.revision in 1..(host.now() + 300000) && host.verify(sender.key, json.encodeToString(SpaceFrame.serializer(), frame), signed.signature))
        if (host.blocked(sender.key) && frame.type !in setOf("GRANT","REMOVED","LEAVE","WITHDRAWN_ACK")) return
        val me = state.value.own ?: return
        if(frame.type=="LEGACY") {
            val control=json.decodeFromString(LegacyControl.serializer(),frame.body)
            require(control.owner==sender.id && control.revision==frame.revision && control.post.matches(Regex("[a-f0-9]{32}")) && control.kind in setOf("CLOSE","HIDE","EDIT") && (control.target.isEmpty() || control.target.matches(Regex("[a-f0-9]{32}"))))
            applyLegacy(control);return
        }
        if (frame.type == "CIRCLE") {
            val access = state.value.circles.firstOrNull { it.circle.id == frame.circle } ?: return
            if (access.departed || sender.key !in access.circle.members.map { it.key } || (access.circle.closed && frame.epoch==access.circle.epoch)) return
            val aes = access.keys[frame.epoch] ?: return
            val action = json.decodeFromString(SpaceAction.serializer(), host.decrypt(frame.body, aes, circleDomain(frame)))
            require(action.id == frame.id && action.id.startsWith(sender.id) && SpaceRules.valid(action, host.now()))
            // Former members cannot inject future records using an old epoch after roster rotation.
            if (frame.epoch != access.circle.epoch && frame.revision >= (state.value.seen["epoch:${frame.circle}"] ?: Long.MAX_VALUE)) return
            val record=SpaceRecord(frame.circle,sender,frame.revision,frame.epoch,action)
            if(missingContext(record)) {
                save(state.value.copy(deferredRecords=SpaceRules.latest(state.value.deferredRecords+record).takeLast(5000)))
                return
            }
            validateAction(access, sender, action, enforceDeadline = false)
            save(state.value.copy(records = SpaceRules.latest(state.value.records + record)))
            return
        }
        if (frame.recipient != host.hash(me.key)) return
        val body = host.decrypt(frame.body, host.sharedKey(sender.key), privateDomain(frame))
        when (frame.type) {
            "WITHDRAW_FRIEND" -> {
                require(body == sender.id)
                if (host.contacts().none { it.key == sender.key }) return
                save(state.value.copy(withdrawals = state.value.withdrawals + (sender.id to sender)))
                host.withdrawContact(sender)
            }
            "WITHDRAWN_ACK" -> {
                require(body == me.id && state.value.withdrawalPeers.any { it.key == sender.key })
                save(state.value.copy(withdrawalAcks = state.value.withdrawalAcks + sender.id))
            }
            "INVITE" -> {
                require(frame.circle.matches(Regex("[a-f0-9]{32}")) && body.length in 1..60)
                if (state.value.circles.none { it.circle.id == frame.circle } && state.value.invitations.none { it.circle == frame.circle })
                    save(state.value.copy(invitations = state.value.invitations + CircleInvitation(frame.circle, body, sender)))
            }
            "CANCEL" -> save(state.value.copy(invitations = state.value.invitations.filterNot { it.circle == frame.circle && it.owner.key == sender.key }))
            "ACCEPT" -> {
                val access = state.value.circles.firstOrNull { it.circle.id == frame.circle && it.circle.owner.key == me.key } ?: return
                if (sender !in state.value.invited[frame.circle].orEmpty()) return
                if (access.circle.members.none { it.id == sender.id }) rotate(access, access.circle.members + sender)
                save(state.value.copy(invited = state.value.invited + (frame.circle to state.value.invited[frame.circle].orEmpty().filterNot { it.id == sender.id })))
            }
            "DECLINE" -> save(state.value.copy(invited = state.value.invited + (frame.circle to state.value.invited[frame.circle].orEmpty().filterNot { it.id == sender.id })))
            "GRANT" -> {
                val grant = json.decodeFromString(CircleGrant.serializer(), body)
                if(host.blocked(sender.key) && !grant.circle.closed)return
                require(grant.circle.id == frame.circle && grant.circle.owner == sender && SpaceRules.validCircle(grant.circle, host) && host.validKey(grant.aes))
                val old = state.value.circles.firstOrNull { it.circle.id == frame.circle }
                require(old?.circle?.owner?.key == sender.key || state.value.invitations.any { it.circle == frame.circle && it.owner == sender && it.accepted })
                require(grant.circle.members.any { it.key == me.key })
                if (old != null && old.circle.epoch >= grant.circle.epoch) return
                val access = CircleAccess(grant.circle, old?.keys.orEmpty() + (grant.circle.epoch to grant.aes))
                save(state.value.copy(circles = state.value.circles.filterNot { it.circle.id == frame.circle } + access,
                    invitations = state.value.invitations.filterNot { it.circle == frame.circle }, seen = state.value.seen + ("epoch:${frame.circle}" to frame.revision)))
            }
            "LEAVE" -> {
                val access = state.value.circles.firstOrNull { it.circle.id == frame.circle && it.circle.owner.key == me.key } ?: return
                if (sender.id == me.id || access.circle.members.none { it.key == sender.key }) return
                rotate(access, access.circle.members.filterNot { it.key == sender.key })
                privateFrame(sender, "REMOVED", frame.circle, "")
            }
            "REMOVED" -> {
                val access = state.value.circles.firstOrNull { it.circle.id == frame.circle && it.circle.owner.key == sender.key } ?: return
                save(state.value.copy(circles = state.value.circles.map { if (it == access) it.copy(departed = true, removalConfirmed = true) else it }))
            }
            else -> error("Version de message inconnue")
        }
    }
    private suspend fun flush(privateOnly: Boolean = false, allowedOwners: Set<String> = emptySet()) {
        val contact = own()
        for (id in state.value.pending.toList()) {
            var signed = state.value.frames.first { it.frame.id == id }
            if (signed.frame.type == "CIRCLE") {
                if (privateOnly) continue
                val access = state.value.circles.firstOrNull { it.circle.id == signed.frame.circle }
                if (access == null || access.departed || access.circle.closed) {
                    save(state.value.copy(pending = state.value.pending - id,rejected=state.value.rejected+id))
                    continue
                }
                if (access.circle.owner.key !in allowedOwners) continue
                val currentAction = json.decodeFromString(SpaceAction.serializer(), host.decrypt(signed.frame.body, access.keys.getValue(signed.frame.epoch), circleDomain(signed.frame)))
                if (!currentAction.deleted) currentAction.media?.let { host.sendAttachment(it) }
                if (signed.frame.epoch != access.circle.epoch) {
                    val previous = signed.frame
                    val raw = host.decrypt(previous.body, access.keys.getValue(previous.epoch), circleDomain(previous))
                    val next = previous.copy(epoch = access.circle.epoch, revision = revision(), body = "")
                    persistFrame(next.copy(body = host.encrypt(raw, access.keys.getValue(next.epoch), circleDomain(next), next.revision)))
                    signed = state.value.frames.first { it.frame.id == id }
                }
            }
            val path = contact.endpoint.substringAfter("://").substringAfter('/', "").let { "/$it/social/$id" }
            val transport = maxOf(host.now(), (state.value.transport[id] ?: 0) + 1)
            save(state.value.copy(transport = state.value.transport + (id to transport)))
            val encrypted = host.encrypt(json.encodeToString(SignedSpaceFrame.serializer(), signed), contact.aes, path, transport)
            host.put(contact.endpoint + "/social/" + id, encrypted)
            save(state.value.copy(pending = state.value.pending - id))
        }
    }
    suspend fun sync(older: Boolean = false) = lock.withLock {
        own()
        val failures = mutableListOf<String>()
        try { flush(privateOnly = true) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { failures += "Envois des cercles en attente" }
        val readable=state.value.circles.filter{older || (!it.circle.closed && (!it.departed || !it.removalConfirmed))}
        val contacts = (readable.map { it.circle.owner } + readable.flatMap { it.circle.members } + state.value.invitations.map { it.owner } + state.value.invited.values.flatten() + state.value.withdrawalPeers + state.value.withdrawals.values + host.contacts() + listOf(state.value.own!!))
            .distinctBy { it.key }
        // Owners first so current membership and keys are applied before other members' posts.
        val owners = state.value.circles.map { it.circle.owner.key }.toSet()
        for (contact in contacts.sortedBy { if (it.key in owners) 0 else 1 }) {
            if (!host.validContact(contact)) continue
            try {
                val cursor = if (older) state.value.cursors[contact.key] else null
                if (older && cursor == null) continue
                val page = json.decodeFromString(SpacePage.serializer(), host.get(contact.endpoint + "/social" + (cursor?.let { "?before=$it" } ?: "")))
                require(page.items.size <= 20 && (page.next == null || page.next.matches(Regex("[0-9]{16}:[a-f0-9]{32}"))))
                // Apply records in causal order within a page: challenge precedes contributions.
                for (item in page.items.asReversed()) {
                    try {
                    val path = contact.endpoint.substringAfter("://").substringAfter('/', "").let { "/$it/social/${item.id}" }
                    val signed = json.decodeFromString(SignedSpaceFrame.serializer(), host.decrypt(item.raw, contact.aes, path))
                    val seenId = contact.key + ":" + item.id
                    if (signed.frame.type != "CIRCLE" && (state.value.seen[seenId] ?: 0) >= signed.frame.revision) continue
                    receive(contact, signed)
                    save(state.value.copy(seen = state.value.seen + (seenId to signed.frame.revision)))
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { failures += "Un message du cercle a été refusé ou attend son contexte" }
                }
                val paging=(state.value.paging[contact.key]?:PageProgress.legacy(state.value.cursors[contact.key])).advance(cursor,page.next)
                save(state.value.copy(cursors=state.value.cursors+(contact.key to paging.next),paging=state.value.paging+(contact.key to paging)))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { failures += "Une partie des cercles est indisponible" }
        }
        retryDeferred()
        val freshOwners = mutableSetOf(state.value.own!!.key)
        for (access in state.value.circles.filter { !it.circle.closed && (!it.departed || !it.removalConfirmed) }) {
            val owner = access.circle.owner
            if (owner.key == state.value.own!!.key) continue
            try {
                val id = host.hash("SuperHealth circle grant v1:${access.circle.id}:${state.value.own!!.key}").take(32)
                val url = owner.endpoint + "/social/" + id
                val path = "/" + url.substringAfter("://").substringAfter('/')
                val signed = json.decodeFromString(SignedSpaceFrame.serializer(), host.decrypt(host.get(url), owner.aes, path))
                require(signed.frame.id == id && signed.frame.type in setOf("GRANT", "REMOVED"))
                receive(owner, signed)
                freshOwners += owner.key
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { failures += "Le créateur doit être joignable avant un nouveau partage" }
        }
        try { flush(allowedOwners = freshOwners) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { failures += "Envois des cercles en attente" }
        for(removal in state.value.removals) {
            if(removal.action in state.value.pending)continue
            val referenced=state.value.records.any{!it.action.deleted && it.action.media?.id==removal.media.id}
            if(referenced)continue
            try{host.removeAttachment(removal.media);save(state.value.copy(removals=state.value.removals-removal))}
            catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){failures+="Suppression d’un média en attente"}
        }
        save(state.value.copy(notificationBaseline=true,error = failures.distinct().joinToString(" · ").ifBlank { null }))
    }
}
