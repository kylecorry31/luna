package com.kylecorry.luna.subscriptions

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class SubscriptionTest {

    @Test
    fun canPublish() = runBlocking {
        val startCount = AtomicInteger(0)
        val callCount = AtomicInteger(0)
        val subscription = Subscription(onStart = { startCount.incrementAndGet() })

        val listener: suspend () -> Unit = {
            callCount.incrementAndGet()
        }

        subscription.subscribe(listener)
        waitUntil { startCount.get() == 1 }

        subscription.publish()
        waitUntil { callCount.get() == 1 }
    }

    @Test
    fun canUnsubscribeAll() = runBlocking {
        val startCount = AtomicInteger(0)
        val callCount = AtomicInteger(0)
        val subscription = Subscription(
            onStart = { startCount.incrementAndGet() }
        )

        val listener1: suspend () -> Unit = {
            callCount.incrementAndGet()
        }

        val listener2: suspend () -> Unit = {
            callCount.incrementAndGet()
        }

        subscription.subscribe(listener1)
        subscription.subscribe(listener2)
        waitUntil { startCount.get() == 1 }

        subscription.publish()
        waitUntil { callCount.get() == 2 }

        subscription.unsubscribeAll()

        subscription.publish()
        delay(50.milliseconds)

        assertEquals(2, callCount.get())
    }

    @Test
    fun canApplyModifiers() = runBlocking {
        val startCount = AtomicInteger(0)
        val callCount = AtomicInteger(0)
        val subscription = Subscription(onStart = { startCount.incrementAndGet() })

        val listener: suspend () -> Unit = {
            callCount.incrementAndGet()
        }

        subscription.subscribe(listener) { it.take(1) }
        waitUntil { startCount.get() == 1 }

        subscription.publish()
        waitUntil { callCount.get() == 1 }

        subscription.publish()
        delay(50.milliseconds)

        assertEquals(1, callCount.get())
    }

    private suspend fun waitUntil(timeoutMs: Long = 1000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Timed out waiting for condition")
            }
            delay(10.milliseconds)
        }
    }
}
