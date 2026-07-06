package com.kylecorry.luna.cache

class MultiTierCache<K, T>(vararg val caches: Cache<K, T>) : Cache<K, T> {
    init {
        require(caches.isNotEmpty()) { "At least one cache is required" }
    }

    override suspend fun get(key: K): T? {
        for (i in caches.indices) {
            val value = caches[i].get(key) ?: continue
            // Populate the higher tier caches
            for (j in 0..<i) {
                caches[j].put(key, value)
            }
            return value
        }
        return null
    }

    override suspend fun peek(key: K): T? {
        for (cache in caches) {
            return cache.peek(key) ?: continue
        }
        return null
    }

    override suspend fun put(key: K, value: T) {
        for (cache in caches) {
            cache.put(key, value)
        }
    }

    override suspend fun getOrPut(key: K, lookup: suspend () -> T): T {
        return getOrPut(key, 0, lookup)
    }

    override suspend fun invalidate(key: K) {
        for (cache in caches) {
            cache.invalidate(key)
        }
    }

    override suspend fun clear() {
        for (cache in caches) {
            cache.clear()
        }
    }

    private suspend fun getOrPut(key: K, cacheIndex: Int, lookup: suspend () -> T): T {
        if (cacheIndex == caches.lastIndex) {
            return caches[cacheIndex].getOrPut(key, lookup)
        }

        return caches[cacheIndex].getOrPut(key) {
            getOrPut(key, cacheIndex + 1, lookup)
        }
    }
}
