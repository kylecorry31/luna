package com.kylecorry.luna.topics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class Topic(
    private val onSubscriberAdded: (count: Int, subscriber: Subscriber) -> Unit = { _, _ -> },
    private val onSubscriberRemoved: (count: Int, subscriber: Subscriber) -> Unit = { _, _ -> },
) : ITopic {

    private val subscribers = mutableSetOf<Subscriber>()

    override fun subscribe(subscriber: Subscriber) {
        synchronized(subscribers) {
            val wasAdded = subscribers.add(subscriber)
            if (wasAdded) {
                onSubscriberAdded(subscribers.size, subscriber)
            }
        }
    }

    override fun unsubscribe(subscriber: Subscriber) {
        synchronized(subscribers) {
            val wasRemoved = subscribers.remove(subscriber)
            if (wasRemoved) {
                onSubscriberRemoved(subscribers.size, subscriber)
            }
        }
    }

    override fun unsubscribeAll() {
        val copy = synchronized(subscribers) { subscribers.toList() }
        copy.forEach(::unsubscribe)
    }

    override suspend fun read(isSatisfied: () -> Boolean) = suspendCancellableCoroutine { cont ->
        val callback: () -> Boolean = {
            if (isSatisfied()) {
                cont.resume(Unit)
                false
            } else {
                true
            }
        }
        cont.invokeOnCancellation {
            unsubscribe(callback)
        }
        subscribe(callback)
    }

    fun publish() {
        val subs = synchronized(subscribers) {
            subscribers.toList()
        }
        subs.filter { !it.invoke() }.forEach(::unsubscribe)
    }

    private val externalScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val flow: Flow<Unit> = callbackFlow {
        val subscription = {
            trySend(Unit)
            true
        }
        subscribe(subscription)
        awaitClose { unsubscribe(subscription) }
    }.shareIn(externalScope, SharingStarted.WhileSubscribed(), 0)

    companion object {

        /**
         * Creates a topic that will start when one subscriber is added and stop when none are left
         */
        fun lazy(start: () -> Unit, stop: () -> Unit): Topic {
            return Topic(
                { count, _ -> if (count == 1) start() },
                { count, _ -> if (count == 0) stop() }
            )
        }
    }
}