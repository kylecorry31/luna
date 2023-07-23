package com.kylecorry.luna.topics

abstract class BaseTopic : ITopic {

    protected abstract val topic: ITopic

    override fun subscribe(subscriber: Subscriber) {
        topic.subscribe(subscriber)
    }

    override fun unsubscribe(subscriber: Subscriber) {
        topic.unsubscribe(subscriber)
    }

    override fun unsubscribeAll() {
        topic.unsubscribeAll()
    }

    override suspend fun read() {
        topic.read()
    }
}