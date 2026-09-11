package com.kylecorry.luna.subscriptions

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

class EventBusTest {

    private val cleanup = mutableListOf<() -> Unit>()

    @AfterEach
    fun cleanUp() {
        cleanup.forEach { it() }
    }

    private fun EventBus<Int>.listen(topic: String, listener: suspend (Int) -> Unit) {
        cleanup.add { unsubscribe(topic, listener) }
        subscribe(topic, listener)
    }


    @Test
    fun receivesValuesPublishedToSubscribedTopic() = runBlocking {
        val value = AtomicReference<Int?>()
        val bus = EventBus<Int>()

        bus.listen("topic") { value.set(it) }
        bus.publish("topic", 3)

        waitUntil { value.get() == 3 }
    }

    @Test
    fun deliversToAllSubscribersOfATopic() = runBlocking {
        val callCount = AtomicInteger(0)
        val bus = EventBus<Int>()

        bus.listen("topic") { callCount.incrementAndGet() }
        bus.listen("topic") { callCount.incrementAndGet() }
        bus.publish("topic", 1)

        waitUntil { callCount.get() == 2 }
    }

    @Test
    fun doesNotDeliverToOtherTopics() = runBlocking {
        val callCount = AtomicInteger(0)
        val value = AtomicReference<Int?>()
        val bus = EventBus<Int>()

        bus.listen("a") { value.set(it) }
        bus.listen("b") { callCount.incrementAndGet() }

        bus.publish("a", 1)
        waitUntil { value.get() == 1 }

        delay(50.milliseconds)
        assertEquals(0, callCount.get())
    }

    @Test
    fun publishingToATopicWithNoSubscribersDoesNothing() = runBlocking {
        val bus = EventBus<Int>()

        bus.publish("topic", 1)

        val value = AtomicReference<Int?>()
        bus.listen("topic") { value.set(it) }
        bus.publish("topic", 2)

        waitUntil { value.get() == 2 }
    }

    @Test
    fun canUnsubscribe() = runBlocking {
        val callCount = AtomicInteger(0)
        val bus = EventBus<Int>()

        val listener: suspend (Int) -> Unit = {
            callCount.incrementAndGet()
        }

        bus.listen("topic", listener)
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

        bus.listen("topic", removed)
        bus.listen("topic") { remainingCount.incrementAndGet() }
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

        val value = AtomicReference<Int?>()
        bus.listen("topic") { value.set(it) }
        bus.publish("topic", 1)

        waitUntil { value.get() == 1 }
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
