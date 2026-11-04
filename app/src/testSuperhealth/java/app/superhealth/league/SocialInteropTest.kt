package app.superhealth.league

import app.superhealth.social.*
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.KeyAgreement
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SocialInteropTest {
    @Test fun appleAndPkcs8RepresentationsPreserveSignaturesAndEcdhSecret() {
        val generator=KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}
        val a=generator.generateKeyPair();val b=generator.generateKeyPair()
        val apple=P256Encoding.apple(a.private.encoded,a.public.encoded)
        assertEquals(97,apple.size)
        val restored=KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(P256Encoding.pkcs8(apple,a.public.encoded)))
        val message="SuperHealth interop".toByteArray()
        val signature=Signature.getInstance("SHA256withECDSA").run{initSign(restored);update(message);sign()}
        assertTrue(Signature.getInstance("SHA256withECDSA").run{initVerify(a.public);update(message);verify(signature)})
        fun secret(key:PrivateKey,peer:PublicKey)=KeyAgreement.getInstance("ECDH").run{init(key);doPhase(peer,true);generateSecret()}
        assertArrayEquals(secret(a.private,b.public),secret(restored,b.public))
        assertArrayEquals(secret(b.private,a.public),secret(restored,b.public))
    }
    private class Timeline(val crypto:SpacesProtocolTest.Host):TimelineHost {
        var stored:String?=null
        override fun randomId()=crypto.randomId()
        override fun now()=crypto.now()
        override fun own()=TimelinePeer(crypto.contact.id,crypto.public,crypto.name,crypto.contact.endpoint,crypto.contact.aes)
        override fun peers()=crypto.friends.map{TimelinePeer(it.id,it.key,it.name,it.endpoint,it.aes)}
        override fun blocked(key:String)=false
        override fun hash(text:String)=crypto.hash(text)
        override fun identityId(key:String)=GroupHistory.author(key)
        override fun encode(text:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
        override fun decode(text:String)=Base64.getUrlDecoder().decode(text).decodeToString()
        override fun sign(text:String)=crypto.sign(text)
        override fun verify(key:String,text:String,signature:String)=crypto.verify(key,text,signature)
        override fun validEndorsement(root:String?,key:String,signature:String?)=GroupIdentity.validEndorsement(root,key,signature)
        override fun encrypt(text:String,aes:String,path:String,revision:Long)=crypto.encrypt(text,aes,path,revision)
        override fun decrypt(text:String,aes:String,path:String)=crypto.decrypt(text,aes,path)
        override fun readTimeline()=stored
        override fun writeTimeline(text:String){stored=text}
        override suspend fun prepareMailbox()=Unit
        override suspend fun get(url:String)=crypto.get(url)
        override suspend fun put(url:String,body:String)=crypto.put(url,body)
        override suspend fun upload(media:FriendMedia)=Unit
        override suspend fun removeMedia(media:FriendMedia)=Unit
    }
    @Test fun portableTimelineProducesStrictAndroidEventsAndReloadsWithoutDuplicates()=runBlocking {
        val network=SpacesProtocolTest.Network();val crypto=SpacesProtocolTest.Host(network,"Alice")
        val host=Timeline(crypto);val timeline=FriendTimeline(host)
        val post=FriendPost("a".repeat(32),crypto.now(),"Cross-platform post")
        timeline.publish(post);timeline.publishPending()
        val path=crypto.contact.endpoint+"/social/"+post.id
        val raw=crypto.decrypt(network.values.getValue(path),crypto.contact.aes,"/"+path.substringAfter("://").substringAfter('/'))
        val verified=League.verifySocial(raw,crypto.public)
        assertNotNull(verified)
        val android=Json { ignoreUnknownKeys=false }.decodeFromString(GroupHistory.Event.serializer(),verified!!)
        assertTrue(GroupHistory.valid(android,crypto.now()))
        assertEquals(post.text,android.post!!.text)
        val restored=FriendTimeline(host);restored.sync();restored.sync()
        assertEquals(1,restored.posts().size)
        restored.edit(post,"Changed");restored.publishPending();assertEquals(1,restored.posts().size)
        restored.delete(post);restored.publishPending();assertTrue(FriendTimeline(host).posts().isEmpty())
    }
    @Test fun portableOwnerForwardsRepliesAndTheirTombstonesToNonMutualFriends()=runBlocking {
        val network=SpacesProtocolTest.Network();val a=SpacesProtocolTest.Host(network,"Alice");val b=SpacesProtocolTest.Host(network,"Bob");val c=SpacesProtocolTest.Host(network,"Chloe")
        a.friends=listOf(b.contact,c.contact);b.friends=listOf(a.contact);c.friends=listOf(a.contact)
        val owner=FriendTimeline(Timeline(a));val commenter=FriendTimeline(Timeline(b));val recipient=FriendTimeline(Timeline(c))
        val post=FriendPost("a".repeat(32),a.now(),"A shared post");owner.publish(post);owner.sync();commenter.sync()
        commenter.reply(a.contact.id,post.id,"A non-mutual reply");commenter.sync();owner.sync();recipient.sync()
        val reply=recipient.replies(a.contact.id,post.id).single();assertEquals(b.public,reply.key)
        assertEquals(1,recipient.resolvedEvents().count{it.reply?.text=="A non-mutual reply"})
        commenter.deleteReply(reply);commenter.sync();owner.sync();recipient.sync()
        assertTrue(recipient.replies(a.contact.id,post.id).isEmpty())
        assertTrue(recipient.resolvedEvents().any{it.id==reply.id && it.key==b.public && it.deleted})
    }
    @Test fun newHeadPagesDoNotSkipGapsOrRestartOlderPages()=runBlocking {
        val network=SpacesProtocolTest.Network();val a=SpacesProtocolTest.Host(network,"Alice");val c=SpacesProtocolTest.Host(network,"Chloe")
        a.friends=listOf(c.contact);c.friends=listOf(a.contact)
        val owner=FriendTimeline(Timeline(a));val reader=FriendTimeline(Timeline(c))
        suspend fun posts(start:Int,end:Int){for(i in start until end)owner.publish(FriendPost(i.toString(16).padStart(32,'0'),a.now(),"Post $i"));owner.sync()}
        posts(0,60);reader.sync();assertEquals(20,reader.posts().size)
        a.clock+=1000;c.clock=a.clock;posts(60,85);reader.sync()
        repeat(12){if(reader.state.value.cursors.values.any{it!=null}){reader.sync(older=true);reader.sync()}}
        assertEquals(85,reader.posts().size)
        assertTrue(reader.state.value.cursors.values.all{it==null})
    }
    @Test fun groupedInboxKeepsReadStateAndDeduplicatesPreviouslyGroupedEvents() {
        val first=SocialInbox.Item("a","title","text","feed",1,group="thread")
        val second=first.copy(id="b",time=2)
        val grouped=SocialInbox.merge(listOf(first.copy(read=true)),second)
        assertEquals(2,grouped.single().count);assertFalse(grouped.single().read)
        assertEquals(grouped,SocialInbox.merge(grouped,first))
        assertEquals(grouped,SocialInbox.merge(grouped,second))
    }
}
