package com.kylecorry.luna.cache

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration

/**
 * A coroutine safe LRU cache, backed by files under [baseFolderPath].
 */
class DiskLRUCache<K, T>(
    baseFolderPath: String,
    private val size: Int? = null,
    private val duration: Duration? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val async: Boolean = false,
    private val asyncScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
    private val getFilename: (K) -> String,
    private val serialize: (T) -> ByteArray,
    private val deserialize: (ByteArray) -> T
) : LRUCache<K, T> {

    private val baseFolder = File(baseFolderPath).canonicalFile
    private val stateMutex = Mutex()
    private val singleFlight = SingleFlight<K, T>(asyncScope)

    override suspend fun get(key: K): T? {
        val cached = getCached(key, updateLastUsed = true) ?: return null
        return deserialize(cached.value)
    }

    override suspend fun peek(key: K): T? {
        val cached = getCached(key, updateLastUsed = false) ?: return null
        return deserialize(cached.value)
    }

    private suspend fun getCached(key: K, updateLastUsed: Boolean): CachedValue<ByteArray>? {
        return stateMutex.withLock {
            withContext(dispatcher) {
                val file = getFile(key)
                if (hasValidCache(file)) {
                    if (updateLastUsed) {
                        file.setLastModified(System.currentTimeMillis())
                    }
                    CachedValue(file.readBytes())
                } else {
                    null
                }
            }
        }
    }

    override suspend fun put(key: K, value: T) {
        if (async) {
            asyncScope.launch {
                putSync(key, value)
            }
        } else {
            putSync(key, value)
        }
    }

    private suspend fun putSync(key: K, value: T) {
        val serialized = serialize(value)
        stateMutex.withLock {
            withContext(dispatcher) {
                val file = getFile(key)
                file.parentFile?.mkdirs()
                file.writeBytes(serialized)
                file.setLastModified(System.currentTimeMillis())
                removeOldest()
            }
        }
    }

    override suspend fun getOrPut(key: K, lookup: suspend () -> T): T {
        val cached = getCached(key, updateLastUsed = true)
        if (cached != null) {
            return deserialize(cached.value)
        }

        return singleFlight.getOrStart(key) {
            val raced = getCached(key, updateLastUsed = true)
            if (raced != null) {
                return@getOrStart deserialize(raced.value)
            }

            val newValue = lookup()
            put(key, newValue)
            newValue
        }
    }

    override suspend fun invalidate(key: K) {
        stateMutex.withLock {
            withContext(dispatcher) {
                getFile(key).delete()
            }
        }
    }

    private fun getFile(key: K): File {
        val file = File(baseFolder, getFilename(key)).canonicalFile
        require(file == baseFolder || file.path.startsWith("${baseFolder.path}${File.separator}")) {
            "Cache key must be relative to the base folder"
        }
        return file
    }

    private fun hasValidCache(file: File): Boolean {
        return file.exists() && file.isFile && !isCacheExpired(file)
    }

    private fun isCacheExpired(file: File): Boolean {
        val duration = duration ?: return false
        val now = System.currentTimeMillis()
        val cachedAt = file.lastModified()
        return cachedAt >= now || now - cachedAt > duration.toMillis()
    }

    private fun removeOldest() {
        val size = size ?: return
        val files = baseFolder
            .walkTopDown()
            .filter(File::isFile)
            .sortedBy(File::lastModified)
            .toList()

        files.take((files.size - size).coerceAtLeast(0)).forEach {
            it.delete()
        }
    }

    private class CachedValue<T>(val value: T)
}
