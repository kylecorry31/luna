package com.kylecorry.luna.coroutines

import kotlinx.coroutines.flow.Flow

interface IFlowable<T> {
    val flow: Flow<T>
}