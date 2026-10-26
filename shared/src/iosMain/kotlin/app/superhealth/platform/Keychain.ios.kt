package app.superhealth.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIDevice

/* Petits secrets (code de récupération, mot de passe WebDAV) dans le trousseau, accessibles sur cet appareil seulement. */

private const val SERVICE = "app.superhealth.secrets"

private fun cfString(s: String): CFTypeRef? = CFStringCreateWithCString(null, s, kCFStringEncodingUTF8)

/** Requête de base (classe, service, compte) ; [extra] ajoute des paires. L'appelant libère le dictionnaire. */
private fun query(name: String, extra: List<Pair<CFTypeRef?, CFTypeRef?>> = emptyList()): CFMutableDictionaryRef? {
    val d = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    val service = cfString(SERVICE)
    val account = cfString(name)
    CFDictionaryAddValue(d, kSecClass, kSecClassGenericPassword)
    CFDictionaryAddValue(d, kSecAttrService, service)
    CFDictionaryAddValue(d, kSecAttrAccount, account)
    extra.forEach { (k, v) -> CFDictionaryAddValue(d, k, v) }
    CFRelease(service); CFRelease(account)
    return d
}

actual fun keychainGet(name: String): String? = memScoped {
    val q = query(name, listOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne))
    val out = alloc<CFTypeRefVar>()
    val status = SecItemCopyMatching(q, out.ptr)
    CFRelease(q)
    if (status != errSecSuccess) return null
    val data: CFDataRef = out.value?.reinterpret() ?: return null
    val len = CFDataGetLength(data).toInt()
    val bytes = CFDataGetBytePtr(data)?.reinterpret<ByteVar>()?.readBytes(len)
    CFRelease(data)
    bytes?.decodeToString()
}

actual fun keychainSet(name: String, value: String) {
    keychainDelete(name)
    val raw = value.encodeToByteArray()
    val data = raw.usePinned { CFDataCreate(null, if (raw.isEmpty()) null else it.addressOf(0).reinterpret<UByteVar>(), raw.size.convert()) }
    val q = query(name, listOf(kSecValueData to data, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly))
    SecItemAdd(q, null)
    CFRelease(q); CFRelease(data)
}

actual fun keychainDelete(name: String) {
    val q = query(name)
    SecItemDelete(q)
    CFRelease(q)
}

actual fun deviceIdentifier(): String? = UIDevice.currentDevice.identifierForVendor?.UUIDString
