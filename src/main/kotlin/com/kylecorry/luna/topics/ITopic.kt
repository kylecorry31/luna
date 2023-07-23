package com.kylecorry.luna.topics

import com.kylecorry.luna.topics.generic.AdapterTopic

typealias Subscriber = () -> Boolean

interface ITopic {
    fun subscribe(subscriber: Subscriber)
    fun unsubscribe(subscriber: Subscriber)
    fun unsubscribeAll()
    suspend fun read()
}

fun <T: Any> ITopic.map(fn: () -> T): com.kylecorry.luna.topics.generic.ITopic<T> {
    return AdapterTopic(this, fn)
}