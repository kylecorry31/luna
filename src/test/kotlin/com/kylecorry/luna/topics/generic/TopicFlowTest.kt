package com.kylecorry.luna.topics.generic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class TopicFlowTest {

    @Test
    fun canCollectValuesWhileSubscribed() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val subscribed = CompletableDeferred<Unit>()
            val topic = Topic<Int>(onSubscriberAdded = { _, _ -> subscribed.complete(Unit) })
            val received = Channel<Int>(Channel.UNLIMITED)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                topic.flow.collect { received.send(it) }
            }

            try {
                subscribed.await()
                for (value in 1..3) {
                    topic.publish(value)
                    assertEquals(value, received.receive())
                }
            } finally {
                collector.cancelAndJoin()
            }
        }
    }

    @Test
    fun multipleCollectorsShareOneSubscription() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val subscribed = CompletableDeferred<Unit>()
            val subscriptions = AtomicInteger()
            val topic = Topic<Int>(onSubscriberAdded = { _, _ ->
                subscriptions.incrementAndGet()
                subscribed.complete(Unit)
            })
            val received = List(3) { Channel<Int>(Channel.UNLIMITED) }
            val collectors = received.map { channel ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    topic.flow.collect { channel.send(it) }
                }
            }

            try {
                subscribed.await()
                for (value in 1..3) {
                    topic.publish(value)
                    received.forEach { assertEquals(value, it.receive()) }
                }
                assertEquals(1, subscriptions.get())
            } finally {
                collectors.forEach { it.cancel() }
                collectors.joinAll()
            }
        }
    }

    @Test
    fun throwingCollectorDoesNotDisruptOtherCollectors() = runBlocking {
        withTimeout(5_000.milliseconds) {
            // Supervision isolates coroutine failure from sibling cancellation.
            supervisorScope {
                val subscribed = CompletableDeferred<Unit>()
                val unsubscribed = CompletableDeferred<Unit>()
                val subscriptions = AtomicInteger()
                val removals = AtomicInteger()
                val topic = Topic<Int>(
                    onSubscriberAdded = { _, _ ->
                        subscriptions.incrementAndGet()
                        subscribed.complete(Unit)
                    },
                    onSubscriberRemoved = { _, _ ->
                        removals.incrementAndGet()
                        unsubscribed.complete(Unit)
                    }
                )
                val failure = IllegalStateException("Collector failed")
                val received = Channel<Int>(Channel.UNLIMITED)
                val survivingCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    topic.flow.collect { received.send(it) }
                }
                val failingCollector = async(start = CoroutineStart.UNDISPATCHED) {
                    topic.flow.collect { throw failure }
                }

                try {
                    subscribed.await()
                    topic.publish(1)
                    val thrown = runCatching { failingCollector.await() }.exceptionOrNull()
                    assertEquals(failure.javaClass, thrown?.javaClass)
                    assertEquals(failure.message, thrown?.message)
                    assertEquals(1, received.receive())
                    assertEquals(0, removals.get())

                    topic.publish(2)
                    assertEquals(2, received.receive())
                    assertEquals(1, subscriptions.get())
                    assertEquals(0, removals.get())

                    survivingCollector.cancelAndJoin()
                    unsubscribed.await()
                    assertEquals(1, removals.get())
                } finally {
                    failingCollector.cancelAndJoin()
                    survivingCollector.cancelAndJoin()
                }
            }
        }
    }


    @Test
    fun unsubscribesOnlyWhenLastCollectorLeaves() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val subscribed = CompletableDeferred<Unit>()
            val unsubscribed = CompletableDeferred<Int>()
            val removals = AtomicInteger()
            val topic = Topic<Int>(
                onSubscriberAdded = { _, _ -> subscribed.complete(Unit) },
                onSubscriberRemoved = { count, _ ->
                    removals.incrementAndGet()
                    unsubscribed.complete(count)
                }
            )
            val received = List(2) { Channel<Int>(Channel.UNLIMITED) }
            val collectors = received.map { channel ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    topic.flow.collect { channel.send(it) }
                }
            }

            try {
                subscribed.await()
                val value = 42
                topic.publish(value)
                received.forEach { assertEquals(value, it.receive()) }

                collectors.first().cancelAndJoin()
                topic.publish(value)
                assertEquals(value, received.last().receive())
                assertEquals(0, removals.get())

                collectors.last().cancelAndJoin()
                assertEquals(0, unsubscribed.await())
                assertEquals(1, removals.get())
            } finally {
                collectors.forEach { it.cancel() }
                collectors.joinAll()
            }
        }
    }

    @Test
    fun unsubscribesOnceWhenManyCollectorsCancelConcurrently() = runBlocking {
        withTimeout(15_000.milliseconds) {
            repeat(20) {
                val subscribed = CompletableDeferred<Unit>()
                val unsubscribed = CompletableDeferred<Int>()
                val subscriptions = AtomicInteger()
                val removals = AtomicInteger()
                val topic = Topic<Int>(
                    onSubscriberAdded = { _, _ ->
                        subscriptions.incrementAndGet()
                        subscribed.complete(Unit)
                    },
                    onSubscriberRemoved = { count, _ ->
                        removals.incrementAndGet()
                        unsubscribed.complete(count)
                    }
                )
                val received = List(100) { CompletableDeferred<Int>() }
                val collectors = received.map { value ->
                    launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                        topic.flow.collect { value.complete(it) }
                    }
                }

                try {
                    subscribed.await()
                    val value = 42
                    topic.publish(value)
                    received.forEach { assertEquals(value, it.await()) }
                    assertEquals(1, subscriptions.get())

                    val cancel = CompletableDeferred<Unit>()
                    val cancellations = collectors.map { collector ->
                        launch(Dispatchers.Default) {
                            cancel.await()
                            collector.cancelAndJoin()
                        }
                    }
                    cancel.complete(Unit)
                    cancellations.joinAll()

                    assertEquals(0, unsubscribed.await())
                    assertEquals(1, removals.get())
                } finally {
                    collectors.forEach { it.cancel() }
                    collectors.joinAll()
                }
            }
        }
    }

    @Test
    fun canCollectValuePublishedDuringLazySubscribe() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val unsubscribed = CompletableDeferred<Unit>()
            val starts = AtomicInteger()
            val stops = AtomicInteger()
            val value = 42
            lateinit var topic: Topic<Int>
            topic = Topic.lazy(
                start = {
                    starts.incrementAndGet()
                    topic.publish(value)
                },
                stop = {
                    stops.incrementAndGet()
                    unsubscribed.complete(Unit)
                }
            )

            assertEquals(0, starts.get())
            assertEquals(value, topic.flow.first())
            unsubscribed.await()
            assertEquals(1, starts.get())
            assertEquals(1, stops.get())
        }
    }
}
