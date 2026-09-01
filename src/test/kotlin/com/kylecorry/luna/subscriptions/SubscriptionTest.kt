package com.kylecorry.luna.subscriptions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class SubscriptionTest {

    @Test
    fun receivesEventPublishedImmediatelyAfterSubscribing() = runBlocking {
        val callCount = AtomicInteger(0)
        val subscription = Subscription()

        subscription.subscribe { callCount.incrementAndGet() }
        subscription.publish()

        waitUntil { callCount.get() == 1 }
    }

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

        // Let the subscription start
        delay(200.milliseconds)

        subscription.publish()
        waitUntil { callCount.get() == 2 }
        subscription.unsubscribeAll()

        subscription.publish()
        delay(50.milliseconds)

        assertEquals(2, callCount.get())
    }

    @Test
    fun startsOnceWhileActiveAndStopsWhenThereAreNoSubscribers() = runBlocking {
        val startCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)
        val subscription = Subscription(
            onStart = { startCount.incrementAndGet() },
            onStop = { stopCount.incrementAndGet() }
        )
        val listener1: suspend () -> Unit = {}
        val listener2: suspend () -> Unit = {}

        subscription.subscribe(listener1)
        waitUntil { startCount.get() == 1 }
        subscription.subscribe(listener2)
        delay(50.milliseconds)

        assertEquals(1, startCount.get())
        assertEquals(0, stopCount.get())

        subscription.unsubscribe(listener1)
        delay(50.milliseconds)
        assertEquals(0, stopCount.get())

        subscription.unsubscribe(listener2)
        waitUntil { stopCount.get() == 1 }
    }

    @Test
    fun stopsIfCancelledWhileStarting() = runBlocking {
        val startCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)
        val subscription = Subscription(
            onStart = {
                startCount.incrementAndGet()
                delay(1000.milliseconds)
            },
            onStop = { stopCount.incrementAndGet() }
        )

        subscription.subscribe { }
        waitUntil { startCount.get() == 1 }
        subscription.unsubscribeAll()

        waitUntil { stopCount.get() == 1 }
    }

    @Test
    fun balancesLifecycleCallsWhenSubscribersChangeConcurrently() = runBlocking {
        val startCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)
        val subscription = Subscription(
            onStart = { startCount.incrementAndGet() },
            onStop = { stopCount.incrementAndGet() }
        )

        coroutineScope {
            repeat(100) { worker ->
                launch(Dispatchers.Default) {
                    repeat(20) {
                        val listener: suspend () -> Unit = { worker.hashCode() }
                        subscription.subscribe(listener)
                        delay(1.milliseconds)
                        subscription.unsubscribe(listener)
                    }
                }
            }
        }

        waitUntil { startCount.get() == stopCount.get() }
        assertEquals(startCount.get(), stopCount.get())
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

    @Test
    fun listenerFailuresDoNotStopOtherListeners() = runBlocking {
        val callCount = AtomicInteger(0)
        val subscription = Subscription()

        subscription.subscribe { throw RuntimeException("Listener failed") }
        subscription.subscribe { callCount.incrementAndGet() }

        subscription.publish()
        waitUntil { callCount.get() == 1 }

        subscription.publish()
        waitUntil { callCount.get() == 2 }
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
