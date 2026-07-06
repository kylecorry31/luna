package com.kylecorry.luna.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * A coroutine safe LRU cache, backed by memory
 */
class MemoryLRUCache<K, T>(
    private val size: Int? = null,
    private val duration: Duration? = null
) : LRUCache<K, T> {

    private val values = mutableMapOf<K, T>()
    private val cachedAt = mutableMapOf<K, Instant>()
    private val stateMutex = Mutex()
    private val singleFlight = SingleFlight<K, T>(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    override suspend fun get(key: K): T? {
        return getCached(key, updateLastUsed = true)?.value
    }

    override suspend fun peek(key: K): T? {
        return getCached(key, updateLastUsed = false)?.value
    }

    private suspend fun getCached(key: K, updateLastUsed: Boolean): CachedValue<T>? {
        return stateMutex.withLock {
            if (hasValidCache(key)) {
                if (updateLastUsed) {
                    cachedAt[key] = Instant.now()
                }
                @Suppress("UNCHECKED_CAST")
                CachedValue(values[key] as T)
            } else {
                null
            }
        }
    }

    override suspend fun put(key: K, value: T) {
        stateMutex.withLock {
            values[key] = value
            cachedAt[key] = Instant.now()
            removeOldest()
        }
    }

    override suspend fun getOrPut(key: K, lookup: suspend () -> T): T {
        val cached = getCached(key, updateLastUsed = true)
        if (cached != null) {
            return cached.value
        }
        return singleFlight.getOrStart(key) {
            val raced = getCached(key, updateLastUsed = true)
            if (raced != null) {
                return@getOrStart raced.value
            }

            val newValue = lookup()
            put(key, newValue)
            newValue
        }
    }

    override suspend fun invalidate(key: K) {
        stateMutex.withLock {
            values.remove(key)
            cachedAt.remove(key)
        }
    }

    private fun removeOldest() {
        if (size == null) {
            return
        }
        if (values.size <= size) {
            return
        }
        val oldest = cachedAt.minByOrNull { it.value }?.key ?: return
        values.remove(oldest)
        cachedAt.remove(oldest)
    }

    private fun hasValidCache(key: K): Boolean {
        return values.containsKey(key) && !isCacheExpired(key)
    }

    private fun isCacheExpired(key: K): Boolean {
        if (duration == null) {
            return false
        }

        val time = cachedAt[key] ?: return false

        val now = Instant.now()
        return time >= now || Duration.between(time, now) > duration
    }

    private class CachedValue<T>(val value: T)
}
