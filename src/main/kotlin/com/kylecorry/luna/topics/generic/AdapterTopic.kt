package com.kylecorry.luna.topics.generic

import com.kylecorry.luna.optional.Optional
import com.kylecorry.luna.topics.ITopic

internal class AdapterTopic<T>(
    private val baseTopic: ITopic,
    private val valueSupplier: () -> T,
    defaultValue: Optional<T> = Optional.empty()
) :
    BaseTopic<T>() {

    override val topic = Topic.lazy(
        { baseTopic.subscribe(this::onValue) },
        { baseTopic.unsubscribe(this::onValue) },
        defaultValue
    )

    private fun onValue(): Boolean {
        topic.publish(valueSupplier())
        return true
    }

}