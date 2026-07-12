package com.kylecorry.luna.concurrency

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SingleFlight<K, V> {

    private val lock = Mutex()
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun invoke(key: K, block: suspend () -> V): V {
        val candidate = CompletableDeferred<V>()
        val actual = lock.withLock {
            inFlight.getOrPut(key) { candidate }
        }

        if (actual !== candidate) {
            return actual.await()
        }

        return try {
            block().also(candidate::complete)
        } catch (throwable: Throwable) {
            candidate.completeExceptionally(throwable)
            throw throwable
        } finally {
            lock.withLock {
                if (inFlight[key] === candidate) {
                    inFlight.remove(key)
                }
            }
        }
    }
}
