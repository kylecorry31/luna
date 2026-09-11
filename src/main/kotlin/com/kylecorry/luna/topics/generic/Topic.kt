package com.kylecorry.luna.topics.generic

import com.kylecorry.luna.concurrency.sharedCallbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class Topic<T : Any>(
    private val onSubscriberAdded: (count: Int, subscriber: Subscriber<T>) -> Unit = { _, _ -> },
    private val onSubscriberRemoved: (count: Int, subscriber: Subscriber<T>) -> Unit = { _, _ -> },
    defaultValue: Optional<T> = Optional.empty()
) : ITopic<T> {

    override var value: Optional<T> = defaultValue
        private set

    private val subscribers = mutableSetOf<Subscriber<T>>()

    override fun subscribe(subscriber: Subscriber<T>) {
        synchronized(subscribers) {
            val wasAdded = subscribers.add(subscriber)
            if (wasAdded) {
                onSubscriberAdded(subscribers.size, subscriber)
            }
        }
    }

    override fun unsubscribe(subscriber: Subscriber<T>) {
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

    override suspend fun read(): T = suspendCancellableCoroutine { cont ->
        val hasRead = AtomicBoolean(false)
        val callback: (T) -> Boolean = {
            if (hasRead.compareAndSet(false, true)) {
                cont.resume(it)
            }
            false
        }
        subscribe(callback)
        cont.invokeOnCancellation {
            unsubscribe(callback)
        }
    }

    fun publish(value: T) {
        this.value = Optional.of(value)
        val subs = synchronized(subscribers) {
            subscribers.toList()
        }
        subs.filter { !it.invoke(value) }.forEach(::unsubscribe)
    }

    override val flow: Flow<T> = sharedCallbackFlow {
        val subscription: Subscriber<T> = { value ->
            trySend(value)
            true
        }
        subscribe(subscription)
        awaitClose { unsubscribe(subscription) }
    }

    companion object {

        /**
         * Creates a topic that will start when one subscriber is added and stop when none are left
         */
        fun <T : Any> lazy(
            start: () -> Unit,
            stop: () -> Unit,
            defaultValue: Optional<T> = Optional.empty()
        ): Topic<T> {
            return Topic(
                { count, _ -> if (count == 1) start() },
                { count, _ -> if (count == 0) stop() },
                defaultValue
            )
        }
    }
}
