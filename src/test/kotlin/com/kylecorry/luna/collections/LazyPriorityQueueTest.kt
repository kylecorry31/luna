package com.kylecorry.luna.collections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch

class LazyPriorityQueueTest {

    @Test
    fun enqueueAndDequeueUsesPriorityOrder() {
        val queue = LazyPriorityQueue<Int>(10, compareByDescending { it })

        queue.enqueue(2)
        queue.enqueue(5)
        queue.enqueue(1)

        val items = queue.dequeue(3)

        assertEquals(listOf(5, 2, 1), items)
        assertEquals(0, queue.count())
    }

    @Test
    fun dequeueRespectsRequestedCount() {
        val queue = LazyPriorityQueue<Int>(10, compareByDescending { it })

        queue.enqueue(1)
        queue.enqueue(3)
        queue.enqueue(2)
        assertEquals(3, queue.count())

        val first = queue.dequeue(2)

        assertEquals(listOf(3, 2), first)
        assertEquals(1, queue.count())

        val second = queue.dequeue(2)

        assertEquals(listOf(1), second)
        assertEquals(0, queue.count())
    }

    @Test
    fun recalculatePrioritiesReordersExistingItems() {
        data class Item(val id: String, var priority: Int)

        val queue = LazyPriorityQueue<Item>(10, compareByDescending { it.priority })

        val low = Item("low", 1)
        val medium = Item("medium", 3)
        val high = Item("high", 5)

        queue.enqueue(low)
        queue.enqueue(medium)
        queue.enqueue(high)

        // Remove the high item
        queue.dequeue(1)

        // Adjust the low priority so it gets picked up next
        low.priority = 10

        queue.recalculatePriorities()

        assertEquals(listOf("low", "medium"), queue.dequeue(3).map { it.id })
        assertEquals(0, queue.count())
    }

    @Test
    fun clearRemovesAllItems() {
        val queue = LazyPriorityQueue<Int>(10, compareByDescending { it })

        queue.enqueue(1)
        queue.enqueue(2)
        queue.clear()

        assertEquals(0, queue.count())
        assertEquals(emptyList<Int>(), queue.dequeue(1))
    }

    @Test
    fun dequeueFromEmptyQueueReturnsEmptyList() {
        val queue = LazyPriorityQueue<Int>(10, compareByDescending { it })

        assertEquals(emptyList<Int>(), queue.dequeue(3))
        assertEquals(0, queue.count())
    }

    @Test
    fun concurrentDequeuesReturnEachItemExactlyOnce() {
        val itemCount = 10000
        val threadCount = 4
        val queue = LazyPriorityQueue<Int>(itemCount, compareByDescending { it })
        repeat(itemCount) { queue.enqueue(it) }

        val dequeued = ConcurrentLinkedQueue<Int>()
        val start = CountDownLatch(1)
        val threads = (0 until threadCount).map {
            Thread {
                start.await()
                while (dequeued.size < itemCount) {
                    dequeued.addAll(queue.dequeue(8))
                }
            }
        }

        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join(10000) }

        assertEquals(itemCount, dequeued.size)
        assertEquals((0 until itemCount).toSet(), dequeued.toSet())
        assertEquals(0, queue.count())
    }

    @Test
    fun concurrentEnqueuesAndDequeuesDoNotLoseItems() {
        val perThread = 2000
        val producerCount = 4
        val itemCount = perThread * producerCount
        val queue = LazyPriorityQueue<Int>(64, compareByDescending { it })

        val dequeued = ConcurrentLinkedQueue<Int>()
        val start = CountDownLatch(1)
        val producers = (0 until producerCount).map { thread ->
            Thread {
                start.await()
                repeat(perThread) { queue.enqueue(thread * perThread + it) }
            }
        }
        val consumers = (0 until 2).map {
            Thread {
                start.await()
                while (dequeued.size < itemCount) {
                    dequeued.addAll(queue.dequeue(8))
                }
            }
        }

        (producers + consumers).forEach { it.start() }
        start.countDown()
        (producers + consumers).forEach { it.join(10000) }

        assertEquals(itemCount, dequeued.size)
        assertEquals((0 until itemCount).toSet(), dequeued.toSet())
        assertEquals(0, queue.count())
    }

    @Test
    fun clearDuringDequeueLeavesQueueConsistent() {
        val queue = LazyPriorityQueue<Int>(64, compareByDescending { it })
        repeat(1000) { queue.enqueue(it) }

        val dequeued = ConcurrentLinkedQueue<Int>()
        val start = CountDownLatch(1)
        val consumer = Thread {
            start.await()
            repeat(200) { dequeued.addAll(queue.dequeue(4)) }
        }
        val clearer = Thread {
            start.await()
            repeat(50) { queue.clear() }
        }

        consumer.start()
        clearer.start()
        start.countDown()
        consumer.join(10000)
        clearer.join(10000)

        // Items may be discarded by clear, but none may be returned twice and the count cannot go negative
        assertEquals(dequeued.size, dequeued.toSet().size)
        assertTrue(queue.count() >= 0, "Count was ${queue.count()}")
    }

    @Test
    fun clearResetsPendingRecalculation() {
        data class Item(val id: String, var priority: Int)

        val queue = LazyPriorityQueue<Item>(10, compareByDescending { it.priority })
        queue.enqueue(Item("a", 1))
        queue.recalculatePriorities()
        queue.clear()

        val low = Item("low", 1)
        val high = Item("high", 5)
        queue.enqueue(low)
        queue.enqueue(high)

        assertEquals(listOf("high", "low"), queue.dequeue(2).map { it.id })
    }
}
