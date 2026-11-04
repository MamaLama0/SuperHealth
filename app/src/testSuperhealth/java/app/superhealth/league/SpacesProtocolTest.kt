package app.superhealth.league

import app.superhealth.social.*
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SpacesProtocolTest {
    internal class Network {
        val values = mutableMapOf<String, String>()
        var loseResponse = false
        val json = Json { encodeDefaults = true }
        fun put(url: String, raw: String) {
            val next = json.decodeFromString(RelayCrypto.Envelope.serializer(), raw)
            values[url]?.let { previous ->
                val old = json.decodeFromString(RelayCrypto.Envelope.serializer(), previous)
                check(next.revision > old.revision || raw == previous)
            }
            require(raw.toByteArray().size <= 24576)
            values[url] = raw
            if (loseResponse) { loseResponse = false; error("response lost after commit") }
        }
        fun get(url: String): String {
            values[url]?.let { return it }
            val prefix = url.substringBefore('?') + "/"
            val before = url.substringAfter("?before=", "")
            val items = values.filterKeys { it.startsWith(prefix) }.map { (path, raw) ->
                Triple(path.substringAfterLast('/'), raw, json.decodeFromString(RelayCrypto.Envelope.serializer(), raw).revision)
            }.filter { before.isBlank() || "${it.third.toString().padStart(16, '0')}:${it.first}" < before }
                .sortedWith(compareByDescending<Triple<String, String, Long>> { it.third }.thenByDescending { it.first })
            val page = items.take(20)
            val next = page.lastOrNull()?.takeIf { items.size > 20 }?.let { "${it.third.toString().padStart(16, '0')}:${it.first}" }
            return json.encodeToString(SpacePage.serializer(), SpacePage(page.map { SpaceRemoteItem(it.first, it.second) }, next))
        }
    }
    internal class Host(val network: Network, val name: String) : SpaceHost {
        val identity = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val public = Base64.getUrlEncoder().withoutPadding().encodeToString(identity.public.encoded)
        val contact = SpaceContact(GroupHistory.author(public), public, name,
            "https://relay.example/v2/cards/" + hash(name) + "/" + hash(name + "read"), RelayCrypto.newKey())
        var clock = 1800000000000L
        var stored: String? = null
        var friends = emptyList<SpaceContact>()
        val blocks = mutableSetOf<String>()
        override fun now() = clock
        override fun randomId() = java.util.UUID.randomUUID().toString().replace("-", "")
        override fun newKey() = RelayCrypto.newKey()
        override fun validContact(contact: SpaceContact) = contact.id == GroupHistory.author(contact.key) && contact.endpoint.startsWith("https://relay.example/v2/cards/") && RelayCrypto.validKey(contact.aes)
        override fun validKey(key: String) = RelayCrypto.validKey(key)
        override fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        override fun sign(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(Signature.getInstance("SHA256withECDSA").run { initSign(identity.private); update(text.toByteArray()); sign() })
        override fun verify(key: String, text: String, signature: String) = runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getUrlDecoder().decode(key))))
                update(text.toByteArray()); verify(Base64.getUrlDecoder().decode(signature))
            }
        }.getOrDefault(false)
        override fun sharedKey(key: String): String {
            val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getUrlDecoder().decode(key)))
            val secret = KeyAgreement.getInstance("ECDH").run { init(identity.private); doPhase(peer, true); generateSecret() }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest("SuperHealth spaces ECDH v1:".toByteArray() + secret))
        }
        override fun encrypt(text: String, key: String, domain: String, revision: Long) = RelayCrypto.encrypt(text, key, domain, revision)
        override fun decrypt(text: String, key: String, domain: String) = RelayCrypto.decrypt(text, key, domain)
        override fun readStore() = stored
        override fun writeStore(text: String) { stored = text }
        override fun contacts() = friends
        override fun blocked(key: String) = key in blocks
        override suspend fun createStream() = contact
        override suspend fun get(url: String) = network.get(url)
        override suspend fun put(url: String, body: String) = network.put(url, body)
    }
    @Test fun privateWithdrawalsRequireSignedConfirmationAndSurviveReload()=runBlocking {
        val n=Network();val ah=Host(n,"Alice");val bh=Host(n,"Bob")
        ah.friends=listOf(bh.contact);bh.friends=listOf(ah.contact)
        val a=SpaceRepository(ah);val b=SpaceRepository(bh);a.initialize();b.initialize()
        a.requestFriendWithdrawals();a.sync();b.sync()
        assertEquals(ah.contact,b.state.value.withdrawals[ah.contact.id])
        val restored=SpaceRepository(bh);restored.acknowledgeWithdrawals(setOf(ah.contact.id));restored.sync();a.sync()
        assertTrue(bh.contact.id in a.state.value.withdrawalAcks)
        assertEquals(a.state.value,SpaceRepository(ah).state.value)
    }
    @Test fun legacyControlsAreBoundToTheSigningOwnerAndAlertDeltasExcludeEdits()=runBlocking {
        val n=Network();val ah=Host(n,"Alice");val bh=Host(n,"Bob");ah.friends=listOf(bh.contact);bh.friends=listOf(ah.contact)
        val a=SpaceRepository(ah);val b=SpaceRepository(bh);a.initialize();b.initialize()
        val post="f".repeat(32);a.moderateLegacy(post,"CLOSE");a.sync();b.sync()
        assertTrue(b.legacyClosed(ah.contact.id,post));assertFalse(b.legacyClosed(bh.contact.id,post))
        a.moderateLegacy(post,"CLOSE",active=false);a.sync();b.sync();assertFalse(b.legacyClosed(ah.contact.id,post))
        val action=SpaceAction("POST",ah.contact.id+"a".repeat(16),ah.now(),"Hello")
        val record=SpaceRecord("c".repeat(32),ah.contact,ah.now(),1,action)
        val before=SpaceStore(own=bh.contact,notificationBaseline=true);val after=before.copy(records=listOf(record))
        assertEquals(1,spaceAlerts(before,after,ah.now()).size)
        assertTrue(spaceAlerts(after,after.copy(records=listOf(record.copy(revision=record.revision+1,action=action.copy(text="Edited")))),ah.now()).isEmpty())
        assertTrue(spaceAlerts(before,after.copy(silentCircles=setOf(record.circle)),ah.now()).isEmpty())
    }
    @Test fun existingRepliesRemainReadableAfterClosureAndDeletedPostsCannotReappear()=runBlocking {
        val n=Network();val ah=Host(n,"Alice");val bh=Host(n,"Bob");ah.friends=listOf(bh.contact);bh.friends=listOf(ah.contact)
        val a=SpaceRepository(ah);val b=SpaceRepository(bh);a.initialize();b.initialize();val circle=a.createCircle("History");enroll(a,b,bh.contact,circle)
        val post=SpaceAction("POST",a.actionId(),ah.now(),"Before close");a.publish(circle,post);a.sync();b.sync()
        val reply=SpaceAction("REPLY",b.actionId(),bh.now(),"Existing comment",post.id);b.publish(circle,reply);b.sync();a.sync()
        ah.clock+=1000;bh.clock=ah.clock
        a.publish(circle,SpaceAction("CLOSE_REPLIES",a.actionId(),ah.now(),parent=post.id));a.sync();b.sync()
        assertTrue(b.state.value.records.any{it.action.id==reply.id})
        a.publish(circle,post.copy(deleted=true))
        assertTrue(runCatching{a.publish(circle,post)}.isFailure)
    }
    @Test fun olderReplyPagesWaitForTheirParentAndRefreshDoesNotResetPagination()=runBlocking {
        val n=Network();val ah=Host(n,"Alice");val bh=Host(n,"Bob");val ch=Host(n,"Chloe")
        ah.friends=listOf(bh.contact,ch.contact);bh.friends=listOf(ah.contact);ch.friends=listOf(ah.contact)
        val a=SpaceRepository(ah);val b=SpaceRepository(bh);val c=SpaceRepository(ch);a.initialize();b.initialize();c.initialize()
        val circle=a.createCircle("History");enroll(a,b,bh.contact,circle);enroll(a,c,ch.contact,circle);b.sync()
        val post=SpaceAction("POST",bh.contact.id+"0".repeat(16),bh.now(),"Parent on an older page");b.publish(circle,post)
        repeat(45){b.publish(circle,SpaceAction("REPLY",bh.contact.id+(it+1).toString(16).padStart(16,'0'),bh.now(),"Reply $it",post.id))};b.sync();c.sync()
        assertTrue(c.state.value.deferredRecords.isNotEmpty())
        repeat(5){if(c.state.value.cursors.values.any{it!=null}){c.sync(older=true);c.sync()}}
        assertEquals(45,c.state.value.records.count{it.action.kind=="REPLY" && it.action.parent==post.id})
        assertTrue(c.state.value.deferredRecords.isEmpty())
    }
    @Test fun challengeSourceTokensArePrivateStableAcrossRecoveryAndScopedToTheCircle()=runBlocking {
        val n=Network();val ah=Host(n,"Alice");val bh=Host(n,"Bob");val a=SpaceRepository(ah);val b=SpaceRepository(bh);a.initialize();b.initialize()
        val source="activity:42";val circle="a".repeat(32)
        val token=a.sourceToken(circle,source)
        assertEquals(token,SpaceRepository(ah).sourceToken(circle,source))
        assertNotEquals(ah.hash(source),token)
        assertNotEquals(a.sourceToken("b".repeat(32),source),token)
        assertNotEquals(b.sourceToken(circle,source),token)
    }
    private suspend fun enroll(a: SpaceRepository, b: SpaceRepository, contact: SpaceContact, circle: String) {
        a.invite(circle,contact);a.sync();b.sync()
        assertTrue(b.state.value.invitations.any { it.circle == circle })
        b.accept(circle,true);b.sync();a.sync();b.sync()
        assertTrue(b.state.value.circles.any { it.circle.id == circle })
    }
    @Test fun nonMutualFriendsCanParticipateAndRemovedMembersLoseFutureKeys() = runBlocking {
        val network=Network();val ah=Host(network,"Alice");val bh=Host(network,"Bob");val ch=Host(network,"Chloe")
        ah.friends=listOf(bh.contact,ch.contact);bh.friends=listOf(ah.contact);ch.friends=listOf(ah.contact)
        val a=SpaceRepository(ah);val b=SpaceRepository(bh);val c=SpaceRepository(ch)
        a.initialize();b.initialize();c.initialize();val circle=a.createCircle("Famille")
        enroll(a,b,bh.contact,circle)
        val oldKey=b.state.value.circles.single().keys.values.last()
        enroll(a,c,ch.contact,circle);b.sync()
        assertFalse(c.state.value.circles.single().keys.values.contains(oldKey))
        val post=SpaceAction("POST",b.actionId(),bh.now(),"Sortie ensemble")
        b.publish(circle,post);b.sync();c.sync()
        assertTrue(c.state.value.records.any { it.author.id==bh.contact.id && it.action.text==post.text })
        a.removeMember(circle,bh.contact.id);a.sync();b.sync();c.sync()
        assertTrue(b.state.value.circles.single().departed)
        val fresh=SpaceAction("POST",a.actionId(),ah.now(),"Prochains partages")
        a.publish(circle,fresh);a.sync();b.sync();c.sync()
        assertFalse(b.state.value.records.any { it.action.id==fresh.id })
        assertTrue(c.state.value.records.any { it.action.id==fresh.id })
        assertTrue(network.values.values.none { it.contains("Sortie ensemble") || it.contains("Prochains partages") })
    }
    @Test fun retriesAndDuplicateActivitiesCountOnceAndWithdrawalWorksAfterDeadline() = runBlocking {
        val network=Network();val ah=Host(network,"Alice");val bh=Host(network,"Bob")
        ah.friends=listOf(bh.contact);bh.friends=listOf(ah.contact)
        val a=SpaceRepository(ah);val b=SpaceRepository(bh);a.initialize();b.initialize()
        val circle=a.createCircle("Défi");enroll(a,b,bh.contact,circle)
        val challenge=SpaceAction("CHALLENGE",a.actionId(),ah.now(),"Six activités",target=6,start=ah.now()-1,end=ah.now()+86400000)
        a.publish(circle,challenge);a.sync();b.sync()
        val contribution=SpaceAction("CONTRIBUTION",b.actionId(),bh.now(),parent=challenge.id,source=bh.hash("activity1"))
        b.publish(circle,contribution);network.loseResponse=true;b.sync();b.sync();a.sync()
        val target=a.state.value.records.first { it.action.id==challenge.id }
        assertEquals(1,SpaceRules.total(a.state.value.records,target,ah.now()))
        b.publish(circle,contribution.copy(id=b.actionId()));b.sync();a.sync()
        assertEquals(1,SpaceRules.total(a.state.value.records,target,ah.now()))
        ah.clock+=2*86400000;bh.clock=ah.clock
        val restored=SpaceRepository(ah);restored.sync()
        assertEquals(1,SpaceRules.total(restored.state.value.records,target,ah.now()))
        b.state.value.records.filter { it.action.kind=="CONTRIBUTION" }.forEach { b.publish(circle,it.action.copy(deleted=true)) }
        b.sync();restored.sync()
        assertEquals(0,SpaceRules.total(restored.state.value.records,target,ah.now()))
    }
    @Test fun onlyPostAuthorCanModerateAndClosedRepliesAreEnforced() = runBlocking {
        val network=Network();val ah=Host(network,"Alice");val bh=Host(network,"Bob")
        ah.friends=listOf(bh.contact);bh.friends=listOf(ah.contact)
        val a=SpaceRepository(ah);val b=SpaceRepository(bh);a.initialize();b.initialize()
        val circle=a.createCircle("Ensemble");enroll(a,b,bh.contact,circle)
        val post=SpaceAction("POST",a.actionId(),ah.now(),"Bonjour")
        a.publish(circle,post);a.sync();b.sync()
        assertTrue(runCatching { b.publish(circle,SpaceAction("CLOSE_REPLIES",b.actionId(),bh.now(),parent=post.id)) }.isFailure)
        a.publish(circle,SpaceAction("CLOSE_REPLIES",a.actionId(),ah.now(),parent=post.id));a.sync();b.sync()
        assertTrue(runCatching { b.publish(circle,SpaceAction("REPLY",b.actionId(),bh.now(),"Réponse",post.id)) }.isFailure)
        assertEquals(a.state.value,SpaceRepository(ah).state.value)
    }
}
