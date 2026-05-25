package com.kylecorry.luna.concurrency

import kotlinx.coroutines.flow.Flow

interface IFlowable<T> {
    val flow: Flow<T>
}