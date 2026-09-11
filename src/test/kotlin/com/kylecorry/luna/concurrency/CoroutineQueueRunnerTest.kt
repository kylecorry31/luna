package com.kylecorry.luna.concurrency

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class CoroutineQueueRunnerTest {

    @Test
    fun testReplace() = runBlocking {
        val runner = CoroutineQueueRunner()

        var task1Complete = false
        var task2Complete = false

        runner.replace {
            delay(50)
            task1Complete = true
        }

        delay(10)

        runner.replace {
            delay(50)
            task2Complete = true
        }

        delay(150)

        assertEquals(false, task1Complete)
        assertEquals(true, task2Complete)
    }

    @Test
    fun testSkipIfRunning() = runBlocking {
        val runner = CoroutineQueueRunner()

        var task1Complete = false
        var task2Complete = false

        runner.skipIfRunning {
            delay(50)
            task1Complete = true
        }

        delay(10)

        runner.skipIfRunning {
            delay(50)
            task2Complete = true
        }

        delay(150)

        assertEquals(true, task1Complete)
        assertEquals(false, task2Complete)
    }

    @Test
    fun testCancel() = runBlocking {
        val runner = CoroutineQueueRunner()

        var task1Complete = false

        runner.skipIfRunning {
            delay(50)
            task1Complete = true
        }
        runner.cancelAndJoin()

        delay(150)

        assertEquals(false, task1Complete)
    }

    @Test
    fun testEnqueue() = runBlocking {
        val runner = CoroutineQueueRunner(1)

        var task1Complete = false
        var task2Complete = false
        var task3Complete = false

        runner.enqueue {
            delay(50)
            task1Complete = true
        }

        delay(10)

        runner.enqueue {
            delay(50)
            task2Complete = true
        }

        delay(10)

        runner.enqueue {
            delay(50)
            task3Complete = true
        }

        delay(200)

        assertEquals(true, task1Complete)
        assertEquals(true, task2Complete)
        assertEquals(false, task3Complete)
    }

    @Test
    fun testCancelWithQueue() = runBlocking {
        val runner = CoroutineQueueRunner(1)

        var task1Complete = false
        var task2Complete = false
        var task3Complete = false

        runner.enqueue {
            delay(50)
            task1Complete = true
        }

        delay(10)

        runner.enqueue {
            delay(50)
            task2Complete = true
        }

        delay(10)

        runner.enqueue {
            delay(50)
            task3Complete = true
        }

        runner.cancelAndJoin()

        delay(200)

        assertEquals(false, task1Complete)
        assertEquals(false, task2Complete)
        assertEquals(false, task3Complete)
    }

    @Test
    fun testRunWithExceptionNotIgnored() = runBlocking {
        val runner = CoroutineQueueRunner(2)

        var task1Complete = false
        var task2Complete = false
        var task3Complete = false

        runner.enqueue {
            delay(50)
            task1Complete = true
        }

        delay(10)

        runner.enqueue {
            delay(50)
            task2Complete = true
            throw Exception("Test")
        }

        delay(10)

        runner.enqueue {
            delay(50)
            task3Complete = true
        }

        delay(200)

        assertEquals(true, task1Complete)
        assertEquals(true, task2Complete)
        assertEquals(false, task3Complete)
    }

    @Test
    fun testRunWithExceptionsIgnored() = runBlocking {
        val runner = CoroutineQueueRunner(2, ignoreExceptions = true)

        var task1Complete = false
        var task2Complete = false
        var task3Complete = false

        runner.enqueue {
            delay(50)
            task1Complete = true
        }

        delay(10)

        runner.enqueue {
            delay(50)
            task2Complete = true
            throw Exception("Test")
        }

        delay(10)

        runner.enqueue {
            delay(50)
            task3Complete = true
        }

        delay(200)

        assertEquals(true, task1Complete)
        assertEquals(true, task2Complete)
        assertEquals(true, task3Complete)
    }

    @Test
    fun canEnqueueAfterCancel() = runBlocking {
        val runner = CoroutineQueueRunner(1)

        var task1Complete = false
        var task2Complete = false

        runner.enqueue {
            delay(50)
            task1Complete = true
        }

        delay(10)

        runner.cancelAndJoin()

        runner.enqueue {
            delay(50)
            task2Complete = true
        }

        delay(200)

        assertEquals(false, task1Complete)
        assertEquals(true, task2Complete)
    }


    @Test
    fun keepsTheQueuePolicyAfterTheConsumerRestarts() = runBlocking {
        val runner = CoroutineQueueRunner(queueSize = 1, queuePolicy = BufferOverflow.DROP_LATEST)
        runner.cancelAndJoin()

        // The consumer is restarted by the first enqueue, which must not fall back to a suspending
        // channel - a full queue drops the latest task instead of rejecting it.
        assertEquals(true, runner.enqueue { delay(100) })
        assertEquals(true, runner.enqueue { delay(100) })
        assertEquals(true, runner.enqueue { delay(100) })

        runner.cancelAndJoin()
    }

    @Test
    fun doesNotLoseTasksWhenEnqueuedConcurrentlyAfterCancel() = runBlocking {
        repeat(50) {
            val runner = CoroutineQueueRunner(queueSize = 16)
            runner.cancelAndJoin()

            val completed = AtomicInteger(0)
            // Every enqueue races to restart the consumer, and each accepted task must run on the
            // channel its consumer is reading from.
            (0 until 8).map {
                async(Dispatchers.Default) {
                    runner.enqueue { completed.incrementAndGet() }
                }
            }.awaitAll()

            delay(100)
            assertEquals(8, completed.get())
            runner.cancelAndJoin()
        }
    }
}
