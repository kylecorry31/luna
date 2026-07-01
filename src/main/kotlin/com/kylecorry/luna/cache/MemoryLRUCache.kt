package com.kylecorry.luna.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * A coroutine safe LRU cache, backed by memory
 */
class MemoryLRUCache<K, T>(
    private val size: Int? = null,
    private val duration: Duration? = null,
    private val useSingleLock: Boolean = false
) : LRUCache<K, T> {

    private val values = mutableMapOf<K, T>()
    private val cachedAt = mutableMapOf<K, Instant>()
    private val mutexes = mutableMapOf<K, Mutex>()
    private val stateMutex = Mutex()
    private val mutex = Mutex()

    override suspend fun get(key: K): T? {
        return getLock(key).withLock {
            stateMutex.withLock {
                if (hasValidCache(key)) {
                    cachedAt[key] = Instant.now()
                    values[key]
                } else {
                    null
                }
            }
        }
    }

    override suspend fun put(key: K, value: T) {
        getLock(key).withLock {
            stateMutex.withLock {
                values[key] = value
                cachedAt[key] = Instant.now()
                removeOldest()
            }
        }
    }

    override suspend fun getOrPut(key: K, lookup: suspend () -> T): T {
        return getLock(key).withLock operation@{
            stateMutex.withLock {
                if (hasValidCache(key)) {
                    cachedAt[key] = Instant.now()
                    @Suppress("UNCHECKED_CAST")
                    return@operation values[key] as T
                }
            }
            val newValue = lookup()
            stateMutex.withLock {
                values[key] = newValue
                cachedAt[key] = Instant.now()
                removeOldest()
            }
            newValue
        }
    }

    override suspend fun invalidate(key: K) {
        getLock(key).withLock {
            stateMutex.withLock {
                values.remove(key)
                cachedAt.remove(key)
                mutexes.remove(key)
            }
        }
    }

    private suspend fun getLock(key: K): Mutex {
        return if (useSingleLock) {
            mutex
        } else {
            stateMutex.withLock {
                mutexes.computeIfAbsent(key) { Mutex() }
            }
        }
    }

    private suspend fun removeOldest() {
        if (size == null) {
            return
        }
        if (values.size <= size) {
            return
        }
        val oldest = cachedAt.minByOrNull { it.value }?.key ?: return
        values.remove(oldest)
        cachedAt.remove(oldest)
        mutexes.remove(oldest)
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

}
