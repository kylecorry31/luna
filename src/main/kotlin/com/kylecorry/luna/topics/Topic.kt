package com.kylecorry.luna.topics

import com.kylecorry.luna.concurrency.sharedCallbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
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
        val hasRead = AtomicBoolean(false)
        val callback: () -> Boolean = {
            if (isSatisfied()) {
                if (hasRead.compareAndSet(false, true)) {
                    cont.resume(Unit)
                }
                false
            } else {
                true
            }
        }
        subscribe(callback)
        cont.invokeOnCancellation {
            unsubscribe(callback)
        }
    }

    fun publish() {
        val subs = synchronized(subscribers) {
            subscribers.toList()
        }
        subs.filter { !it.invoke() }.forEach(::unsubscribe)
    }

    override val flow: Flow<Unit> = sharedCallbackFlow {
        val subscription = {
            trySend(Unit)
            true
        }
        subscribe(subscription)
        awaitClose { unsubscribe(subscription) }
    }

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