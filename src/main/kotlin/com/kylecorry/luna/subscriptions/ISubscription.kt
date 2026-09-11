package com.kylecorry.luna.subscriptions

import com.kylecorry.luna.concurrency.IFlowable
import kotlinx.coroutines.flow.Flow

interface ISubscription : IFlowable<Unit> {
    fun subscribe(listener: suspend () -> Unit)

    fun subscribe(listener: suspend () -> Unit, modifiers: (flow: Flow<Unit>) -> Flow<Unit>)

    fun unsubscribe(listener: suspend () -> Unit)

    fun unsubscribeAll()

    fun publish()
}
