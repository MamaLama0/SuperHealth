package app.superhealth.platform

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Security.*

private fun attrs(public: Boolean): CFMutableDictionaryRef {
    val d = CFDictionaryCreateMutable(null,0,kCFTypeDictionaryKeyCallBacks.ptr,kCFTypeDictionaryValueCallBacks.ptr)!!
    CFDictionaryAddValue(d,kSecAttrKeyType,kSecAttrKeyTypeECSECPrimeRandom)
    CFDictionaryAddValue(d,kSecAttrKeyClass,if(public) kSecAttrKeyClassPublic else kSecAttrKeyClassPrivate)
    memScoped {
        val size=alloc<IntVar>();size.value=256
        val number=CFNumberCreate(null,kCFNumberIntType,size.ptr)!!
        CFDictionaryAddValue(d,kSecAttrKeySizeInBits,number);CFRelease(number)
    }
    return d
}
private fun cfData(bytes: ByteArray): CFDataRef = bytes.usePinned {
    CFDataCreate(null,if(bytes.isEmpty()) null else it.addressOf(0).reinterpret<UByteVar>(),bytes.size.convert())!!
}
private fun bytes(data: CFDataRef): ByteArray = CFDataGetBytePtr(data)!!.reinterpret<ByteVar>().readBytes(CFDataGetLength(data).toInt())
private val spki = "3059301306072a8648ce3d020106082a8648ce3d030107034200".chunked(2).map { it.toInt(16).toByte() }.toByteArray()

actual fun generateSigningIdentity(): SigningIdentity {
    val a=attrs(false)
    val key=try { SecKeyCreateRandomKey(a,null) ?: error("EC P-256 unavailable") } finally { CFRelease(a) }
    try {
        val pub=SecKeyCopyPublicKey(key) ?: error("Missing public key")
        try {
            val privateData=SecKeyCopyExternalRepresentation(key,null) ?: error("Private key export failed")
            val publicData=SecKeyCopyExternalRepresentation(pub,null) ?: error("Public key export failed")
            try {
                val point=bytes(publicData);require(point.size==65 && point[0]==4.toByte())
                return SigningIdentity(bytes(privateData),spki+point)
            } finally { CFRelease(privateData);CFRelease(publicData) }
        } finally { CFRelease(pub) }
    } finally { CFRelease(key) }
}
actual fun signIdentity(privateKey: ByteArray,message: ByteArray): ByteArray {
    val a=attrs(false);val data=cfData(privateKey)
    val key=try { SecKeyCreateWithData(data,a,null) ?: error("Invalid signing key") } finally { CFRelease(a);CFRelease(data) }
    val msg=cfData(message)
    try {
        val signature=SecKeyCreateSignature(key,kSecKeyAlgorithmECDSASignatureMessageX962SHA256,msg,null) ?: error("Signing failed")
        try { return bytes(signature) } finally { CFRelease(signature) }
    } finally { CFRelease(key);CFRelease(msg) }
}
actual fun verifyIdentity(publicKey: ByteArray,message: ByteArray,signature: ByteArray): Boolean = runCatching {
    require(publicKey.size==91 && publicKey.copyOfRange(0,spki.size).contentEquals(spki))
    val a=attrs(true);val data=cfData(publicKey.copyOfRange(spki.size,publicKey.size))
    val key=try { SecKeyCreateWithData(data,a,null) ?: return@runCatching false } finally { CFRelease(a);CFRelease(data) }
    val msg=cfData(message);val sig=cfData(signature)
    try { SecKeyVerifySignature(key,kSecKeyAlgorithmECDSASignatureMessageX962SHA256,msg,sig,null) }
    finally { CFRelease(key);CFRelease(msg);CFRelease(sig) }
}.getOrDefault(false)

actual fun identitySharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
    require(publicKey.size == 91 && publicKey.copyOfRange(0, spki.size).contentEquals(spki))
    val privateAttrs = attrs(false); val privateData = cfData(privateKey)
    val own = try { SecKeyCreateWithData(privateData, privateAttrs, null) ?: error("Invalid private key") }
        finally { CFRelease(privateAttrs); CFRelease(privateData) }
    val publicAttrs = attrs(true); val publicData = cfData(publicKey.copyOfRange(spki.size, publicKey.size))
    val peer = try { SecKeyCreateWithData(publicData, publicAttrs, null) ?: error("Invalid public key") }
        finally { CFRelease(publicAttrs); CFRelease(publicData) }
    val parameters = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
    try {
        val result = SecKeyCopyKeyExchangeResult(own, kSecKeyAlgorithmECDHKeyExchangeStandard, peer, parameters, null)
            ?: error("ECDH unavailable")
        return try { bytes(result) } finally { CFRelease(result) }
    } finally { CFRelease(parameters); CFRelease(peer); CFRelease(own) }
}

actual fun normalizeSigningIdentity(identity: SigningIdentity) = SigningIdentity(app.superhealth.social.P256Encoding.apple(identity.privateKey, identity.publicKey), identity.publicKey)
