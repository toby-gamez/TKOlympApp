package com.tkolymp.shared.storage

expect class TokenStorage(platformContext: Any) {
    suspend fun saveToken(token: String)
    suspend fun getToken(): String?
    suspend fun clear()
}
