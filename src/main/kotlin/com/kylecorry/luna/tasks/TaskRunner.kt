package com.kylecorry.luna.tasks

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskRunner(
    private val queueSize: Int = 1,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ignoreExceptions: Boolean = false
) {
    private var taskChannel = Channel<suspend () -> Unit>(queueSize)
    private var consumerJob: Job? = null
    private var isRunningTask = false
    private val mutex = Mutex()
    private val replaceMutex = Mutex()

    init {
        launchConsumer()
    }

    private fun launchConsumer() {
        consumerJob?.cancel() // cancel the existing consumer job
        consumerJob = scope.launch {
            for (task in taskChannel) {
                withContext(dispatcher) {
                    try {
                        mutex.withLock { isRunningTask = true }
                        task.invoke()
                    } catch (e: Exception) {
                        if (!ignoreExceptions) {
                            throw e
                        }
                    } finally {
                        mutex.withLock { isRunningTask = false }
                    }
                }
            }
        }
    }

    suspend fun enqueue(task: suspend () -> Unit): Boolean {
        checkConsumer()
        val result = taskChannel.trySend(task)
        return result.isSuccess
    }

    suspend fun replace(task: suspend () -> Unit) {
        replaceMutex.withLock {
            cancelAndJoin()
            enqueue(task)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun skipIfRunning(task: suspend () -> Unit): Boolean {
        checkConsumer()
        val shouldEnqueue = mutex.withLock { !isRunningTask && taskChannel.isEmpty }
        if (shouldEnqueue) {
            enqueue(task)
        }
        return shouldEnqueue
    }

    suspend fun cancelAndJoin() {
        consumerJob?.cancelAndJoin()
        taskChannel.close()
    }

    fun cancel(){
        consumerJob?.cancel()
        taskChannel.close()
    }

    private fun checkConsumer() {
        if (consumerJob?.isActive != true) {
            taskChannel = Channel(queueSize)
            launchConsumer()
        }
    }
}
