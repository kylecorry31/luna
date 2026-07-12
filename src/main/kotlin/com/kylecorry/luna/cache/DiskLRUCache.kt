package com.kylecorry.luna.cache

import com.kylecorry.luna.concurrency.SingleFlight
import com.kylecorry.luna.serialization.ISerializer
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
    private val async: Boolean = false,
    private val asyncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val getFilename: (K) -> String,
    private val serializer: ISerializer<T>
) : Cache<K, T> {

    private val baseFolder = File(baseFolderPath).canonicalFile
    private val stateMutex = Mutex()
    private val singleFlight = SingleFlight<K, T>()
    private val tempFilePrefix = ".luna-cache-"
    private val tempFileSuffix = ".tmp"

    override suspend fun get(key: K): T? {
        return getCached(key, updateLastUsed = true)?.value
    }

    override suspend fun peek(key: K): T? {
        return getCached(key, updateLastUsed = false)?.value
    }

    private suspend fun getCached(key: K, updateLastUsed: Boolean): CachedValue<T>? = withTempFile { tmp ->
        val isCached = stateMutex.withLock {
            withContext(Dispatchers.IO) {
                val file = getFile(key)
                if (hasValidCache(file)) {
                    if (updateLastUsed) {
                        file.setLastModified(System.currentTimeMillis())
                    }
                    file.copyTo(tmp, true)
                    true
                } else {
                    false
                }
            }
        }

        if (!isCached) {
            return@withTempFile null
        }

        withContext(Dispatchers.IO) {
            CachedValue(tmp.inputStream().use { serializer.deserialize(it) })
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

    private suspend fun putSync(key: K, value: T) = withTempFile { tmp ->
        withContext(Dispatchers.IO) {
            tmp.outputStream().use { serializer.serialize(value, it) }
        }
        stateMutex.withLock {
            withContext(Dispatchers.IO) {
                val file = getFile(key)
                file.parentFile?.mkdirs()
                tmp.copyTo(file, true)
                file.setLastModified(System.currentTimeMillis())
                removeOldest()
            }
        }
    }

    override suspend fun getOrPut(key: K, lookup: suspend () -> T): T {
        val cached = getCached(key, updateLastUsed = true)
        if (cached != null) {
            return cached.value
        }

        return singleFlight.invoke(key) {
            val raced = getCached(key, updateLastUsed = true)
            if (raced != null) {
                return@invoke raced.value
            }

            val newValue = lookup()
            put(key, newValue)
            newValue
        }
    }

    override suspend fun invalidate(key: K) {
        stateMutex.withLock {
            withContext(Dispatchers.IO) {
                getFile(key).delete()
            }
        }
    }

    override suspend fun clear() {
        stateMutex.withLock {
            withContext(Dispatchers.IO) {
                if (!baseFolder.exists()) {
                    return@withContext
                }

                baseFolder.walkBottomUp()
                    .filter { it != baseFolder }
                    .filter { it.isDirectory || (it.isFile && !isTempFile(it)) }
                    .forEach { it.delete() }
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

    private suspend inline fun <T> withTempFile(crossinline block: suspend (file: File) -> T): T {
        val tmp = withContext(Dispatchers.IO) {
            File.createTempFile(tempFilePrefix, tempFileSuffix)
        }
        return try {
            block(tmp)
        } finally {
            tmp.delete()
        }
    }

    private fun isTempFile(file: File): Boolean {
        return file.name.startsWith(tempFilePrefix) && file.name.endsWith(tempFileSuffix)
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
            .filter { it.isFile && !isTempFile(it) }
            .sortedBy(File::lastModified)
            .toList()

        files.take((files.size - size).coerceAtLeast(0)).forEach {
            it.delete()
        }
    }

    private class CachedValue<T>(val value: T)
}
