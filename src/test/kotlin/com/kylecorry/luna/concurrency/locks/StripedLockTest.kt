package com.kylecorry.luna.concurrency.locks

import com.kylecorry.luna.concurrency.locks.StripedLock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class StripedLockTest {

    @Test
    fun constructorRequiresPositiveStripes() {
        assertThrows(IllegalArgumentException::class.java) {
            StripedLock(0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            StripedLock(-1)
        }
    }

    @Test
    fun withLockReturnsBlockResult() {
        val lock = StripedLock()

        val result = lock.withLock("key") { "value" }

        assertEquals("value", result)
    }

    @Test
    fun withLockSerializesSameKey() {
        val lock = StripedLock()
        val executor = Executors.newFixedThreadPool(2)

        val firstEntered = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        try {
            val first = executor.submit {
                lock.withLock("same") {
                    firstEntered.countDown()
                    releaseFirst.await(500, TimeUnit.MILLISECONDS)
                }
            }

            val second = executor.submit {
                firstEntered.await(500, TimeUnit.MILLISECONDS)
                lock.withLock("same") {
                    secondEntered.countDown()
                }
            }

            firstEntered.await(500, TimeUnit.MILLISECONDS)
            Thread.sleep(100)
            assertEquals(1L, secondEntered.count)

            releaseFirst.countDown()

            first.get(500, TimeUnit.MILLISECONDS)
            second.get(500, TimeUnit.MILLISECONDS)
            assertEquals(0L, secondEntered.count)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun withLockAllowsDifferentStripesToProceedConcurrently() {
        val lock = StripedLock(2)
        val executor = Executors.newFixedThreadPool(2)

        val firstEntered = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEnteredWhileFirstHeld = AtomicBoolean(false)

        try {
            val first = executor.submit {
                lock.withLock(HashKey(0)) {
                    firstEntered.countDown()
                    secondEnteredWhileFirstHeld.set(secondEntered.await(500, TimeUnit.MILLISECONDS))
                    releaseFirst.await(500, TimeUnit.MILLISECONDS)
                }
            }

            val second = executor.submit {
                firstEntered.await(500, TimeUnit.MILLISECONDS)
                lock.withLock(HashKey(1)) {
                    secondEntered.countDown()
                }
            }

            second.get(500, TimeUnit.MILLISECONDS)
            releaseFirst.countDown()
            first.get(500, TimeUnit.MILLISECONDS)

            assertTrue(secondEnteredWhileFirstHeld.get())
        } finally {
            executor.shutdownNow()
        }
    }

    private class HashKey(private val hash: Int) {
        override fun hashCode(): Int {
            return hash
        }
    }
}
