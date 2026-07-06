package com.kylecorry.luna.cache

interface Cache<K, T> {
    suspend fun get(key: K): T?

    suspend fun peek(key: K): T?

    suspend fun put(key: K, value: T)

    suspend fun getOrPut(key: K, lookup: suspend () -> T): T

    suspend fun invalidate(key: K)
    suspend fun clear()
}
