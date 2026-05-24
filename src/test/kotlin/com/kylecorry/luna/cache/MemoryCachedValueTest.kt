package com.kylecorry.luna.cache

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class MemoryCachedValueTest {

    @Test
    fun getReturnsInitialValue() = runBlocking {
        val cache = MemoryCachedValue("initial")

        assertEquals("initial", cache.get())
    }

    @Test
    fun getReturnsNullWhenEmpty() = runBlocking {
        val cache = MemoryCachedValue<String>()

        assertNull(cache.get())
    }

    @Test
    fun putStoresValue() = runBlocking {
        val cache = MemoryCachedValue<String>()

        cache.put("value")

        assertEquals("value", cache.get())
    }

    @Test
    fun getReturnsNullAfterValueExpires() = runBlocking {
        val cache = MemoryCachedValue<String>(duration = Duration.ofMillis(10))

        cache.put("value")
        delay(20.milliseconds)

        assertNull(cache.get())
    }

    @Test
    fun getOrPutReturnsCachedValueWithoutLookup() = runBlocking {
        val cache = MemoryCachedValue("cached")
        var lookups = 0

        val value = cache.getOrPut {
            lookups++
            "lookup"
        }

        assertEquals("cached", value)
        assertEquals(0, lookups)
    }

    @Test
    fun getOrPutStoresLookupValueWhenEmpty() = runBlocking {
        val cache = MemoryCachedValue<String>()
        var lookups = 0

        val value = cache.getOrPut {
            lookups++
            "lookup"
        }

        assertEquals("lookup", value)
        assertEquals("lookup", cache.get())
        assertEquals(1, lookups)
    }

    @Test
    fun getOrPutCleansUpExpiredValue() = runBlocking {
        val cleaned = mutableListOf<String>()
        val cache = MemoryCachedValue(
            duration = Duration.ofMillis(10),
            cleanup = cleaned::add
        )

        cache.put("old")
        delay(20.milliseconds)

        val value = cache.getOrPut { "new" }

        assertEquals("new", value)
        assertEquals("new", cache.get())
        assertEquals(listOf("old"), cleaned)
    }

    @Test
    fun putCleansUpPreviousValue() = runBlocking {
        val cleaned = mutableListOf<String>()
        val cache = MemoryCachedValue(cleanup = cleaned::add)

        cache.put("old")
        cache.put("new")

        assertEquals("new", cache.get())
        assertEquals(listOf("old"), cleaned)
    }

    @Test
    fun resetClearsAndCleansUpValue() = runBlocking {
        val cleaned = mutableListOf<String>()
        val cache = MemoryCachedValue("value", cleanup = cleaned::add)

        cache.reset()

        assertNull(cache.get())
        assertEquals(listOf("value"), cleaned)
    }

    @Test
    fun resetEmptyCacheDoesNotCleanUpValue() = runBlocking {
        val cleaned = mutableListOf<String>()
        val cache = MemoryCachedValue<String>(cleanup = cleaned::add)

        cache.reset()

        assertEquals(emptyList<String>(), cleaned)
    }
}
