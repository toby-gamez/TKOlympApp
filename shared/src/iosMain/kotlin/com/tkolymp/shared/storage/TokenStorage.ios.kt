package com.tkolymp.shared.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretObjCPointerOrNull
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import kotlinx.cinterop.ObjCObject
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
actual class TokenStorage actual constructor(platformContext: Any) : ITokenStorage {

    private val service = "com.tkolymp.tkolympapp"
    private val jwtAccount = "jwt"

    actual override suspend fun saveToken(token: String) {
        val data = (token as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        keychainDelete(jwtAccount)
        val query = NSMutableDictionary().apply {
            cfString(kSecClass)?.let { setObject(cfAny(kSecClassGenericPassword), forKey = it) }
            cfString(kSecAttrService)?.let { setObject(service, forKey = it) }
            cfString(kSecAttrAccount)?.let { setObject(jwtAccount, forKey = it) }
            cfString(kSecValueData)?.let { setObject(data, forKey = it) }
        }
        SecItemAdd(query as CFDictionaryRef, null)
    }

    actual override suspend fun getToken(): String? = memScoped {
        val query = NSMutableDictionary().apply {
            cfString(kSecClass)?.let { setObject(cfAny(kSecClassGenericPassword), forKey = it) }
            cfString(kSecAttrService)?.let { setObject(service, forKey = it) }
            cfString(kSecAttrAccount)?.let { setObject(jwtAccount, forKey = it) }
            cfString(kSecReturnData)?.let { setObject(cfAny(kCFBooleanTrue), forKey = it) }
            cfString(kSecMatchLimit)?.let { setObject(cfAny(kSecMatchLimitOne), forKey = it) }
        }
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
        if (status != 0) return@memScoped null
        val data = result.value as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
    }

    actual override suspend fun clear() {
        keychainDelete(jwtAccount)
    }

    private fun keychainDelete(account: String) {
        val query = NSMutableDictionary().apply {
            cfString(kSecClass)?.let { setObject(cfAny(kSecClassGenericPassword), forKey = it) }
            cfString(kSecAttrService)?.let { setObject(service, forKey = it) }
            cfString(kSecAttrAccount)?.let { setObject(account, forKey = it) }
        }
        SecItemDelete(query as CFDictionaryRef)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun cfString(ptr: platform.CoreFoundation.CFStringRef?): NSString? =
    ptr?.let { interpretObjCPointerOrNull<NSString>(it.rawValue) }

@OptIn(ExperimentalForeignApi::class)
private fun cfAny(ptr: kotlinx.cinterop.CPointer<*>?): ObjCObject? =
    ptr?.let { interpretObjCPointerOrNull<ObjCObject>(it.rawValue) }
