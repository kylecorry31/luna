package com.kylecorry.luna.coroutines

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class CoroutineObjectPoolTest {

    @Test
    fun acquireCreatesObject() = runBlocking {
        val pool = CoroutineObjectPool(maxSize = 2) { "obj" }
        val obj = pool.acquire()
        assertEquals("obj", obj)
    }

    @Test
    fun acquireReusesReleasedObject() = runBlocking {
        val created = AtomicInteger(0)
        val pool = CoroutineObjectPool(maxSize = 2) { "obj-${created.incrementAndGet()}" }

        val obj1 = pool.acquire()
        pool.release(obj1)
        val obj2 = pool.acquire()

        assertEquals(obj1, obj2)
        assertEquals(1, created.get())
    }

    @Test
    fun acquireCreatesUpToMaxSize() = runBlocking {
        val created = AtomicInteger(0)
        val pool = CoroutineObjectPool(maxSize = 3) { "obj-${created.incrementAndGet()}" }

        val obj1 = pool.acquire()
        val obj2 = pool.acquire()
        val obj3 = pool.acquire()

        assertEquals(3, created.get())
        assertEquals("obj-1", obj1)
        assertEquals("obj-2", obj2)
        assertEquals("obj-3", obj3)
    }

    @Test
    fun acquireBlocksWhenPoolExhausted() = runBlocking {
        val pool = CoroutineObjectPool(maxSize = 1) { "obj" }

        val obj1 = pool.acquire()

        val deferred = async {
            pool.acquire()
        }

        delay(50)
        assertTrue(!deferred.isCompleted)

        pool.release(obj1)
        val obj2 = deferred.await()
        assertEquals("obj", obj2)
    }

    @Test
    fun releaseCallsCleanupWhenPoolFull() = runBlocking {
        val cleaned = ConcurrentLinkedQueue<String>()
        val pool = CoroutineObjectPool(maxSize = 1, cleanup = { cleaned.add(it) }) { "obj" }

        val obj1 = pool.acquire()
        pool.release(obj1)

        // Pool is now full (1 object), releasing another should trigger cleanup
        pool.release("extra")

        assertEquals(listOf("extra"), cleaned.toList())
    }

    @Test
    fun closeCleanUpRemainingObjects() = runBlocking {
        val cleaned = ConcurrentLinkedQueue<String>()
        val pool = CoroutineObjectPool(maxSize = 3, cleanup = { cleaned.add(it) }) { "obj" }

        val obj1 = pool.acquire()
        val obj2 = pool.acquire()
        pool.release(obj1)
        pool.release(obj2)

        pool.close()

        assertEquals(2, cleaned.size)
        assertTrue(cleaned.contains(obj1))
        assertTrue(cleaned.contains(obj2))
    }

    @Test
    fun useAcquiresAndReleases() = runBlocking {
        val created = AtomicInteger(0)
        val pool = CoroutineObjectPool(maxSize = 1) { "obj-${created.incrementAndGet()}" }

        val result = pool.use { obj ->
            assertEquals("obj-1", obj)
            "result"
        }

        assertEquals("result", result)

        // Object should have been released back, so acquiring again reuses it
        val obj = pool.acquire()
        assertEquals("obj-1", obj)
        assertEquals(1, created.get())
    }

    @Test
    fun useReleasesOnException() = runBlocking {
        val created = AtomicInteger(0)
        val pool = CoroutineObjectPool(maxSize = 1) { "obj-${created.incrementAndGet()}" }

        try {
            pool.use { throw RuntimeException("test") }
        } catch (_: RuntimeException) {
        }

        // Object should have been released back despite the exception
        val obj = pool.acquire()
        assertEquals("obj-1", obj)
        assertEquals(1, created.get())
    }

    @Test
    fun concurrentAccessRespectsMaxSize() = runBlocking {
        val created = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val current = AtomicInteger(0)
        val pool = CoroutineObjectPool(maxSize = 3) { "obj-${created.incrementAndGet()}" }

        val jobs = (1..10).map {
            async {
                pool.use {
                    val running = current.incrementAndGet()
                    while (true) {
                        val prev = maxConcurrent.get()
                        if (running > prev) {
                            if (maxConcurrent.compareAndSet(prev, running)) break
                        } else break
                    }
                    delay(30)
                    current.decrementAndGet()
                }
            }
        }

        jobs.awaitAll()

        assertTrue(created.get() <= 3, "Created more objects than maxSize: ${created.get()}")
        assertTrue(maxConcurrent.get() <= 3, "Exceeded concurrency limit: ${maxConcurrent.get()}")
    }

    @Test
    fun factoryExceptionDoesNotLeakSlot() = runBlocking {
        val calls = AtomicInteger(0)
        val pool = CoroutineObjectPool(maxSize = 1) {
            if (calls.incrementAndGet() == 1) {
                throw RuntimeException("factory failure")
            }
            "obj"
        }

        try {
            pool.acquire()
        } catch (_: RuntimeException) {
        }

        // The slot should not be consumed by the failed creation
        val obj = pool.acquire()
        assertEquals("obj", obj)
    }
}
