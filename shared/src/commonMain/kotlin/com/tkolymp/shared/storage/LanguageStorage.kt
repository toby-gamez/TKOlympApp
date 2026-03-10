package com.tkolymp.shared.storage

expect class LanguageStorage(platformContext: Any) {
    suspend fun getLanguageCode(): String?
    suspend fun saveLanguageCode(code: String)
}
