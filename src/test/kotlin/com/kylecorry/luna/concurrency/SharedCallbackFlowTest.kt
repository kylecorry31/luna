package com.kylecorry.luna.concurrency

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class SharedCallbackFlowTest {

    private val callback = TestCallback()

    @Test
    fun registersTheCallbackOnceForAllCollectors() = runBlocking {
        withTimeout(TIMEOUT) {
            val flow = callback.flow()
            val received = List(3) { Channel<Int>(Channel.UNLIMITED) }
            val collectors = received.map { values ->
                launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    flow.collect { values.send(it) }
                }
            }

            try {
                waitForRegistration()
                callback.emit(1)

                received.forEach { assertEquals(1, it.receive()) }
                assertEquals(1, callback.registrations.get())
            } finally {
                collectors.forEach { it.cancelAndJoin() }
            }
        }
    }

    @Test
    fun unregistersTheCallbackWhenTheLastCollectorLeaves() = runBlocking {
        withTimeout(TIMEOUT) {
            val flow = callback.flow()
            val first = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                flow.collect { }
            }
            val second = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                flow.collect { }
            }

            waitForRegistration()
            first.cancelAndJoin()
            delay(SETTLE_TIME)
            assertEquals(0, callback.unregistrations.get())

            second.cancelAndJoin()
            waitFor("the callback was not unregistered") { callback.unregistrations.get() == 1 }
            assertEquals(1, callback.registrations.get())
        }
    }

    @Test
    fun aSlowCollectorReceivesEveryValueRatherThanTheNewest() = runBlocking {
        withTimeout(TIMEOUT) {
            val flow = callback.flow()
            val received = Channel<Int>(Channel.UNLIMITED)
            val unblock = CompletableDeferred<Unit>()
            val collector = collect(flow, received, unblock)

            try {
                waitForRegistration()
                callback.emit(1)
                assertEquals(1, received.receive())

                (2..50).forEach(callback::emit)
                unblock.complete(Unit)

                // Delivery matches a listener subscribed directly to the source: nothing is dropped
                assertEquals((2..50).toList(), List(49) { received.receive() })
            } finally {
                collector.cancelAndJoin()
            }
        }
    }

    @Test
    fun aBurstPublishedFasterThanItIsCollectedIsNotCoalesced() = runBlocking {
        withTimeout(TIMEOUT) {
            val flow = callback.flow()
            val received = Channel<Int>(Channel.UNLIMITED)
            val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                flow.collect { received.send(it) }
            }

            try {
                waitForRegistration()
                // The emitting thread runs the whole burst before the collector is dispatched
                (1..BURST).forEach(callback::emit)

                assertEquals((1..BURST).toList(), List(BURST) { received.receive() })
            } finally {
                collector.cancelAndJoin()
            }
        }
    }

    @Test
    fun replaysValuesToNewCollectors() = runBlocking {
        withTimeout(TIMEOUT) {
            val flow = callback.flow(replay = 1)
            val first = Channel<Int>(Channel.UNLIMITED)
            val firstCollector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                flow.collect { first.send(it) }
            }

            try {
                waitForRegistration()
                callback.emit(7)
                assertEquals(7, first.receive())

                val second = Channel<Int>(Channel.UNLIMITED)
                val secondCollector =
                    launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                        flow.collect { second.send(it) }
                    }

                try {
                    assertEquals(7, second.receive())
                    assertEquals(1, callback.registrations.get())
                } finally {
                    secondCollector.cancelAndJoin()
                }
            } finally {
                firstCollector.cancelAndJoin()
            }
        }
    }

    @Test
    fun theStartedStrategyRunsInTheGivenScope() = runBlocking {
        withTimeout(TIMEOUT) {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            callback.flow(scope = scope, started = SharingStarted.Eagerly)

            // Eagerly registers the callback without waiting for a collector
            waitForRegistration()

            scope.cancel()
            waitFor("the callback was not unregistered") { callback.unregistrations.get() == 1 }
        }
    }

    private fun CoroutineScope.collect(
        flow: Flow<Int>,
        received: Channel<Int>,
        unblock: CompletableDeferred<Unit>
    ) = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
        flow.collect {
            received.send(it)
            if (it == 1) {
                unblock.await()
            }
        }
    }

    private suspend fun waitForRegistration() {
        waitFor("the callback was not registered") { callback.registrations.get() == 1 }
    }

    private suspend fun waitFor(message: String, condition: () -> Boolean) {
        withTimeoutOrNull(2000.milliseconds) {
            while (!condition()) {
                delay(1.milliseconds)
            }
        } ?: fail(message)
    }

    private class TestCallback {
        val registrations = AtomicInteger()
        val unregistrations = AtomicInteger()

        private val listeners = CopyOnWriteArrayList<(Int) -> Unit>()

        fun emit(value: Int) {
            listeners.forEach { it(value) }
        }

        fun flow(
            replay: Int = 0,
            scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            started: SharingStarted = SharingStarted.WhileSubscribed()
        ): Flow<Int> = sharedCallbackFlow(replay, scope, started) {
            val listener: (Int) -> Unit = { trySend(it) }
            listeners.add(listener)
            registrations.incrementAndGet()
            awaitClose {
                listeners.remove(listener)
                unregistrations.incrementAndGet()
            }
        }
    }

    companion object {
        private val TIMEOUT = 5000.milliseconds
        private val SETTLE_TIME = 50.milliseconds

        // Stays within the capacity the callback's channel defaults to
        private const val BURST = 50
    }
}
