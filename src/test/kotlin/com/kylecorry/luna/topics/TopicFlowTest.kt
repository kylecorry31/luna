package com.kylecorry.luna.topics

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
            val topic = Topic(onSubscriberAdded = { _, _ -> subscribed.complete(Unit) })
            val received = Channel<Unit>(Channel.UNLIMITED)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                topic.flow.collect { received.send(it) }
            }

            try {
                subscribed.await()
                repeat(3) {
                    topic.publish()
                    assertEquals(Unit, received.receive())
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
            val topic = Topic(onSubscriberAdded = { _, _ ->
                subscriptions.incrementAndGet()
                subscribed.complete(Unit)
            })
            val received = List(3) { Channel<Unit>(Channel.UNLIMITED) }
            val collectors = received.map { channel ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    topic.flow.collect { channel.send(it) }
                }
            }

            try {
                subscribed.await()
                repeat(3) {
                    topic.publish()
                    received.forEach { assertEquals(Unit, it.receive()) }
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
                val topic = Topic(
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
                val received = Channel<Unit>(Channel.UNLIMITED)
                val survivingCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    topic.flow.collect { received.send(it) }
                }
                val failingCollector = async(start = CoroutineStart.UNDISPATCHED) {
                    topic.flow.collect { throw failure }
                }

                try {
                    subscribed.await()
                    topic.publish()
                    val thrown = runCatching { failingCollector.await() }.exceptionOrNull()
                    assertEquals(failure.javaClass, thrown?.javaClass)
                    assertEquals(failure.message, thrown?.message)
                    assertEquals(Unit, received.receive())
                    assertEquals(0, removals.get())

                    topic.publish()
                    assertEquals(Unit, received.receive())
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
            val topic = Topic(
                onSubscriberAdded = { _, _ -> subscribed.complete(Unit) },
                onSubscriberRemoved = { count, _ ->
                    removals.incrementAndGet()
                    unsubscribed.complete(count)
                }
            )
            val received = List(2) { Channel<Unit>(Channel.UNLIMITED) }
            val collectors = received.map { channel ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    topic.flow.collect { channel.send(it) }
                }
            }

            try {
                subscribed.await()
                topic.publish()
                received.forEach { assertEquals(Unit, it.receive()) }

                collectors.first().cancelAndJoin()
                topic.publish()
                assertEquals(Unit, received.last().receive())
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
                val topic = Topic(
                    onSubscriberAdded = { _, _ ->
                        subscriptions.incrementAndGet()
                        subscribed.complete(Unit)
                    },
                    onSubscriberRemoved = { count, _ ->
                        removals.incrementAndGet()
                        unsubscribed.complete(count)
                    }
                )
                val received = List(100) { CompletableDeferred<Unit>() }
                val collectors = received.map { value ->
                    launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                        topic.flow.collect { value.complete(it) }
                    }
                }

                try {
                    subscribed.await()
                    topic.publish()
                    received.forEach { assertEquals(Unit, it.await()) }
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
            lateinit var topic: Topic
            topic = Topic.lazy(
                start = {
                    starts.incrementAndGet()
                    topic.publish()
                },
                stop = {
                    stops.incrementAndGet()
                    unsubscribed.complete(Unit)
                }
            )

            assertEquals(0, starts.get())
            assertEquals(Unit, topic.flow.first())
            unsubscribed.await()
            assertEquals(1, starts.get())
            assertEquals(1, stops.get())
        }
    }
}
