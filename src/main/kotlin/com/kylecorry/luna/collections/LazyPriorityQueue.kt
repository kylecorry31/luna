package com.kylecorry.luna.collections

import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * A priority queue which does not determine priority until dequeue time
 */
class LazyPriorityQueue<T : Any>(initialCapacity: Int, comparator: Comparator<T>) {
    private val stagingQueue = ConcurrentLinkedQueue<T>()
    private val priorityQueue = PriorityQueue(initialCapacity, comparator)
    private val count = AtomicInteger(0)
    private val stagedCount = AtomicInteger(0)

    @Volatile
    private var prioritiesChanged = false

    /**
     * Enqueue an item into the priority queue. This operation is thread safe.
     */
    fun enqueue(item: T) {
        stagingQueue.add(item)
        count.incrementAndGet()
        stagedCount.incrementAndGet()
    }

    /**
     * Recalculate the priorities during the next dequeue
     */
    fun recalculatePriorities() {
        prioritiesChanged = true
    }

    /**
     * Dequeue the highest priority items from the queue
     * This is not thread safe
     */
    fun dequeue(count: Int = 1): List<T> {
        // Read the staging queue
        val stagedItems = (0..<stagedCount.get()).mapNotNull {
            val item = stagingQueue.poll()
            if (item != null) {
                stagedCount.decrementAndGet()
            }
            item
        }

        // Reprioritize
        if (prioritiesChanged) {
            // Clear the flag first, so if this changes it will get picked up on the next dequeue
            prioritiesChanged = false
            val originalItems = priorityQueue.toList()
            priorityQueue.clear()
            priorityQueue.addAll(originalItems)
        }

        // Prioritize the new items
        priorityQueue.addAll(stagedItems)

        // Dequeue
        return (0..<count).mapNotNull {
            val item = priorityQueue.poll()
            if (item != null) {
                this.count.decrementAndGet()
            }
            item
        }
    }

    /**
     * Clear the queue
     * This is not thread safe
     */
    fun clear() {
        stagingQueue.clear()
        priorityQueue.clear()
        count.set(0)
        stagedCount.set(0)
    }

    fun count(): Int {
        return count.get()
    }

}
