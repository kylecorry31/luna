package com.kylecorry.luna.coroutines

import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class CustomDispatchersTest {

    @Test
    fun testNewFixedThreadDispatcherUsesNameAndDaemonThreads() = runBlocking {
        val dispatcher = CustomDispatchers.newFixedThreadDispatcher(threads = 1, name = "TestDispatcher")

        dispatcher.use { dispatcher ->
            var threadName: String? = null
            var isDaemon: Boolean? = null

            withContext(dispatcher) {
                threadName = Thread.currentThread().name.substringBefore(" @")
                isDaemon = Thread.currentThread().isDaemon
            }

            assertTrue(threadName!!.startsWith("TestDispatcher-"))
            assertTrue(isDaemon == true)
        }
    }

    @Test
    fun testNewFixedThreadDispatcherRespectsThreadCountUpperBound() = runBlocking {
        val dispatcher = CustomDispatchers.newFixedThreadDispatcher(threads = 2, name = "Pool")

        dispatcher.use { dispatcher ->
            val names = ConcurrentHashMap.newKeySet<String>()

            val jobs = List(20) {
                launch(dispatcher) {
                    names.add(Thread.currentThread().name.substringBefore(" @"))
                    delay(200.milliseconds)
                }
            }

            jobs.joinAll()

            assertTrue(names.isNotEmpty())
            assertTrue(names.size <= 2)
            assertTrue(names.all { it.startsWith("Pool-") })
        }
    }
}
