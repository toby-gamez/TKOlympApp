package com.tkolymp.shared.storage

interface OfflineDataStorage {
    suspend fun save(key: String, json: String)
    suspend fun load(key: String): String?
    suspend fun deleteByPrefix(prefix: String)
    suspend fun allKeys(): Set<String>
}
