package com.kylecorry.luna.subscriptions

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

class Subscription(
    replay: Int = 0,
    bufferSize: Int = 1,
    bufferOverflowBehavior: BufferOverflow = BufferOverflow.DROP_OLDEST,
    private val onStart: suspend () -> Unit = {},
    private val onStop: suspend () -> Unit = {},
) : ISubscription {
    private val sharedFlow = MutableSharedFlow<Unit>(
        replay = replay,
        extraBufferCapacity = bufferSize,
        onBufferOverflow = bufferOverflowBehavior
    )

    private val startStopLock = Mutex()
    private var activeListeners = 0

    private val subscriptionFlow = sharedFlow
        .onSubscription { withContext(NonCancellable) { startSubscription() } }
        .onCompletion { withContext(NonCancellable) { stopSubscription() } }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val jobLock = Any()
    private val listeners = mutableMapOf<suspend () -> Unit, Job>()

    override fun subscribe(listener: suspend () -> Unit) {
        subscribe(listener) { it }
    }

    override fun subscribe(
        listener: suspend () -> Unit,
        modifiers: (Flow<Unit>) -> Flow<Unit>
    ) {
        synchronized(jobLock) {
            listeners[listener]?.cancel()
            listeners[listener] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                modifiers(subscriptionFlow).collect { listener() }
            }
        }
    }

    override fun unsubscribe(listener: suspend () -> Unit) {
        synchronized(jobLock) {
            listeners.remove(listener)?.cancel()
        }
    }

    override fun unsubscribeAll() {
        synchronized(jobLock) {
            listeners.values.forEach { it.cancel() }
            listeners.clear()
        }
    }

    override fun publish() {
        sharedFlow.tryEmit(Unit)
    }

    override fun flow(): Flow<Unit> = subscriptionFlow

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
