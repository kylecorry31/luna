package com.kylecorry.luna.subscriptions

import com.kylecorry.luna.concurrency.IFlowable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class SubscriptionFlowTest {

    @Test
    fun receivesEventPublishedImmediatelyAfterCollectionStarts() = runBlocking {
        val subscription = Subscription()
        val values = mutableListOf<Unit>()
        val collector = collect(subscription.flow) { values.add(it) }
        subscription.publish()
        yield()
        assertEquals(listOf(Unit), values)
        collector.cancelAndJoin()
    }

    @Test
    fun broadcastsAndCancellingOneCollectorLeavesTheOtherActive() = runBlocking {
        val subscription = Subscription()
        var firstCount = 0
        var secondCount = 0
        val first = collect(subscription.flow) { firstCount++ }
        val second = collect(subscription.flow) { secondCount++ }
        subscription.publish()
        yield()
        assertEquals(1, firstCount)
        assertEquals(1, secondCount)
        first.cancelAndJoin()
        subscription.publish()
        yield()
        assertEquals(1, firstCount)
        assertEquals(2, secondCount)
        second.cancelAndJoin()
    }

    @Test
    fun startsOnceStopsAfterLastCollectorAndRestarts() = runBlocking {
        val calls = mutableListOf<String>()
        val subscription = Subscription(onStart = { calls.add("start") }, onStop = { calls.add("stop") })
        assertEquals(emptyList<String>(), calls)
        val first = collect(subscription.flow) {}
        val second = collect(subscription.flow) {}
        assertEquals(listOf("start"), calls)
        first.cancelAndJoin()
        assertEquals(listOf("start"), calls)
        second.cancelAndJoin()
        assertEquals(listOf("start", "stop"), calls)
        collect(subscription.flow) {}.cancelAndJoin()
        assertEquals(listOf("start", "stop", "start", "stop"), calls)
    }

    @Test
    fun finishesStartingBeforeStoppingWhenCancelledDuringStart() = runBlocking {
        withTimeout(5000.milliseconds) {
            val releaseStart = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            val subscription = Subscription(
                onStart = { calls.add("starting"); releaseStart.await(); calls.add("started") },
                onStop = { calls.add("stopped") }
            )
            val collector = collect(subscription.flow) {}
            collector.cancel()
            assertEquals(listOf("starting"), calls)
            releaseStart.complete(Unit)
            collector.join()
            assertEquals(listOf("starting", "started", "stopped"), calls)
        }
    }

    @Test
    fun receivesEventPublishedWhileStarting() = runBlocking {
        withTimeout(5000.milliseconds) {
            val releaseStart = CompletableDeferred<Unit>()
            val received = CompletableDeferred<Unit>()
            val subscription = Subscription(onStart = { releaseStart.await() })
            val collector = collect(subscription.flow) { received.complete(it) }
            subscription.publish()
            releaseStart.complete(Unit)
            assertEquals(Unit, received.await())
            collector.cancelAndJoin()
        }
    }

    @Test
    fun flowOperatorsCanFilterAndCompleteCollection() = runBlocking {
        var stops = 0
        val subscription = Subscription(onStop = { stops++ })
        val values = mutableListOf<Unit>()
        var accept = false
        val collector = collect(subscription.flow.filter { accept }.take(1)) { values.add(it) }
        subscription.publish()
        yield()
        assertTrue(values.isEmpty())
        accept = true
        subscription.publish()
        withTimeout(5000.milliseconds) { collector.join() }
        assertEquals(listOf(Unit), values)
        assertEquals(1, stops)
        subscription.publish()
        yield()
        assertEquals(1, values.size)
    }

    @Test
    fun cancellingOwnerStopsAllCollections() = runBlocking {
        var stops = 0
        val subscription = Subscription(onStop = { stops++ })
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner)
        val first = subscription.flow.onEach {}.launchIn(scope)
        val second = subscription.flow.onEach {}.launchIn(scope)
        yield()
        owner.cancelAndJoin()
        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)
        assertEquals(1, stops)
    }

    @Test
    fun collectorFailureIsReportedAndDoesNotStopSupervisedSibling() = runBlocking {
        withTimeout(5000.milliseconds) {
            val failure = IllegalStateException("Listener failed")
            val reported = CompletableDeferred<Throwable>()
            val handler = CoroutineExceptionHandler { _, error -> reported.complete(error) }
            var stops = 0
            val subscription = Subscription(onStop = { stops++ })
            supervisorScope {
                val failed = launch(handler, start = CoroutineStart.UNDISPATCHED) {
                    subscription.flow.collect { throw failure }
                }
                var count = 0
                val healthy = collect(subscription.flow) { count++ }
                subscription.publish()
                assertEquals(failure, reported.await())
                failed.join()
                yield()
                assertEquals(1, count)
                assertEquals(0, stops)
                subscription.publish()
                yield()
                assertEquals(2, count)
                healthy.cancelAndJoin()
                assertEquals(1, stops)
            }
        }
    }

    @Test
    fun replayRetainsEventsPublishedBeforeCollection() = runBlocking {
        val subscription = Subscription(replay = 2)
        subscription.publish()
        subscription.publish()
        val values = mutableListOf<Unit>()
        val collector = collect(subscription.flow.take(2)) { values.add(it) }
        withTimeout(5000.milliseconds) { collector.join() }
        assertEquals(listOf(Unit, Unit), values)
    }

    @Test
    fun defaultDoesNotReplayPastEvents() = runBlocking {
        val subscription = Subscription()
        subscription.publish()
        var count = 0
        val collector = collect(subscription.flow) { count++ }
        yield()
        assertEquals(0, count)
        subscription.publish()
        yield()
        assertEquals(1, count)
        collector.cancelAndJoin()
    }

    @Test
    fun balancesLifecycleWhenCollectorsChangeConcurrently() = runBlocking {
        withTimeout(10000.milliseconds) {
            val starts = AtomicInteger()
            val stops = AtomicInteger()
            val subscription =
                Subscription(onStart = { starts.incrementAndGet() }, onStop = { stops.incrementAndGet() })
            coroutineScope {
                repeat(20) {
                    launch(Dispatchers.Default) {
                        repeat(50) {
                            val collector = collect(subscription.flow) {}
                            yield()
                            collector.cancelAndJoin()
                        }
                    }
                }
            }
            assertTrue(starts.get() > 0)
            assertEquals(starts.get(), stops.get())
        }
    }

    private fun CoroutineScope.collect(flow: Flow<Unit>, listener: suspend (Unit) -> Unit): Job =
        launch(start = CoroutineStart.UNDISPATCHED) { flow.collect(listener) }
}
