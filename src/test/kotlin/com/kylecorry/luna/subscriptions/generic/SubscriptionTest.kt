package com.kylecorry.luna.subscriptions.generic

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class SubscriptionTest {

    @Test
    fun canPublish() = runBlocking {
        val startCount = AtomicInteger(0)
        var value: Int? = null
        val subscription = Subscription<Int>(onStart = { startCount.incrementAndGet() })

        val listener: suspend (Int) -> Unit = {
            value = it
        }

        subscription.subscribe(listener)
        waitUntil { startCount.get() == 1 }

        subscription.publish(3)
        waitUntil { value == 3 }
    }

    @Test
    fun canUnsubscribeAll() = runBlocking {
        val startCount = AtomicInteger(0)
        val callCount = AtomicInteger(0)
        val subscription = Subscription<Int>(
            onStart = { startCount.incrementAndGet() }
        )

        val listener1: suspend (Int) -> Unit = {
            callCount.incrementAndGet()
        }

        val listener2: suspend (Int) -> Unit = {
            callCount.incrementAndGet()
        }

        subscription.subscribe(listener1)
        subscription.subscribe(listener2)
        waitUntil { startCount.get() == 1 }

        // Let the subscription start
        delay(200.milliseconds)

        subscription.publish(1)
        waitUntil { callCount.get() == 2 }

        subscription.unsubscribeAll()

        subscription.publish(2)
        delay(50.milliseconds)

        assertEquals(2, callCount.get())
    }

    @Test
    fun canApplyModifiers() = runBlocking {
        val startCount = AtomicInteger(0)
        val callCount = AtomicInteger(0)
        var lastValue: Int? = null
        val subscription = Subscription<Int>(onStart = { startCount.incrementAndGet() })

        val listener: suspend (Int) -> Unit = {
            callCount.incrementAndGet()
            lastValue = it
        }

        subscription.subscribe(listener) { flow ->
            flow.filter { it % 2 == 0 }
        }
        waitUntil { startCount.get() == 1 }

        subscription.publish(1)
        delay(50.milliseconds)
        assertEquals(0, callCount.get())

        subscription.publish(2)
        waitUntil { callCount.get() == 1 }

        assertEquals(2, lastValue)
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
