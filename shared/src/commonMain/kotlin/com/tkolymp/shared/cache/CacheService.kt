package com.tkolymp.shared.cache

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
            val entry = cache[key] as? CacheEntry<T> ?: return@withLock null
            if (entry.isValid()) entry.data else {
                cache.remove(key)
                null
            }
        }
        result
    }

    suspend fun <T> put(key: String, value: T, ttl: Duration = 5.minutes) = withContext(cacheDispatcher) {
        mutex.withLock {
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
            cache.keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
        }
    }

    suspend fun clear() = withContext(cacheDispatcher) {
        mutex.withLock {
            cache.clear()
        }
    }
}
