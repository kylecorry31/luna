package com.kylecorry.luna.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class MultiTierLRUCacheTest {

    @Test
    fun constructorRequiresAtLeastOneCache() {
        assertThrows(IllegalArgumentException::class.java) {
            MultiTierLRUCache<String, String>()
        }
    }

    @Test
    fun getOrPutReturnsTopTierCachedValueWithoutLookup() = runBlocking {
        val top = MemoryLRUCache<String, String>()
        val bottom = MemoryLRUCache<String, String>()
        val cache = MultiTierLRUCache(top, bottom)
        var lookups = 0

        top.put("key", "cached")
        val value = cache.getOrPut("key") {
            lookups++
            "lookup"
        }

        assertEquals("cached", value)
        assertEquals(0, lookups)
    }

    @Test
    fun getOrPutPromotesLowerTierCachedValueWithoutLookup() = runBlocking {
        val top = MemoryLRUCache<String, String>()
        val bottom = MemoryLRUCache<String, String>()
        val cache = MultiTierLRUCache(top, bottom)
        var lookups = 0

        bottom.put("key", "cached")
        val value = cache.getOrPut("key") {
            lookups++
            "lookup"
        }

        assertEquals("cached", value)
        assertEquals("cached", top.get("key"))
        assertEquals(0, lookups)
    }

    @Test
    fun peekReturnsLowerTierCachedValueWithoutPromoting() = runBlocking {
        val top = MemoryLRUCache<String, String>()
        val bottom = MemoryLRUCache<String, String>()
        val cache = MultiTierLRUCache(top, bottom)

        bottom.put("key", "cached")
        val value = cache.peek("key")

        assertEquals("cached", value)
        assertNull(top.peek("key"))
    }

    @Test
    fun getOrPutStoresLookupValueInAllTiersWhenMissing() = runBlocking {
        val top = MemoryLRUCache<String, String>()
        val bottom = MemoryLRUCache<String, String>()
        val cache = MultiTierLRUCache(top, bottom)
        var lookups = 0

        val value = cache.getOrPut("key") {
            lookups++
            "lookup"
        }

        assertEquals("lookup", value)
        assertEquals("lookup", top.get("key"))
        assertEquals("lookup", bottom.get("key"))
        assertEquals(1, lookups)
    }

    @Test
    fun getOrPutPromotesNullLowerTierCachedValueWithoutLookup() = runBlocking {
        val top = MemoryLRUCache<String, String?>()
        val bottom = MemoryLRUCache<String, String?>()
        val cache = MultiTierLRUCache(top, bottom)
        var lookups = 0

        bottom.getOrPut("key") { null }
        val value = cache.getOrPut("key") {
            lookups++
            "lookup"
        }
        val cachedValue = cache.getOrPut("key") {
            lookups++
            "lookup"
        }

        assertNull(value)
        assertNull(cachedValue)
        assertEquals(0, lookups)
    }

    @Test
    fun getOrPutStoresNullLookupValueInAllTiersWhenMissing() = runBlocking {
        val top = MemoryLRUCache<String, String?>()
        val bottom = MemoryLRUCache<String, String?>()
        val cache = MultiTierLRUCache(top, bottom)
        var lookups = 0

        val value = cache.getOrPut("key") {
            lookups++
            null
        }
        top.invalidate("key")
        val cachedValue = cache.getOrPut("key") {
            lookups++
            "lookup"
        }

        assertNull(value)
        assertNull(cachedValue)
        assertEquals(1, lookups)
    }

    @Test
    fun invalidateRemovesValueFromAllTiers() = runBlocking {
        val top = MemoryLRUCache<String, String>()
        val bottom = MemoryLRUCache<String, String>()
        val cache = MultiTierLRUCache(top, bottom)

        cache.put("key", "value")
        cache.invalidate("key")

        assertNull(top.get("key"))
        assertNull(bottom.get("key"))
    }

    @Test
    fun clearRemovesValuesFromAllTiers() = runBlocking {
        val top = MemoryLRUCache<String, String>()
        val bottom = MemoryLRUCache<String, String>()
        val cache = MultiTierLRUCache(top, bottom)

        top.put("top", "1")
        bottom.put("bottom", "2")
        cache.clear()

        assertNull(top.get("top"))
        assertNull(bottom.get("bottom"))
    }

    @Test
    fun concurrentGetOrPutOnlyRunsOneLookupPerKey() = runBlocking {
        val top = MemoryLRUCache<String, String>()
        val bottom = MemoryLRUCache<String, String>()
        val cache = MultiTierLRUCache(top, bottom)
        var lookups = 0

        val values = (0..<32).map {
            async(Dispatchers.Default) {
                cache.getOrPut("key") {
                    lookups++
                    delay(20.milliseconds)
                    "lookup"
                }
            }
        }.awaitAll()

        assertEquals(List(32) { "lookup" }, values)
        assertEquals("lookup", top.get("key"))
        assertEquals("lookup", bottom.get("key"))
        assertEquals(1, lookups)
    }
}
