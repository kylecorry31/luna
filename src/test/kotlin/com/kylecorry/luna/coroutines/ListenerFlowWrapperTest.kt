package com.kylecorry.luna.coroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(DelicateCoroutinesApi::class)
class ListenerFlowWrapperTest {

    private val actionWaitTime = 50L

    @Test
    fun canWrapListenerForSingleConsumer() = runBlocking {
        val wrapper = MockListenerFlowWrapper(false)
        val values = mutableListOf<Int>()

        val job = GlobalScope.launch {
            wrapper.flow.collectLatest {
                values.add(it)
            }
        }

        wait()

        assertTrue(wrapper.isRunning)

        wrapper.tick()
        wait()
        wrapper.tick()
        wait()
        wrapper.tick()
        wait()

        // Cancel it
        job.cancel()
        wait()

        // This won't be processed - it stopped listening
        wrapper.tick()
        wait()

        assertEquals(3, values.size)
        assertEquals(listOf(1, 2, 3), values)
        assertEquals(3, wrapper.count)
        assertEquals(1, wrapper.timesStarted)
        assertFalse(wrapper.isRunning)
    }

    @Test
    fun canWrapListenerForMultipleConsumers() = runBlocking {
        val wrapper = MockListenerFlowWrapper(false)
        val values1 = mutableListOf<Int>()
        val values2 = mutableListOf<Int>()

        val job1 = GlobalScope.launch {
            wrapper.flow.collectLatest {
                values1.add(it)
            }
        }

        val job2 = GlobalScope.launch {
            wrapper.flow.collectLatest {
                values2.add(it)
            }
        }

        wait()

        assertTrue(wrapper.isRunning)

        wrapper.tick()
        wait()
        wrapper.tick()
        wait()
        wrapper.tick()
        wait()

        // Cancel it
        job1.cancel()
        wait()

        // This won't be processed by job 1 - it stopped listening
        wrapper.tick()
        wait()

        assertTrue(wrapper.isRunning)

        // Cancel job 2
        job2.cancel()
        wait()

        // This won't be processed - there are no listeners
        wrapper.tick()
        wait()

        assertEquals(3, values1.size)
        assertEquals(listOf(1, 2, 3), values1)
        assertEquals(4, wrapper.count)
        assertEquals(1, wrapper.timesStarted)
        assertEquals(4, values2.size)
        assertEquals(listOf(1, 2, 3, 4), values2)
        assertFalse(wrapper.isRunning)
    }

    @Test
    fun canWrapListenerWithReplay() = runBlocking {
        val wrapper = MockListenerFlowWrapper(true)
        val values1 = mutableListOf<Int>()
        val values2 = mutableListOf<Int>()

        val job1 = GlobalScope.launch {
            wrapper.flow.collectLatest {
                values1.add(it)
            }
        }

        wait()

        assertTrue(wrapper.isRunning)

        wrapper.tick()
        wait()

        // This should get the first event replayed
        val job2 = GlobalScope.launch {
            wrapper.flow.collectLatest {
                values2.add(it)
            }
        }
        wait()

        wrapper.tick()
        wait()
        wrapper.tick()
        wait()

        // Cancel it
        job1.cancel()
        wait()

        // This won't be processed by job 1 - it stopped listening
        wrapper.tick()
        wait()

        assertTrue(wrapper.isRunning)

        // Cancel job 2
        job2.cancel()
        wait()

        // This won't be processed - there are no listeners
        wrapper.tick()
        wait()

        assertEquals(3, values1.size)
        assertEquals(listOf(1, 2, 3), values1)
        assertEquals(4, wrapper.count)
        assertEquals(1, wrapper.timesStarted)
        assertEquals(4, values2.size)
        assertEquals(listOf(1, 2, 3, 4), values2)
        assertFalse(wrapper.isRunning)
    }


    private suspend fun wait() {
        delay(actionWaitTime)
    }
}