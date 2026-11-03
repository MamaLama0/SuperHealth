package app.superhealth.league

import org.junit.Assert.*
import org.junit.Test
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.serialization.json.Json

class CommentThreadsTest {
    private val now=System.currentTimeMillis()
    private val b64=Base64.getUrlEncoder().withoutPadding()
    private fun pair()=KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair()
    private fun key(pair:KeyPair)=b64.encodeToString(pair.public.encoded)
    private val owner=pair();private val stranger=pair();private val another=pair()
    private val post="a".repeat(32)
    private fun comment(pair:KeyPair,id:Char,at:Long=now,to:GroupHistory.ReplyRef?=null)=GroupHistory.Event(id.toString().repeat(32),key(pair),"Commenter",at,
        reply=GroupHistory.Reply(GroupHistory.author(key(owner)),post,"Hello",at,to))
    private fun wrap(inner:GroupHistory.Event,signer:KeyPair=stranger,wrapperOwner:KeyPair=owner):GroupHistory.Event {
        val payload=Json.encodeToString(GroupHistory.Event.serializer(),inner)
        val signature=Signature.getInstance("SHA256withECDSA").run{initSign(signer.private);update("SuperHealth group v1:".toByteArray());update(payload.toByteArray());sign()}
        val signed="SHG1.${b64.encodeToString(payload.toByteArray())}.${b64.encodeToString(signature)}"
        return GroupHistory.Event(GroupHistory.forwardId(inner.key,inner.id),key(wrapperOwner),"Owner",now,
            forward=GroupHistory.Forward(post,inner.key,signed))
    }
    @Test fun commentsOfNonFriendsAreVisibleOnlyThroughTheirPostOwner() {
        val inner=comment(stranger,'b');val wrapper=wrap(inner)
        assertTrue(GroupHistory.valid(wrapper,now))
        val target=GroupHistory.author(key(owner))
        assertTrue(GroupHistory.conversation(listOf(inner),target,post,key(owner),setOf(key(owner))).isEmpty())
        assertEquals(listOf(inner),GroupHistory.conversation(listOf(wrapper),target,post,key(owner),setOf(key(owner))))
        assertTrue(GroupHistory.conversation(listOf(wrap(inner,wrapperOwner=another)),target,post,key(owner),setOf(key(owner))).isEmpty())
    }
    @Test fun tamperedAuthorsCrossPostForwardingAndWrongSignaturesAreRejected() {
        val inner=comment(stranger,'b');val wrapper=wrap(inner)
        assertNull(GroupHistory.forwarded(wrap(inner,signer=another),now))
        assertNull(GroupHistory.forwarded(wrapper.copy(forward=wrapper.forward!!.copy(post="c".repeat(32))),now))
        assertNull(GroupHistory.forwarded(wrapper.copy(id="d".repeat(32)),now))
        assertNull(GroupHistory.forwarded(wrapper.copy(key=key(another)),now))
    }
    @Test fun signedDeletionsReplaceRelayedCommentsAndVersionsDeduplicate() {
        val inner=comment(stranger,'b');val deleted=inner.copy(reply=null,deleted=true,revision=now+1)
        val target=GroupHistory.author(key(owner))
        assertTrue(GroupHistory.conversation(listOf(wrap(inner),wrap(deleted)),target,post,key(owner),setOf(key(owner))).isEmpty())
        assertEquals(1,GroupHistory.conversation(listOf(inner,wrap(inner)),target,post,key(owner),setOf(key(owner),key(stranger))).size)
    }
    @Test fun lateRepliesAppearBelowTheirParentsAndCyclesNeverHideComments() {
        val first=comment(stranger,'b',now-300)
        val other=comment(stranger,'c',now-200)
        val child=comment(another,'d',now-100,GroupHistory.ReplyRef(GroupHistory.author(first.key),first.id))
        val rows=GroupHistory.replyRows(listOf(child,other,first))
        assertEquals(listOf(first,child,other),rows.map{it.event})
        assertEquals(listOf(0,1,0),rows.map{it.depth})
        val cycle=first.copy(reply=first.reply!!.copy(to=GroupHistory.ReplyRef(GroupHistory.author(child.key),child.id)))
        assertEquals(2,GroupHistory.replyRows(listOf(cycle,child)).size)
        assertEquals(2,GroupHistory.replyRows(listOf(other,child)).size)
    }
    @Test fun inboxDeduplicatesWithoutResettingReadStateAndBoundsHistory() {
        val item=SocialInbox.Item("one","T","Text","feed",now,read=true)
        assertEquals(listOf(item),SocialInbox.merge(listOf(item),item.copy(read=false)))
        val old=(1..200).map{SocialInbox.Item("$it","T","Text","feed",now-it)}
        val merged=SocialInbox.merge(old,item)
        assertEquals(200,merged.size)
        assertEquals("one",merged.first().id)
    }
}
