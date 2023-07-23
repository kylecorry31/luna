package com.kylecorry.luna.topics.generic

import com.kylecorry.luna.optional.Optional

abstract class BaseTopic<T> : ITopic<T> {

    protected abstract val topic: ITopic<T>
    override val value: Optional<T>
        get() = topic.value

    override fun subscribe(subscriber: Subscriber<T>) {
        topic.subscribe(subscriber)
    }

    override fun unsubscribe(subscriber: Subscriber<T>) {
        topic.unsubscribe(subscriber)
    }

    override fun unsubscribeAll() {
        topic.unsubscribeAll()
    }

    override suspend fun read(): T {
        return topic.read()
    }
}