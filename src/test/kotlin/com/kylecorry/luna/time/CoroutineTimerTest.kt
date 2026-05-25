package com.kylecorry.luna.time

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class CoroutineTimerTest {

    @Test
    fun testOnceRunsAction() = runBlocking {
        val didRun = AtomicBoolean(false)
        val timer = CoroutineTimer(scope = this, observeOn = Dispatchers.Default) {
            didRun.set(true)
        }

        timer.once(120)

        delay(30.milliseconds)
        assertFalse(didRun.get())

        delay(130.milliseconds)
        assertTrue(didRun.get())

        timer.stop()
    }

    @Test
    fun testStopPreventsPendingOnce() = runBlocking {
        val didRun = AtomicBoolean(false)
        val timer = CoroutineTimer(scope = this, observeOn = Dispatchers.Default) {
            didRun.set(true)
        }

        timer.once(100)
        delay(20.milliseconds)
        timer.stop()

        delay(120.milliseconds)
        assertFalse(didRun.get())
    }

    @Test
    fun testIntervalRunsRepeatedly() = runBlocking {
        val runs = AtomicInteger(0)
        val timer = CoroutineTimer(scope = this, observeOn = Dispatchers.Default) {
            runs.incrementAndGet()
        }

        timer.interval(20)
        delay(90.milliseconds)
        timer.stop()

        assertTrue(runs.get() >= 3)
    }

    @Test
    fun testIntervalWithSkipSkipsWhileActionIsRunning() = runBlocking {
        val runs = AtomicInteger(0)
        val timer = CoroutineTimer(
            scope = this,
            observeOn = Dispatchers.Default,
            actionBehavior = TimerActionBehavior.Skip
        ) {
            runs.incrementAndGet()
            delay(100.milliseconds)
        }

        timer.interval(20)
        delay(70.milliseconds)
        timer.stop()

        assertEquals(1, runs.get())
    }

    @Test
    fun testIntervalWithReplaceCancelsCurrentAction() = runBlocking {
        val cancellations = AtomicInteger(0)
        val timer = CoroutineTimer(
            scope = this,
            observeOn = Dispatchers.Default,
            actionBehavior = TimerActionBehavior.Replace
        ) {
            try {
                delay(100.milliseconds)
            } catch (e: CancellationException) {
                cancellations.incrementAndGet()
                throw e
            }
        }

        timer.interval(20)
        delay(80.milliseconds)
        timer.stop()

        assertTrue(cancellations.get() > 0)
    }
}
