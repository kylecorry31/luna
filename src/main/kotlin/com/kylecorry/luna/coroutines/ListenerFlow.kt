package com.kylecorry.luna.coroutines

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

abstract class ListenerFlow<T>(replay: Boolean = false): IFlowable<T> {

    private val lock = Any()

    private val _flow = MutableSharedFlow<T>(
        replay = if (replay) 1 else 0,
        extraBufferCapacity = if (replay) 0 else 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val flow = _flow
        .onStart { startListening() }
        .onCompletion { stopListening() }

    private var activeListeners = 0

    protected abstract fun start()
    protected abstract fun stop()

    private fun startListening() {
        synchronized(lock) {
            if (activeListeners == 0) {
                start()
            }
            activeListeners++
        }
    }

    private fun stopListening() {
        synchronized(lock) {
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