package com.kylecorry.luna.subscriptions.generic

import com.kylecorry.luna.concurrency.IFlowable
import kotlinx.coroutines.flow.Flow

interface ISubscription<T> : IFlowable<T> {
    fun subscribe(listener: suspend (T) -> Unit)

    fun subscribe(listener: suspend (T) -> Unit, modifiers: (flow: Flow<T>) -> Flow<T>)

    fun unsubscribe(listener: suspend (T) -> Unit)

    fun unsubscribeAll()

    fun publish(value: T)
}
