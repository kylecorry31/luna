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
    private val listeners = mutableMapOf<Any, Job>()

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
        synchronized(jobLock) {
            listeners[key]?.cancel()
            listeners[key] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                modifiers(flow).collect(listener)
            }
        }
    }

    override fun unsubscribe(listener: suspend (value: T) -> Unit) {
        unsubscribeListener(listener)
    }

    internal fun unsubscribeListener(key: Any) {
        synchronized(jobLock) {
            listeners.remove(key)?.cancel()
        }
    }

    override fun unsubscribeAll() {
        synchronized(jobLock) {
            listeners.values.forEach { it.cancel() }
            listeners.clear()
        }
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
