package com.tkolymp.shared.auth

interface IAuthService {
    suspend fun initialize()
    suspend fun login(username: String, password: String): Boolean
    suspend fun refreshJwt(): Boolean
    suspend fun hasToken(): Boolean
    fun getToken(): String?
}
