package com.tkolymp.shared.storage

import kotlinx.cinterop.ExperimentalForeignApi
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
actual class UserStorage actual constructor(platformContext: Any) : IUserStorage {

    private val service = "com.tkolymp.tkolympapp"

    actual override suspend fun savePersonId(personId: String) = keychainSave("person_id", personId)
    actual override suspend fun getPersonId(): String? = keychainGet("person_id")
    actual override suspend fun saveCstsId(cstsId: String) = keychainSave("csts_id", cstsId)
    actual override suspend fun getCstsId(): String? = keychainGet("csts_id")

    actual override suspend fun saveCoupleIds(coupleIds: List<String>) =
        keychainSave("couple_ids", coupleIds.joinToString(","))

    actual override suspend fun getCoupleIds(): List<String> {
        val raw = keychainGet("couple_ids") ?: return emptyList()
        return if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    actual override suspend fun saveCurrentUserJson(json: String) = keychainSave("current_user_json", json)
    actual override suspend fun getCurrentUserJson(): String? = keychainGet("current_user_json")
    actual override suspend fun savePersonDetailsJson(json: String) = keychainSave("person_details_json", json)
    actual override suspend fun getPersonDetailsJson(): String? = keychainGet("person_details_json")

    actual override suspend fun clear() {
        listOf("person_id", "csts_id", "couple_ids", "current_user_json", "person_details_json")
            .forEach { keychainDelete(it) }
    }

    private fun keychainSave(account: String, value: String) {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        keychainDelete(account)
        val query = NSMutableDictionary().apply {
            cfString(kSecClass)?.let { setObject(cfAny(kSecClassGenericPassword), forKey = it) }
            cfString(kSecAttrService)?.let { setObject(service, forKey = it) }
            cfString(kSecAttrAccount)?.let { setObject(account, forKey = it) }
            cfString(kSecValueData)?.let { setObject(data, forKey = it) }
        }
        SecItemAdd(query as CFDictionaryRef, null)
    }

    private fun keychainGet(account: String): String? = memScoped {
        val query = NSMutableDictionary().apply {
            cfString(kSecClass)?.let { setObject(cfAny(kSecClassGenericPassword), forKey = it) }
            cfString(kSecAttrService)?.let { setObject(service, forKey = it) }
            cfString(kSecAttrAccount)?.let { setObject(account, forKey = it) }
            cfString(kSecReturnData)?.let { setObject(cfAny(kCFBooleanTrue), forKey = it) }
            cfString(kSecMatchLimit)?.let { setObject(cfAny(kSecMatchLimitOne), forKey = it) }
        }
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
        if (status != 0) return@memScoped null
        val data = result.value as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
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
