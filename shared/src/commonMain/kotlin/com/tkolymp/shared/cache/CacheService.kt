package com.tkolymp.shared.cache

import com.tkolymp.shared.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class CacheEntry<T>(
    val data: T,
    val timestamp: Instant,
    val ttl: Duration
) {
    fun isValid(): Boolean = kotlin.time.Clock.System.now() < timestamp + ttl
}

class CacheService {
    private val cache = mutableMapOf<String, CacheEntry<*>>()
    private val cacheDispatcher = Dispatchers.Default.limitedParallelism(1)

    suspend fun <T> get(key: String): T? = withContext(cacheDispatcher) {
        @Suppress("UNCHECKED_CAST")
        val entry = cache[key] as? CacheEntry<T>
        if (entry == null) {
            Logger.d("CacheService", "get: MISS for key=$key")
            return@withContext null
        }
        if (entry.isValid()) {
            Logger.d("CacheService", "get: HIT for key=$key")
            entry.data
        } else {
            Logger.d("CacheService", "get: EXPIRED for key=$key, removing")
            cache.remove(key)
            null
        }
    }

    suspend fun <T> put(key: String, value: T, ttl: Duration = 5.minutes) = withContext(cacheDispatcher) {
        Logger.d("CacheService", "put: key=$key ttl=${ttl.inWholeMilliseconds}ms")
        cache[key] = CacheEntry(value, kotlin.time.Clock.System.now(), ttl)
    }

    suspend fun invalidate(key: String) = withContext(cacheDispatcher) {
        cache.remove(key)
    }

    suspend fun invalidatePrefix(prefix: String) = withContext(cacheDispatcher) {
        val toRemove = cache.keys.filter { it.startsWith(prefix) }
        toRemove.forEach { cache.remove(it) }
        Logger.d("CacheService", "invalidatePrefix: prefix=$prefix removed=${toRemove.size}")
    }

    suspend fun clear() = withContext(cacheDispatcher) {
        cache.clear()
    }
}
