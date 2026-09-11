package com.kylecorry.luna.concurrency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

suspend fun <T> Flow<T>.read(scope: CoroutineScope = CoroutineScope(Dispatchers.Default)): T =
    suspendCancellableCoroutine { cont ->
        var job: Job? = null
        job = scope.launch {
            collectLatest { value ->
                cont.resume(value)
                job?.cancel()
            }
        }

        cont.invokeOnCancellation {
            job.cancel()
        }
    }

/**
 * A [callbackFlow] which is shared by all collectors, so the callback is registered once across
 * all consumers.
 *
 * @param replay the number of values replayed to new collectors
 * @param scope the scope the callback is registered in
 * @param started the strategy controlling when the callback is registered and unregistered
 */
fun <T> sharedCallbackFlow(
    replay: Int = 0,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    started: SharingStarted = SharingStarted.WhileSubscribed(),
    block: suspend ProducerScope<T>.() -> Unit
): Flow<T> = callbackFlow(block).shareIn(scope, started, replay)

fun timer(period: Duration, initialDelay: Duration = Duration.ZERO) = flow {
    delay(initialDelay.toMillis().milliseconds)
    while (true) {
        emit(Unit)
        delay(period.toMillis().milliseconds)
    }
}