package com.kylecorry.luna.concurrency.locks

import com.kylecorry.luna.concurrency.locks.NoLock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class NoLockTest {

    @Test
    fun withLockReturnsBlockResult() {
        val lock = NoLock()

        val result = lock.withLock("key") { "value" }

        assertEquals("value", result)
    }

    @Test
    fun withLockDoesNotSerializeSameKey() {
        val lock = NoLock()
        val executor = Executors.newFixedThreadPool(2)

        val firstEntered = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEnteredWhileFirstHeld = AtomicBoolean(false)

        try {
            val first = executor.submit {
                lock.withLock("same") {
                    firstEntered.countDown()
                    secondEnteredWhileFirstHeld.set(secondEntered.await(500, TimeUnit.MILLISECONDS))
                    releaseFirst.await(500, TimeUnit.MILLISECONDS)
                }
            }

            val second = executor.submit {
                firstEntered.await(500, TimeUnit.MILLISECONDS)
                lock.withLock("same") {
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
}
