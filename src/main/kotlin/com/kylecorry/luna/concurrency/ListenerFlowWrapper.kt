package com.kylecorry.luna.concurrency

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription

/**
 * A wrapper for a flow around a listener (e.g. a sensor listener)
 * It will start register a listener when the first flow is collected and stop listening when the last flow is collected
 * @param replay True if the flow should replay the last value to new collectors
 */
abstract class ListenerFlowWrapper<T>(replay: Boolean = false) : IFlowable<T> {

    private val lock = Any()

    private val _flow = MutableSharedFlow<T>(
        replay = if (replay) 1 else 0,
        extraBufferCapacity = if (replay) 0 else 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val flow = _flow
        .onSubscription { startListening() }
        .onCompletion { stopListening() }

    private var activeListeners = 0

    protected abstract fun start()
    protected abstract fun stop()

    private fun startListening() {
        synchronized(lock) {
            val shouldStart = activeListeners == 0
            activeListeners++
            if (shouldStart) {
                start()
            }
        }
    }

    private fun stopListening() {
        synchronized(lock) {
            if (activeListeners == 0) {
                return
            }
            activeListeners--
            if (activeListeners == 0) {
                stop()
            }
        }
    }

    protected fun emit(event: T) {
        _flow.tryEmit(event)
    }
}
