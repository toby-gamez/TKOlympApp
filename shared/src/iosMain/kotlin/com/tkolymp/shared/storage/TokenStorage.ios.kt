package com.tkolymp.shared.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
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
actual class TokenStorage actual constructor(platformContext: Any) {

    private val service = "com.tkolymp.tkolympapp"
    private val jwtAccount = "jwt"

    actual suspend fun saveToken(token: String) {
        val data = (token as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        keychainDelete(jwtAccount)
        val query = NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword, kSecClass)
            setObject(service, kSecAttrService)
            setObject(jwtAccount, kSecAttrAccount)
            setObject(data, kSecValueData)
        }
        SecItemAdd(query as CFDictionaryRef, null)
    }

    actual suspend fun getToken(): String? = memScoped {
        val query = NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword, kSecClass)
            setObject(service, kSecAttrService)
            setObject(jwtAccount, kSecAttrAccount)
            setObject(kCFBooleanTrue, kSecReturnData)
            setObject(kSecMatchLimitOne, kSecMatchLimit)
        }
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
        if (status != 0) return@memScoped null
        val data = result.value as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
    }

    actual suspend fun clear() {
        keychainDelete(jwtAccount)
    }

    private fun keychainDelete(account: String) {
        val query = NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword, kSecClass)
            setObject(service, kSecAttrService)
            setObject(account, kSecAttrAccount)
        }
        SecItemDelete(query as CFDictionaryRef)
    }
}
