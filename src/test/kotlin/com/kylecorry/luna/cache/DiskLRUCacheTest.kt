package com.kylecorry.luna.cache

import com.kylecorry.luna.serialization.ISerializer
import com.kylecorry.luna.serialization.TextSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.time.Duration.Companion.milliseconds

class DiskLRUCacheTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun getReturnsNullWhenEmpty() = runBlocking {
        val cache = createCache()

        assertNull(cache.get("key"))
    }

    @Test
    fun putStoresValue() = runBlocking {
        val cache = createCache()

        cache.put("key", "value")

        assertEquals("value", cache.get("key"))
    }

    @Test
    fun getReturnsNullAfterValueExpires() = runBlocking {
        val cache = createCache(duration = Duration.ofMillis(10))

        cache.put("key", "value")
        delay(20.milliseconds)

        assertNull(cache.get("key"))
    }

    @Test
    fun getOrPutReturnsCachedValueWithoutLookup() = runBlocking {
        val cache = createCache()
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
    fun getOrPutReturnsCachedNullValueWithoutLookup() = runBlocking {
        val cache = createNullableCache()
        var lookups = 0

        cache.put("key", null)
        val value = cache.getOrPut("key") {
            lookups++
            "lookup"
        }

        assertNull(value)
        assertEquals(0, lookups)
    }

    @Test
    fun getOrPutStoresLookupValueWhenMissing() = runBlocking {
        val cache = createCache()
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
    fun concurrentGetOrPutOnlyRunsOneLookupPerKey() = runBlocking {
        val cache = createCache()
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
        assertEquals("lookup", cache.get("key"))
        assertEquals(1, lookups)
    }

    @Test
    fun getOrPutStoresNullLookupValueWhenMissing() = runBlocking {
        val cache = createNullableCache()
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
        val cache = createCache()

        cache.put("key", "value")
        cache.invalidate("key")

        assertNull(cache.get("key"))
    }

    @Test
    fun putEvictsLeastRecentlyUsedValueWhenOverSize() = runBlocking {
        val cache = createCache(size = 2)

        cache.put("one", "1")
        delay(20.milliseconds)
        cache.put("two", "2")
        delay(20.milliseconds)
        cache.put("three", "3")

        assertNull(cache.get("one"))
        assertEquals("2", cache.get("two"))
        assertEquals("3", cache.get("three"))
    }

    @Test
    fun getMarksValueAsRecentlyUsed() = runBlocking {
        val cache = createCache(size = 2)

        cache.put("one", "1")
        delay(20.milliseconds)
        cache.put("two", "2")
        delay(20.milliseconds)
        cache.get("one")
        delay(20.milliseconds)
        cache.put("three", "3")

        assertEquals("1", cache.get("one"))
        assertNull(cache.get("two"))
        assertEquals("3", cache.get("three"))
    }

    @Test
    fun peekDoesNotMarkValueAsRecentlyUsed() = runBlocking {
        val cache = createCache(size = 2)

        cache.put("one", "1")
        delay(20.milliseconds)
        cache.put("two", "2")
        delay(20.milliseconds)
        assertEquals("1", cache.peek("one"))
        delay(20.milliseconds)
        cache.put("three", "3")

        assertNull(cache.get("one"))
        assertEquals("2", cache.get("two"))
        assertEquals("3", cache.get("three"))
    }

    @Test
    fun getOrPutMarksCachedValueAsRecentlyUsed() = runBlocking {
        val cache = createCache(size = 2)

        cache.put("one", "1")
        delay(20.milliseconds)
        cache.put("two", "2")
        delay(20.milliseconds)
        cache.getOrPut("one") { "lookup" }
        delay(20.milliseconds)
        cache.put("three", "3")

        assertEquals("1", cache.get("one"))
        assertNull(cache.get("two"))
        assertEquals("3", cache.get("three"))
    }

    @Test
    fun putSerializesValue() = runBlocking {
        val cache = createEncodedCache()

        cache.put("key", EncodedValue(3, "value"))

        assertEquals("3:value", tempDir.resolve("key").readBytes().decodeToString())
    }

    @Test
    fun putWritesValueInBackgroundWhenAsync() = runBlocking {
        val serializeStarted = CountDownLatch(1)
        val allowSerialize = CountDownLatch(1)
        val cache = createBlockingSerializeCache(serializeStarted, allowSerialize)

        cache.put("key", "value")

        assertTrue(serializeStarted.await(1, TimeUnit.SECONDS))
        assertNull(cache.get("key"))

        allowSerialize.countDown()

        assertEquals("value", awaitCachedValue(cache, "key"))
    }

    @Test
    fun getOrPutStoresLookupValueInBackgroundWhenAsync() = runBlocking {
        val serializeStarted = CountDownLatch(1)
        val allowSerialize = CountDownLatch(1)
        val cache = createBlockingSerializeCache(serializeStarted, allowSerialize)

        val value = cache.getOrPut("key") {
            "lookup"
        }

        assertEquals("lookup", value)
        assertTrue(serializeStarted.await(1, TimeUnit.SECONDS))
        assertNull(cache.get("key"))

        allowSerialize.countDown()

        assertEquals("lookup", awaitCachedValue(cache, "key"))
    }

    @Test
    fun getDeserializesValue() = runBlocking {
        val cache = createEncodedCache()
        tempDir.resolve("key").writeBytes("4:value".encodeToByteArray())

        assertEquals(EncodedValue(4, "value"), cache.get("key"))
    }

    @Test
    fun concurrentAccessDoesNotFailDuringEviction() = runBlocking {
        val cache = createCache(size = 16)

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

    private fun createCache(
        size: Int? = null,
        duration: Duration? = null,
        async: Boolean = false
    ): DiskLRUCache<String, String> {
        return DiskLRUCache(
            baseFolderPath = tempDir.toString(),
            size = size,
            duration = duration,
            async = async,
            getFilename = { it },
            serializer = TextSerializer()
        )
    }

    private fun createNullableCache(): DiskLRUCache<String, String?> {
        return DiskLRUCache(
            baseFolderPath = tempDir.toString(),
            getFilename = { it },
            serializer = object : ISerializer<String?> {
                private val base = TextSerializer()

                override fun serialize(obj: String?, stream: OutputStream) {
                    base.serialize(obj ?: "", stream)
                }

                override fun deserialize(stream: InputStream): String? {
                    return base.deserialize(stream).ifEmpty { null }
                }
            }
        )
    }

    private fun createEncodedCache(): DiskLRUCache<String, EncodedValue> {
        return DiskLRUCache(
            baseFolderPath = tempDir.toString(),
            getFilename = { it },
            serializer = object : ISerializer<EncodedValue> {
                private val base = TextSerializer()

                override fun serialize(obj: EncodedValue, stream: OutputStream) {
                    base.serialize("${obj.id}:${obj.value}", stream)
                }

                override fun deserialize(stream: InputStream): EncodedValue {
                    val text = base.deserialize(stream)
                    val parts = text.split(":", limit = 2)
                    return EncodedValue(parts[0].toInt(), parts[1])
                }
            }
        )
    }

    private fun createBlockingSerializeCache(
        serializeStarted: CountDownLatch,
        allowSerialize: CountDownLatch
    ): DiskLRUCache<String, String> {
        return DiskLRUCache(
            baseFolderPath = tempDir.toString(),
            async = true,
            getFilename = { it },
            serializer = object : ISerializer<String> {
                private val base = TextSerializer()

                override fun serialize(obj: String, stream: OutputStream) {
                    serializeStarted.countDown()
                    allowSerialize.await(5, TimeUnit.SECONDS)
                    base.serialize(obj, stream)
                }

                override fun deserialize(stream: InputStream): String {
                    return base.deserialize(stream)
                }
            }
        )
    }

    private suspend fun awaitCachedValue(
        cache: DiskLRUCache<String, String>,
        key: String
    ): String? {
        repeat(50) {
            cache.get(key)?.let { return it }
            delay(20.milliseconds)
        }
        return cache.get(key)
    }

    private data class EncodedValue(val id: Int, val value: String)
}
