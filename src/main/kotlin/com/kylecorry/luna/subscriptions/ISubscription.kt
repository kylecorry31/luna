package com.kylecorry.luna.subscriptions

import kotlinx.coroutines.flow.Flow

interface ISubscription {
    fun subscribe(listener: suspend () -> Unit)

    fun subscribe(listener: suspend () -> Unit, modifiers: (flow: Flow<Unit>) -> Flow<Unit>)

    fun unsubscribe(listener: suspend () -> Unit)

    fun unsubscribeAll()

    fun publish()

    fun flow(): Flow<Unit>
}