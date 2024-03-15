package com.kylecorry.luna.cache

import com.kylecorry.luna.hash.HashUtils

class MemoizedValue<T> {

    private var cachedValue: T? = null
    private var cachedHash: Int? = null
    private val lock = Any()

    fun getOrPut(vararg keys: Any?, value: () -> T): T = synchronized(lock) {
        val hash = HashUtils.hash(*keys)
        if (cachedHash == null || cachedHash != hash) {
            cachedValue = value()
            cachedHash = hash
        }
        cachedValue!!
    }

    fun reset(): Unit = synchronized(lock) {
        cachedValue = null
        cachedHash = null
    }
}