package com.kylecorry.luna.hooks

import com.kylecorry.luna.equality.EqualityChangeDetector

class MemoizedValue<T> {

    private var cachedValue: T? = null
    private val changeDetector = EqualityChangeDetector()
    private val lock = Any()

    fun getOrPut(vararg dependencies: Any?, value: () -> T): T = synchronized(lock) {
        if (changeDetector.hasChanges(dependencies)) {
            cachedValue = value()
        }
        // This cast is safe because the value is only set in the above block - it also handles if T is nullable
        @Suppress("UNCHECKED_CAST")
        cachedValue as T
    }

    fun reset(): Unit = synchronized(lock) {
        cachedValue = null
        changeDetector.reset()
    }
}