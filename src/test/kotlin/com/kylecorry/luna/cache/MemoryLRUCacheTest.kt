package com.kylecorry.luna.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class MemoryLRUCacheTest {

    @Test
    fun getReturnsNullWhenEmpty() = runBlocking {
        val cache = MemoryLRUCache<String, String>()

        assertNull(cache.get("key"))
    }

    @Test
    fun putStoresValue() = runBlocking {
        val cache = MemoryLRUCache<String, String>()

        cache.put("key", "value")

        assertEquals("value", cache.get("key"))
    }

    @Test
    fun getReturnsNullAfterValueExpires() = runBlocking {
        val cache = MemoryLRUCache<String, String>(duration = Duration.ofMillis(10))

        cache.put("key", "value")
        delay(20.milliseconds)

        assertNull(cache.get("key"))
    }

    @Test
    fun getOrPutReturnsCachedValueWithoutLookup() = runBlocking {
        val cache = MemoryLRUCache<String, String>()
        var lookups = 0

        cache.put("key", "cached")
        val value = cache.getOrPut("key") {
            lookups++
            "lookup"
        }

        assertEquals("cached", value)
        assertEquals(0, lookups)
    }

    @Test
    fun getOrPutStoresLookupValueWhenMissing() = runBlocking {
        val cache = MemoryLRUCache<String, String>()
        var lookups = 0

        val value = cache.getOrPut("key") {
            lookups++
            "lookup"
        }

        assertEquals("lookup", value)
        assertEquals("lookup", cache.get("key"))
        assertEquals(1, lookups)
    }

    @Test
    fun getOrPutStoresNullLookupValueWhenMissing() = runBlocking {
        val cache = MemoryLRUCache<String, String?>()
        var lookups = 0

        val value = cache.getOrPut("key") {
            lookups++
            null
        }

        assertNull(value)
        assertNull(cache.get("key"))
        assertEquals(1, lookups)

        cache.getOrPut("key") {
            lookups++
            "lookup"
        }

        assertEquals(1, lookups)
    }

    @Test
    fun invalidateRemovesValue() = runBlocking {
        val cache = MemoryLRUCache<String, String>()

        cache.put("key", "value")
        cache.invalidate("key")

        assertNull(cache.get("key"))
    }

    @Test
    fun putEvictsLeastRecentlyUsedValueWhenOverSize() = runBlocking {
        val cache = MemoryLRUCache<String, String>(size = 2)

        cache.put("one", "1")
        delay(5.milliseconds)
        cache.put("two", "2")
        delay(5.milliseconds)
        cache.put("three", "3")

        assertNull(cache.get("one"))
        assertEquals("2", cache.get("two"))
        assertEquals("3", cache.get("three"))
    }

    @Test
    fun getMarksValueAsRecentlyUsed() = runBlocking {
        val cache = MemoryLRUCache<String, String>(size = 2)

        cache.put("one", "1")
        delay(5.milliseconds)
        cache.put("two", "2")
        delay(5.milliseconds)
        cache.get("one")
        delay(5.milliseconds)
        cache.put("three", "3")

        assertEquals("1", cache.get("one"))
        assertNull(cache.get("two"))
        assertEquals("3", cache.get("three"))
    }

    @Test
    fun getOrPutMarksCachedValueAsRecentlyUsed() = runBlocking {
        val cache = MemoryLRUCache<String, String>(size = 2)

        cache.put("one", "1")
        delay(5.milliseconds)
        cache.put("two", "2")
        delay(5.milliseconds)
        cache.getOrPut("one") { "lookup" }
        delay(5.milliseconds)
        cache.put("three", "3")

        assertEquals("1", cache.get("one"))
        assertNull(cache.get("two"))
        assertEquals("3", cache.get("three"))
    }

    @Test
    fun concurrentAccessDoesNotFailDuringEviction() = runBlocking {
        val cache = MemoryLRUCache<String, String>(size = 16)

        val jobs = (0..<32).map { worker ->
            launch(Dispatchers.Default) {
                repeat(250) { iteration ->
                    val key = "$worker-$iteration"
                    cache.put(key, key)
                    cache.get(key)
                }
            }
        }

        jobs.joinAll()
    }
}
