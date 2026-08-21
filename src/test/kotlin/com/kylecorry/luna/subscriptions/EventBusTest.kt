package com.kylecorry.luna.subscriptions

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class EventBusTest {

    @Test
    fun receivesValuesPublishedToSubscribedTopic() = runBlocking {
        var value: Int? = null
        val bus = EventBus<Int>()

        bus.subscribe("topic") { value = it }
        bus.publish("topic", 3)

        waitUntil { value == 3 }
    }

    @Test
    fun deliversToAllSubscribersOfATopic() = runBlocking {
        val callCount = AtomicInteger(0)
        val bus = EventBus<Int>()

        bus.subscribe("topic") { callCount.incrementAndGet() }
        bus.subscribe("topic") { callCount.incrementAndGet() }
        bus.publish("topic", 1)

        waitUntil { callCount.get() == 2 }
    }

    @Test
    fun doesNotDeliverToOtherTopics() = runBlocking {
        val callCount = AtomicInteger(0)
        var value: Int? = null
        val bus = EventBus<Int>()

        bus.subscribe("a") { value = it }
        bus.subscribe("b") { callCount.incrementAndGet() }

        bus.publish("a", 1)
        waitUntil { value == 1 }

        delay(50.milliseconds)
        assertEquals(0, callCount.get())
    }

    @Test
    fun publishingToATopicWithNoSubscribersDoesNothing() = runBlocking {
        val bus = EventBus<Int>()

        bus.publish("topic", 1)

        var value: Int? = null
        bus.subscribe("topic") { value = it }
        bus.publish("topic", 2)

        waitUntil { value == 2 }
    }

    @Test
    fun canUnsubscribe() = runBlocking {
        val callCount = AtomicInteger(0)
        val bus = EventBus<Int>()

        val listener: suspend (Int) -> Unit = {
            callCount.incrementAndGet()
        }

        bus.subscribe("topic", listener)
        bus.publish("topic", 1)
        waitUntil { callCount.get() == 1 }

        bus.unsubscribe("topic", listener)
        bus.publish("topic", 2)
        delay(50.milliseconds)

        assertEquals(1, callCount.get())
    }

    @Test
    fun unsubscribingOnlyAffectsTheGivenListener() = runBlocking {
        val removedCount = AtomicInteger(0)
        val remainingCount = AtomicInteger(0)
        val bus = EventBus<Int>()

        val removed: suspend (Int) -> Unit = {
            removedCount.incrementAndGet()
        }

        bus.subscribe("topic", removed)
        bus.subscribe("topic") { remainingCount.incrementAndGet() }
        bus.publish("topic", 1)
        waitUntil { removedCount.get() == 1 && remainingCount.get() == 1 }

        bus.unsubscribe("topic", removed)
        bus.publish("topic", 2)
        waitUntil { remainingCount.get() == 2 }

        assertEquals(1, removedCount.get())
    }

    @Test
    fun unsubscribingFromAnUnknownTopicDoesNothing() = runBlocking {
        val bus = EventBus<Int>()

        bus.unsubscribe("topic") {}

        var value: Int? = null
        bus.subscribe("topic") { value = it }
        bus.publish("topic", 1)

        waitUntil { value == 1 }
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
