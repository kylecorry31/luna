package com.kylecorry.luna.coroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(DelicateCoroutinesApi::class)
class FlowableWrapperTest {

    private val actionWaitTime = 50L

    @Test
    fun canWrapListenerForSingleConsumer() = runBlocking {
        val wrapper = MockListenerFlowWrapper(false)
        val mappedWrapper = StringWrapper(wrapper)
        val values = mutableListOf<String>()

        val job = GlobalScope.launch {
            mappedWrapper.flow.collectLatest {
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
        assertEquals(listOf("1", "2", "3"), values)
        assertEquals(3, wrapper.count)
        assertEquals(1, wrapper.timesStarted)
        assertFalse(wrapper.isRunning)
    }

    @Test
    fun canWrapListenerForMultipleConsumers() = runBlocking {
        val wrapper = MockListenerFlowWrapper(false)
        val mappedWrapper = StringWrapper(wrapper)
        val values1 = mutableListOf<String>()
        val values2 = mutableListOf<String>()

        val job1 = GlobalScope.launch {
            mappedWrapper.flow.collectLatest {
                values1.add(it)
            }
        }

        val job2 = GlobalScope.launch {
            mappedWrapper.flow.collectLatest {
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
        assertEquals(listOf("1", "2", "3"), values1)
        assertEquals(4, wrapper.count)
        assertEquals(1, wrapper.timesStarted)
        assertEquals(4, values2.size)
        assertEquals(listOf("1", "2", "3", "4"), values2)
        assertFalse(wrapper.isRunning)
    }

    private suspend fun wait() {
        delay(actionWaitTime)
    }

    private class StringWrapper(private val mockFlowable: MockListenerFlowWrapper) : FlowableWrapper<Int, String>() {
        override val baseFlow: IFlowable<Int>
            get() = mockFlowable

        override fun map(value: Int): String {
            return value.toString()
        }
    }

}