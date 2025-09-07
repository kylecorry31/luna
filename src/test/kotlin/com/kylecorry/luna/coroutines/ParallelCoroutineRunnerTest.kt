package com.kylecorry.luna.coroutines

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class ParallelCoroutineRunnerTest {

    @Test
    fun testRun() = runBlocking {
        val runner = ParallelCoroutineRunner()
        var task1 = false
        var task2 = false
        var task3 = false

        val tasks = listOf<suspend () -> Any>(
            { delay(10); task1 = true },
            { delay(20); task2 = true },
            { delay(5); task3 = true }
        )

        runner.run(tasks)

        assertEquals(true, task1)
        assertEquals(true, task2)
        assertEquals(true, task3)
    }

    @Test
    fun testRunWithItems() = runBlocking {
        val runner = ParallelCoroutineRunner()
        val items = listOf(1, 2, 3)
        val results = ConcurrentHashMap<Int, Boolean>()

        runner.run(items) { item ->
            delay(item * 10L)
            results[item] = true
        }

        assertEquals(setOf(1, 2, 3), results.keys)
        assertTrue(results.values.all { it })
    }

    @Test
    fun testMapOrderPreserved() = runBlocking {
        val runner = ParallelCoroutineRunner()
        val tasks = listOf<suspend () -> Int>(
            { delay(50); 1 },
            { delay(10); 2 },
            { delay(30); 3 }
        )

        val mapped = runner.map(tasks)
        assertEquals(listOf(1, 2, 3), mapped)
    }

    @Test
    fun testMapFunctionsOrderPreserved() = runBlocking {
        val runner = ParallelCoroutineRunner()
        val functions = listOf(
            { Thread.sleep(50); 1 },
            { Thread.sleep(10); 2 },
            { Thread.sleep(30); 3 }
        )

        val mapped = runner.mapFunctions(functions)
        assertEquals(listOf(1, 2, 3), mapped)
    }

    @Test
    fun testMapWithItems() = runBlocking {
        val runner = ParallelCoroutineRunner()
        val items = listOf(1, 2, 3)

        val mapped = runner.map(items) { item ->
            when (item) {
                1 -> delay(50)
                2 -> delay(10)
                3 -> delay(30)
            }
            item * 2
        }

        assertEquals(listOf(2, 4, 6), mapped)
    }

    @Test
    fun testRespectsConcurrencyLimit() = runBlocking {
        val runner = ParallelCoroutineRunner(maxParallel = 2)
        val current = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)
        val starts = ConcurrentLinkedQueue<Int>()

        val tasks = (0 until 10).map { i ->
            suspend {
                val running = current.incrementAndGet()
                starts.add(i)
                // Track max concurrency
                while (true) {
                    val prev = maxObserved.get()
                    if (running > prev) {
                        if (maxObserved.compareAndSet(prev, running)) break
                    } else break
                }
                delay(30)
                current.decrementAndGet()
            }
        }

        runner.run(tasks)

        assertTrue(maxObserved.get() <= 2, "Exceeded concurrency limit: ${maxObserved.get()}")
    }
}
