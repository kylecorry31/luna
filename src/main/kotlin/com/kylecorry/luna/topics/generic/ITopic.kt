package com.kylecorry.luna.topics.generic

import com.kylecorry.luna.optional.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

typealias Subscriber<T> = (T) -> Boolean

interface ITopic<T> {
    val value: Optional<T>

    fun subscribe(subscriber: Subscriber<T>)
    fun unsubscribe(subscriber: Subscriber<T>)
    fun unsubscribeAll()
    suspend fun read(): T
}

fun <T> ITopic<T>.tap(fn: (T) -> Unit): ITopic<T> {
    return TopicOperator(this, value) { result, _, value ->
        fn(value)
        result.publish(value)
    }
}

fun <T, V> ITopic<T>.map(fn: (T) -> V): ITopic<V> {
    return TopicOperator(this, value.map(fn)) { result, _, value ->
        result.publish(fn(value))
    }
}

fun <T> ITopic<T>.collect(minHistory: Int = 0, maxHistory: Int = Int.MAX_VALUE): ITopic<List<T>> {
    val data = mutableListOf<T>()
    value.ifPresent {
        data.add(it)
        while (data.size > 0 && data.size > maxHistory) {
            data.removeFirst()
        }
    }

    val initial = if (data.size > minHistory) {
        Optional.of(data.toList())
    } else {
        Optional.empty()
    }
    return TopicOperator(this, initial) { result, _, value ->
        data.add(value)
        while (data.size > 0 && data.size > maxHistory) {
            data.removeFirst()
        }

        if (data.size < minHistory) {
            return@TopicOperator
        }

        result.publish(data.toList())
    }
}

fun <T, V> ITopic<T>.suspendMap(
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    fn: suspend (T) -> V
): ITopic<V> {
    return TopicOperator(this) { result, _, value ->
        scope.launch {
            result.publish(fn(value))
        }
    }
}

fun <T> ITopic<T>.distinct(): ITopic<T> {
    return TopicOperator(this, value) { result, _, value ->
        val current = result.value
        if (current.isEmpty || current.get() != value) {
            result.publish(value)
        }
    }
}

fun <T> ITopic<T>.filter(predicate: (T) -> Boolean): ITopic<T> {
    return TopicOperator(this, value) { result, _, value ->
        if (predicate(value)) {
            result.publish(value)
        }
    }
}

fun <T> ITopic<T>.replay(): ITopic<T> {
    return TopicOperator(
        this,
        value,
        { _, subscriber, result ->
            result.value.ifPresent {
                val keep = subscriber(it)
                if (!keep) {
                    result.unsubscribe(subscriber)
                }
            }
        }) { result, _, value ->
        result.publish(value)
    }
}