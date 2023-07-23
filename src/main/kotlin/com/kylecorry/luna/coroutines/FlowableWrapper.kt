package com.kylecorry.luna.coroutines

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

abstract class FlowableWrapper<T, U> : IFlowable<U> {

    protected abstract val baseFlow: IFlowable<T>

    private var _flow: Flow<U>? = null
    private val lock = Any()

    abstract fun map(value: T): U

    override val flow: Flow<U>
        get(){
            synchronized(lock) {
                if (_flow == null) {
                    _flow = baseFlow.flow.map(this::map)
                }
                return _flow!!
            }
        }
}