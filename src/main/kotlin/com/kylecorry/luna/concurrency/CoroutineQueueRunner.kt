package com.kylecorry.luna.concurrency

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

class CoroutineQueueRunner(
    private val queueSize: Int = 1,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val dispatcher: CoroutineContext = Dispatchers.Default,
    private val ignoreExceptions: Boolean = false,
    private val queuePolicy: BufferOverflow = BufferOverflow.DROP_LATEST
) {
    private var taskChannel = newChannel()
    private var consumerJob: Job? = null
    private var isRunningTask = false
    private val mutex = Mutex()
    private val replaceMutex = Mutex()
    private val consumerLock = Any()

    init {
        synchronized(consumerLock) {
            launchConsumer(taskChannel)
        }
    }

    private fun newChannel() = Channel<suspend () -> Unit>(queueSize, queuePolicy)

    private fun launchConsumer(channel: Channel<suspend () -> Unit>) {
        consumerJob?.cancel() // cancel the existing consumer job
        consumerJob = scope.launch {
            for (task in channel) {
                try {
                    mutex.withLock { isRunningTask = true }
                    withContext(dispatcher) {
                        task.invoke()
                    }
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

    suspend fun enqueue(task: suspend () -> Unit): Boolean {
        return activeChannel().trySend(task).isSuccess
    }

    suspend fun replace(task: suspend () -> Unit) {
        replaceMutex.withLock {
            cancelAndJoin()
            enqueue(task)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun skipIfRunning(task: suspend () -> Unit): Boolean {
        val channel = activeChannel()
        val shouldEnqueue = mutex.withLock { !isRunningTask && channel.isEmpty }
        if (shouldEnqueue) {
            channel.trySend(task)
        }
        return shouldEnqueue
    }

    suspend fun cancelAndJoin() {
        val job = synchronized(consumerLock) { consumerJob }
        job?.cancelAndJoin()
        synchronized(consumerLock) { taskChannel.close() }
    }

    fun cancel() {
        synchronized(consumerLock) {
            consumerJob?.cancel()
            taskChannel.close()
        }
    }

    private fun activeChannel(): Channel<suspend () -> Unit> {
        return synchronized(consumerLock) {
            if (consumerJob?.isActive != true) {
                taskChannel = newChannel()
                launchConsumer(taskChannel)
            }
            taskChannel
        }
    }
}
