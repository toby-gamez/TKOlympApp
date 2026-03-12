package com.tkolymp.shared.cache

import com.tkolymp.shared.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class CacheEntry<T>(
    val data: T,
    val timestamp: Instant,
    val ttl: Duration
) {
    fun isValid(): Boolean = Clock.System.now() < timestamp + ttl
}

class CacheService {
    private val cache = mutableMapOf<String, CacheEntry<*>>()
    private val mutex = Mutex()
    private val cacheDispatcher = Dispatchers.Default.limitedParallelism(1)

    suspend fun <T> get(key: String): T? = withContext(cacheDispatcher) {
        @Suppress("UNCHECKED_CAST")
        val result: T? = mutex.withLock {
            val entry = cache[key] as? CacheEntry<T>
            if (entry == null) {
                Logger.d("CacheService", "get: MISS for key=$key")
                return@withLock null
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
        result
    }

    suspend fun <T> put(key: String, value: T, ttl: Duration = 5.minutes) = withContext(cacheDispatcher) {
        mutex.withLock {
            Logger.d("CacheService", "put: key=$key ttl=${ttl.inWholeMilliseconds}ms")
            cache[key] = CacheEntry(value, Clock.System.now(), ttl)
        }
    }

    suspend fun invalidate(key: String) = withContext(cacheDispatcher) {
        mutex.withLock {
            cache.remove(key)
        }
    }

    suspend fun invalidatePrefix(prefix: String) = withContext(cacheDispatcher) {
        mutex.withLock {
            val toRemove = cache.keys.filter { it.startsWith(prefix) }
            toRemove.forEach { cache.remove(it) }
            Logger.d("CacheService", "invalidatePrefix: prefix=$prefix removed=${toRemove.size}")
        }
    }

    suspend fun clear() = withContext(cacheDispatcher) {
        mutex.withLock {
            cache.clear()
        }
    }
}
