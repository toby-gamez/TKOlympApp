package com.tkolymp.tkolympapp

import com.tkolymp.shared.cache.CacheService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CacheServiceTest {

    @Test
    fun `put and get returns cached value within TTL`() = runTest {
        val cache = CacheService()
        cache.put("key1", "hello", 5.minutes)
        assertEquals("hello", cache.get<String>("key1"))
    }

    @Test
    fun `get for missing key returns null`() = runTest {
        val cache = CacheService()
        assertNull(cache.get<String>("no-such-key"))
    }

    @Test
    fun `expired entry returns null`() = runTest {
        val cache = CacheService()
        cache.put("exp", "value", (-1).seconds)
        assertNull(cache.get<String>("exp"))
    }

    @Test
    fun `invalidate removes specific key`() = runTest {
        val cache = CacheService()
        cache.put("a", 1, 5.minutes)
        cache.put("b", 2, 5.minutes)
        cache.invalidate("a")
        assertNull(cache.get<Int>("a"))
        assertEquals(2, cache.get<Int>("b"))
    }

    @Test
    fun `invalidatePrefix removes all matching keys and leaves others intact`() = runTest {
        val cache = CacheService()
        cache.put("cal_week1", "w1", 5.minutes)
        cache.put("cal_week2", "w2", 5.minutes)
        cache.put("overview_data", "ov", 5.minutes)
        cache.invalidatePrefix("cal_")
        assertNull(cache.get<String>("cal_week1"))
        assertNull(cache.get<String>("cal_week2"))
        assertEquals("ov", cache.get<String>("overview_data"))
    }

    @Test
    fun `clear removes all entries`() = runTest {
        val cache = CacheService()
        cache.put("x", 1, 5.minutes)
        cache.put("y", 2, 5.minutes)
        cache.clear()
        assertNull(cache.get<Int>("x"))
        assertNull(cache.get<Int>("y"))
    }

    @Test
    fun `overwriting key updates value`() = runTest {
        val cache = CacheService()
        cache.put("key", "first", 5.minutes)
        cache.put("key", "second", 5.minutes)
        assertEquals("second", cache.get<String>("key"))
    }

    @Test
    fun `LRU eviction removes oldest entry when capacity exceeded`() = runTest {
        val cache = CacheService()
        // CacheService MAX_ENTRIES = 200; fill 201 to trigger one eviction
        repeat(200) { i -> cache.put("k$i", i, 5.minutes) }
        // Key k0 was inserted first and should be evicted when 201st entry is added
        cache.put("k_new", "newest", 5.minutes)
        assertNull(cache.get<Int>("k0"))
        assertEquals("newest", cache.get<String>("k_new"))
    }

    @Test
    fun `get updates LRU order preventing premature eviction`() = runTest {
        val cache = CacheService()
        // Insert k0..k199 (200 entries)
        repeat(200) { i -> cache.put("k$i", i, 5.minutes) }
        // Access k0 to make it most-recently-used
        cache.get<Int>("k0")
        // Add one more entry — k1 should be evicted (now oldest), not k0
        cache.put("k_extra", "extra", 5.minutes)
        assertEquals(0, cache.get<Int>("k0"))
        assertNull(cache.get<Int>("k1"))
    }
}
