package com.kylecorry.luna.subscriptions.generic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class Subscription<T>(
    replay: Int = 0,
    bufferSize: Int = 1,
    bufferOverflowBehavior: BufferOverflow = BufferOverflow.DROP_OLDEST,
    private val onStart: suspend () -> Unit = {},
    private val onStop: suspend () -> Unit = {},
) : ISubscription<T> {

    private val startStopLock = Mutex()
    private var activeListeners = 0
    private val sharedFlow = MutableSharedFlow<T>(
        replay = replay,
        extraBufferCapacity = bufferSize,
        onBufferOverflow = bufferOverflowBehavior
    )

    override val flow: Flow<T> = sharedFlow
        .onSubscription { withContext(NonCancellable) { startSubscription() } }
        .onCompletion { withContext(NonCancellable) { stopSubscription() } }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val jobLock = Any()
    private class ListenerEntry {
        var job: Job? = null
        var cancelled = false
    }

    private val listeners = mutableMapOf<Any, ListenerEntry>()

    override fun subscribe(listener: suspend (value: T) -> Unit) {
        subscribe(listener) { it }
    }

    override fun subscribe(
        listener: suspend (value: T) -> Unit,
        modifiers: (Flow<T>) -> Flow<T>
    ) {
        subscribeListener(listener, listener, modifiers)
    }

    internal fun subscribeListener(
        key: Any,
        listener: suspend (T) -> Unit,
        modifiers: (Flow<T>) -> Flow<T>
    ) {
        val entry = ListenerEntry()
        synchronized(jobLock) {
            listeners.put(key, entry)?.let(::cancelEntry)
        }

        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            modifiers(flow).collect(listener)
        }

        synchronized(jobLock) {
            if (listeners[key] === entry && !entry.cancelled) {
                entry.job = job
            } else {
                entry.cancelled = true
                job.cancel()
            }
        }
    }

    override fun unsubscribe(listener: suspend (value: T) -> Unit) {
        unsubscribeListener(listener)
    }

    internal fun unsubscribeListener(key: Any) {
        synchronized(jobLock) {
            listeners.remove(key)?.let(::cancelEntry)
        }
    }

    override fun unsubscribeAll() {
        synchronized(jobLock) {
            val entries = listeners.values.toList()
            listeners.clear()
            entries.forEach(::cancelEntry)
        }
    }

    private fun cancelEntry(entry: ListenerEntry) {
        entry.cancelled = true
        entry.job?.cancel()
    }

    override fun publish(value: T) {
        sharedFlow.tryEmit(value)
    }

    private suspend fun startSubscription() {
        startStopLock.withLock {
            val shouldStart = activeListeners == 0
            activeListeners++
            if (shouldStart) {
                onStart()
            }
        }
    }

    private suspend fun stopSubscription() {
        startStopLock.withLock {
            if (activeListeners == 0) {
                return
            }
            activeListeners--
            if (activeListeners == 0) {
                onStop()
            }
        }
    }
}
