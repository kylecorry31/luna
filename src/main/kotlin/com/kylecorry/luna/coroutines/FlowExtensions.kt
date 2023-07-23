package com.kylecorry.luna.coroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import java.time.Duration
import kotlin.coroutines.resume

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

fun timer(period: Duration, initialDelay: Duration = Duration.ZERO) = flow {
    delay(initialDelay.toMillis())
    while (true) {
        emit(Unit)
        delay(period.toMillis())
    }
}