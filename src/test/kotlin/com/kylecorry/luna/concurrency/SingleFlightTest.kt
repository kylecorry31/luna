package com.kylecorry.luna.concurrency

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightTest {

    @Test
    fun sharesInFlightWorkForSameKey() = runBlocking {
        val singleFlight = SingleFlight<String, Int>()
        val calls = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async {
            singleFlight.invoke("key") {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                42
            }
        }

        started.await()

        val followers = (0..<10).map {
            async {
                singleFlight.invoke("key") {
                    calls.incrementAndGet()
                    -1
                }
            }
        }

        release.complete(Unit)

        assertEquals(42, first.await())
        assertEquals(List(10) { 42 }, followers.awaitAll())
        assertEquals(1, calls.get())
    }

    @Test
    fun runsWorkSeparatelyForDifferentKeys() = runBlocking {
        val singleFlight = SingleFlight<String, Int>()
        val calls = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()

        val first = async {
            singleFlight.invoke("first") {
                calls.incrementAndGet()
                release.await()
                1
            }
        }

        val second = async {
            singleFlight.invoke("second") {
                calls.incrementAndGet()
                release.await()
                2
            }
        }

        release.complete(Unit)

        assertEquals(1, first.await())
        assertEquals(2, second.await())
        assertEquals(2, calls.get())
    }

    @Test
    fun removesInFlightWorkAfterSuccess() = runBlocking {
        val singleFlight = SingleFlight<String, Int>()
        val calls = AtomicInteger(0)

        val first = singleFlight.invoke("key") {
            calls.incrementAndGet()
        }

        val second = singleFlight.invoke("key") {
            calls.incrementAndGet()
        }

        assertEquals(1, first)
        assertEquals(2, second)
        assertEquals(2, calls.get())
    }

    @Test
    fun sharesExceptionForSameKeyAndRemovesInFlightWorkAfterFailure() = runBlocking {
        supervisorScope {
            val singleFlight = SingleFlight<String, Int>()
            val calls = AtomicInteger(0)
            val expected = IllegalStateException("boom")
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()

            val first = async {
                runCatching {
                    singleFlight.invoke("key") {
                        calls.incrementAndGet()
                        started.complete(Unit)
                        release.await()
                        throw expected
                    }
                }.exceptionOrNull()
            }

            started.await()

            val followers = (0..<5).map {
                async {
                    runCatching {
                        singleFlight.invoke("key") {
                            calls.incrementAndGet()
                            -1
                        }
                    }.exceptionOrNull()
                }
            }

            release.complete(Unit)

            val failures = listOf(first.await()) + followers.awaitAll()
            failures.forEach {
                assertEquals(expected::class.java, it?.javaClass)
                assertEquals(expected.message, it?.message)
            }
            assertEquals(1, calls.get())

            val next = singleFlight.invoke("key") {
                calls.incrementAndGet()
            }

            assertEquals(2, next)
            assertEquals(2, calls.get())
        }
    }

    @Test
    fun allowsNullKeys() = runBlocking {
        val singleFlight = SingleFlight<String?, Int>()
        val calls = AtomicInteger(0)

        val result = singleFlight.invoke(null) {
            calls.incrementAndGet()
        }

        assertEquals(1, result)
        assertEquals(1, calls.get())
    }
}
