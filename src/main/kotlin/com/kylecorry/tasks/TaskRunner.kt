package com.kylecorry.tasks

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskRunner(
    private val queueSize: Int = 0,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ignoreExceptions: Boolean = false
) {

    private val queue = mutableListOf<suspend () -> Unit>()
    private var job: Job? = null
    private val mutex = Mutex()

    suspend fun enqueue(task: suspend () -> Unit) {
        mutex.withLock {
            // If the queue is empty and there is no running task, run the task immediately
            if ((queueSize == 0 && job?.isActive != true) || queue.size < queueSize) {
                queue.add(task)
            }
            Unit
        }

        onTaskAdded()
    }

    suspend fun replace(task: suspend () -> Unit) {
        cancelAsync()
        enqueue(task)
    }

    suspend fun skipIfRunning(task: suspend () -> Unit) {
        val shouldEnqueue = mutex.withLock {
            queue.isEmpty() && job?.isActive != true
        }
        if (shouldEnqueue) {
            enqueue(task)
        }
    }

    fun cancel() = runBlocking {
        cancelAsync()
    }

    suspend fun cancelAsync() {
        mutex.withLock {
            job?.cancel()
            job = null
            queue.clear()
        }
    }

    private suspend fun processNextTask() {
        mutex.withLock {
            // A task is already running
            if (job?.isActive == true) {
                return@withLock
            }
            val task = queue.removeFirstOrNull()
            if (task != null) {
                runTask(task)
            }
        }
    }

    private fun runTask(task: suspend () -> Unit) {
        job = scope.launch(dispatcher) {
            try {
                task.invoke()
            } catch (e: Exception) {
                if (!ignoreExceptions) {
                    throw e
                }
            }
            job = null
            scope.launch { processNextTask() }
        }
    }

    private suspend fun onTaskAdded() {
        processNextTask()
    }
}