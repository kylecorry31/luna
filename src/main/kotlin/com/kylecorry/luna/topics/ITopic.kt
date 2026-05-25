package com.kylecorry.luna.topics

import com.kylecorry.luna.concurrency.IFlowable
import com.kylecorry.luna.topics.generic.AdapterTopic

typealias Subscriber = () -> Boolean

interface ITopic: IFlowable<Unit> {
    fun subscribe(subscriber: Subscriber)
    fun unsubscribe(subscriber: Subscriber)
    fun unsubscribeAll()
    suspend fun read(isSatisfied: () -> Boolean = { true })
}

fun <T: Any> ITopic.map(fn: () -> T): com.kylecorry.luna.topics.generic.ITopic<T> {
    return AdapterTopic(this, fn)
}