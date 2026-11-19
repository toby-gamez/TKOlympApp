package com.tkolymp.shared.storage

interface OfflineDataStorage {
    suspend fun save(key: String, json: String)
    /** Saves many entries at once; implementations should avoid per-entry index/disk overhead. */
    suspend fun saveAll(entries: Map<String, String>) { entries.forEach { (k, v) -> save(k, v) } }
    suspend fun load(key: String): String?
    suspend fun deleteByPrefix(prefix: String)
    suspend fun allKeys(): Set<String>
}
