package com.tkolymp.shared.storage

interface ITokenStorage {
    suspend fun saveToken(token: String)
    suspend fun getToken(): String?
    suspend fun clear()
}
