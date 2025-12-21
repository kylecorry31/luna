package com.kylecorry.luna.coroutines

import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class BackgroundTaskTest {

    @Test
    fun testTaskExecutes() = runBlocking {
        val executed = AtomicInteger(0)
        val task = BackgroundTask {
            executed.incrementAndGet()
        }

        task.start()
        delay(100)

        assertEquals(1, executed.get())
    }

    @Test
    fun testTaskCanBeStartedMultipleTimes() = runBlocking {
        val executed = AtomicInteger(0)
        val task = BackgroundTask {
            executed.incrementAndGet()
        }

        task.start()
        delay(50)
        task.start()
        delay(50)

        assertEquals(2, executed.get())
    }

    @Test
    fun testTaskCanBeStopped() = runBlocking {
        val completions = mutableListOf<Long>()

        val task = BackgroundTask {
            delay(500)
            completions.add(System.currentTimeMillis())
        }

        task.start()
        delay(50)
        task.stop()
        delay(600)

        // Task should not have completed since it was stopped
        assertEquals(0, completions.size)
    }

    @Test
    fun testPreviousJobCancelledWhenStartingAgain() = runBlocking {
        val executionTimes = mutableListOf<Long>()
        val taskStartTime = System.currentTimeMillis()

        val task = BackgroundTask {
            delay(500)
            executionTimes.add(System.currentTimeMillis() - taskStartTime)
        }

        task.start()
        delay(50)
        // Starting again should cancel the previous job
        task.start()
        delay(600)

        // Only the second execution should complete
        assertEquals(1, executionTimes.size)
    }

    @Test
    fun testStopBeforeStart() {
        val executed = AtomicInteger(0)
        val task = BackgroundTask {
            executed.incrementAndGet()
        }

        // Stopping before starting should not throw
        task.stop()
        assertEquals(0, executed.get())
    }

    @Test
    fun testMultipleStopsAreSafe() = runBlocking {
        val task = BackgroundTask {
            delay(100)
        }

        task.start()
        task.stop()
        task.stop()
        task.stop()

        // No exception should be thrown
        assertTrue(true)
    }

    @Test
    fun testCustomCoroutineContext() = runBlocking {
        val executed = AtomicInteger(0)
        val task = BackgroundTask(Dispatchers.IO) {
            executed.incrementAndGet()
        }

        task.start()
        delay(100)

        assertEquals(1, executed.get())
    }

    @Test
    fun testTaskWithException() = runBlocking {
        val task = BackgroundTask {
            throw IllegalStateException("Test exception")
        }

        // Starting a task with an exception should not crash
        task.start()
        delay(100)

        // Should be able to start again
        task.start()
        delay(100)

        assertTrue(true)
    }

    @Test
    fun testConcurrentStartAndStop() = runBlocking {
        val executed = AtomicInteger(0)
        val task = BackgroundTask {
            delay(50)
            executed.incrementAndGet()
        }

        repeat(10) {
            task.start()
            task.stop()
        }

        delay(200)

        // The exact count may vary due to race conditions, but it should be safe
        assertTrue(executed.get() <= 10)
    }
}
