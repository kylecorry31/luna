package com.kylecorry.luna.cache

import kotlinx.coroutines.Dispatchers
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
    private val useSingleLock: Boolean = false,
    private val getFilename: (K) -> String,
    private val serialize: (T) -> ByteArray,
    private val deserialize: (ByteArray) -> T
): LRUCache<K, T> {

    private val baseFolder = File(baseFolderPath).canonicalFile
    private val mutexes = mutableMapOf<K, Mutex>()
    private val stateMutex = Mutex()
    private val mutex = Mutex()

    override suspend fun get(key: K): T? {
        return getLock(key).withLock {
            stateMutex.withLock {
                withContext(Dispatchers.IO) {
                    val file = getFile(key)
                    if (hasValidCache(file)) {
                        file.setLastModified(System.currentTimeMillis())
                        deserialize(file.readBytes())
                    } else {
                        null
                    }
                }
            }
        }
    }

    override suspend fun put(key: K, value: T) {
        getLock(key).withLock {
            stateMutex.withLock {
                withContext(Dispatchers.IO) {
                    val file = getFile(key)
                    file.parentFile?.mkdirs()
                    file.writeBytes(serialize(value))
                    file.setLastModified(System.currentTimeMillis())
                    removeOldest()
                }
            }
        }
    }

    override suspend fun getOrPut(key: K, lookup: suspend () -> T): T {
        return getLock(key).withLock operation@{
            val cached = stateMutex.withLock {
                withContext(Dispatchers.IO) {
                    val file = getFile(key)
                    if (hasValidCache(file)) {
                        file.setLastModified(System.currentTimeMillis())
                        CachedValue(deserialize(file.readBytes()))
                    } else {
                        null
                    }
                }
            }
            if (cached != null) {
                return@operation cached.value
            }

            val newValue = lookup()
            stateMutex.withLock {
                withContext(Dispatchers.IO) {
                    val file = getFile(key)
                    file.parentFile?.mkdirs()
                    file.writeBytes(serialize(newValue))
                    file.setLastModified(System.currentTimeMillis())
                    removeOldest()
                }
            }
            newValue
        }
    }

    override suspend fun invalidate(key: K) {
        getLock(key).withLock {
            stateMutex.withLock {
                withContext(Dispatchers.IO) {
                    getFile(key).delete()
                }
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
