package com.kylecorry.luna.coroutines

import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(DelicateCoroutinesApi::class)
class FlowExtensionsTest {

    private val actionWaitTime = 50L

    @Test
    fun testRead() = runBlocking {
        val wrapper = MockListenerFlowWrapper(false)
        var value: Int? = null

        val job = GlobalScope.launch {
            value = wrapper.flow.read()
        }

        wait()

        assertTrue(wrapper.isRunning)

        wrapper.tick()
        wait()

        // It got the value, so it stopped listening
        assertFalse(wrapper.isRunning)

        // This won't be processed - it stopped listening
        wrapper.tick()
        wait()

        assertFalse(job.isActive)
        assertFalse(job.isCancelled)
        assertTrue(job.isCompleted)
        assertEquals(1, value)
        assertEquals(1, wrapper.count)
        assertEquals(1, wrapper.timesStarted)
        assertFalse(wrapper.isRunning)
    }

    @Test
    fun testReadWhenCancelled() = runBlocking {
        val wrapper = MockListenerFlowWrapper(false)
        var value: Int? = null

        val job = GlobalScope.launch {
            value = wrapper.flow.read()
        }

        wait()

        assertTrue(wrapper.isRunning)

        // Cancel it before it gets the value
        job.cancel()
        wait()

        // There aren't any listeners
        assertFalse(wrapper.isRunning)

        // This won't be processed - it stopped listening
        wrapper.tick()
        wait()

        assertFalse(job.isActive)
        assertTrue(job.isCancelled)
        assertTrue(job.isCompleted)
        assertEquals(null, value)
        assertEquals(0, wrapper.count)
        assertEquals(1, wrapper.timesStarted)
        assertFalse(wrapper.isRunning)
    }

    @Test
    fun testReadWith2Consumers() = runBlocking {
        val wrapper = MockListenerFlowWrapper(false)
        var value1: Int? = null
        var value2: Int? = null

        val job1 = GlobalScope.launch {
            value1 = wrapper.flow.read()
        }

        val job2 = GlobalScope.launch {
            value2 = wrapper.flow.read()
        }

        wait()

        assertTrue(wrapper.isRunning)

        wrapper.tick()
        wait()

        // It got the value, so it stopped listening
        assertFalse(wrapper.isRunning)

        // This won't be processed - it stopped listening
        wrapper.tick()
        wait()

        assertFalse(job1.isActive)
        assertFalse(job1.isCancelled)
        assertTrue(job1.isCompleted)
        assertFalse(job2.isActive)
        assertFalse(job2.isCancelled)
        assertTrue(job2.isCompleted)
        assertEquals(1, value1)
        assertEquals(1, value2)
        assertEquals(1, wrapper.count)
        assertEquals(1, wrapper.timesStarted)
        assertFalse(wrapper.isRunning)
    }

    @Test
    fun testReadWith2Consumers1Cancelled() = runBlocking {
        val wrapper = MockListenerFlowWrapper(false)
        var value1: Int? = null
        var value2: Int? = null

        val job1 = GlobalScope.launch {
            value1 = wrapper.flow.read()
        }

        val job2 = GlobalScope.launch {
            value2 = wrapper.flow.read()
        }

        wait()

        assertTrue(wrapper.isRunning)

        // Cancel job 1 before it gets the value
        job1.cancel()
        wait()

        wrapper.tick()
        wait()

        // It got the value, so it stopped listening
        assertFalse(wrapper.isRunning)

        // This won't be processed - it stopped listening
        wrapper.tick()
        wait()

        assertFalse(job1.isActive)
        assertTrue(job1.isCancelled)
        assertTrue(job1.isCompleted)
        assertFalse(job2.isActive)
        assertFalse(job2.isCancelled)
        assertTrue(job2.isCompleted)
        assertEquals(null, value1)
        assertEquals(1, value2)
        assertEquals(1, wrapper.count)
        assertEquals(1, wrapper.timesStarted)
        assertFalse(wrapper.isRunning)
    }

    private suspend fun wait() {
        delay(actionWaitTime)
    }

}