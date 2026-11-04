package app.superhealth.league

import app.superhealth.platform.*
import app.superhealth.social.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class SocialPlatformTest {
 @Test fun recoveredAndroidAndAppleKeysUseTheSameSignatureAndEcdhDomains() {
  val a=generateSigningIdentity();val b=generateSigningIdentity()
  val canonical=P256Encoding.pkcs8(a.privateKey,a.publicKey)
  val recovered=normalizeSigningIdentity(SigningIdentity(canonical,a.publicKey))
  val apple=normalizeSigningIdentity(SigningIdentity(P256Encoding.apple(canonical,a.publicKey),a.publicKey))
  val message="SuperHealth group v1:cross-platform".toByteArray()
  assertTrue(verifyIdentity(a.publicKey,message,signIdentity(recovered.privateKey,message)))
  assertTrue(verifyIdentity(a.publicKey,message,signIdentity(apple.privateKey,message)))
  assertFalse(verifyIdentity(b.publicKey,message,signIdentity(recovered.privateKey,message)))
  assertContentEquals(identitySharedSecret(recovered.privateKey,b.publicKey),identitySharedSecret(b.privateKey,a.publicKey))
 }
 @Test fun privateCardAndOldStateDefaultsRemainRecoverable() {
  val state=Json.decodeFromString(League.State.serializer(),"{}")
  assertTrue(state.blockedSocialKeys.isEmpty());assertTrue(state.ownPosts.isEmpty())
  val preferences=SocialSharing(index=false,parts=false,activities=false,progress=false,photo=false)
  assertEquals(preferences,Json.decodeFromString(SocialSharing.serializer(),Json.encodeToString(SocialSharing.serializer(),preferences)))
 }
 @Test fun notificationRoutesRejectInjectedOrUnboundedTargets() {
  val circle="a".repeat(32);val post="b".repeat(32);val author="c".repeat(16)
  assertNotNull(app.superhealth.ui.Routes.external("group/circles?circle=$circle&post=$post"))
  assertNotNull(app.superhealth.ui.Routes.external("group/post/$author/$post"))
  assertNull(app.superhealth.ui.Routes.external("group/circles?circle=$circle&delete=true"))
  assertNull(app.superhealth.ui.Routes.external("group/post/../$post"))
 }
 @Test fun malformedSnapshotsAndMixedEventKindsAreRejected() {
  val now=System.currentTimeMillis();val id="a".repeat(32)
  assertFalse(FriendTimelineRules.valid(FriendPost(id,now,kind="PR"),now))
  assertFalse(FriendTimelineRules.validEvent(FriendEvent(id,"k".repeat(91),"Test",now,post=FriendPost(id,now),deleted=true),now))
  assertFalse(SpaceRules.valid(SpaceAction("POST",id,now,media=SpaceAttachment("a".repeat(64),"key","bad/type","b".repeat(64),1,"bad")),now))
 }
}
