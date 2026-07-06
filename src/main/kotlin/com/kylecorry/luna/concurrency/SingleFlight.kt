package com.kylecorry.luna.concurrency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SingleFlight<K, V>(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val lock = Mutex()
    private val inFlight = mutableMapOf<K, Deferred<V>>()

    suspend fun invoke(key: K, block: suspend () -> V): V {
        val existing = lock.withLock {
            inFlight[key]
        }

        if (existing != null) {
            return existing.await()
        }

        val deferred = scope.async(start = CoroutineStart.LAZY) {
            block()
        }

        val actual = lock.withLock {
            val raced = inFlight[key]

            if (raced != null) {
                raced
            } else {
                inFlight[key] = deferred
                deferred
            }
        }

        return try {
            actual.await()
        } finally {
            lock.withLock {
                if (inFlight[key] === actual) {
                    inFlight.remove(key)
                }
            }
        }
    }
}