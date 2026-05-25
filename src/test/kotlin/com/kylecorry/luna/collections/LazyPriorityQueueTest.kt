package com.kylecorry.luna.collections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
