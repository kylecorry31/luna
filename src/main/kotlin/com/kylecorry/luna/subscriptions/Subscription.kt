package com.kylecorry.luna.subscriptions

import com.kylecorry.luna.subscriptions.generic.Subscription as ValueSubscription
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow

class Subscription(
    replay: Int = 0,
    bufferSize: Int = 1,
    bufferOverflowBehavior: BufferOverflow = BufferOverflow.DROP_OLDEST,
    onStart: suspend () -> Unit = {},
    onStop: suspend () -> Unit = {},
) : ISubscription {
    private val subscription = ValueSubscription<Unit>(
        replay = replay,
        bufferSize = bufferSize,
        bufferOverflowBehavior = bufferOverflowBehavior,
        onStart = onStart,
        onStop = onStop
    )

    override fun subscribe(listener: suspend () -> Unit) {
        subscribe(listener) { it }
    }

    override fun subscribe(listener: suspend () -> Unit, modifiers: (Flow<Unit>) -> Flow<Unit>) {
        subscription.subscribeListener(listener, { listener() }, modifiers)
    }

    override fun unsubscribe(listener: suspend () -> Unit) {
        subscription.unsubscribeListener(listener)
    }

    override fun unsubscribeAll() = subscription.unsubscribeAll()

    override fun publish() = subscription.publish(Unit)

    override val flow: Flow<Unit>
        get() = subscription.flow
}
