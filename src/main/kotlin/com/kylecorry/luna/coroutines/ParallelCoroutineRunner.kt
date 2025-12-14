package com.kylecorry.luna.coroutines

class ParallelCoroutineRunner(private val maxParallel: Int = 8) {

    suspend fun run(coroutines: List<suspend () -> Any>) {
        Parallel.forEach(coroutines, maxParallel)
    }

    suspend fun <R> run(items: List<R>, coroutine: suspend (R) -> Unit) {
        Parallel.forEach(items, maxParallel, coroutine)
    }

    suspend fun <T> map(coroutines: List<suspend () -> T>): List<T> {
        return Parallel.map(coroutines, maxParallel)
    }

    suspend fun <T> mapFunctions(functions: List<() -> T>): List<T> {
        return Parallel.mapFunctions(functions, maxParallel)
    }

    suspend fun <R, T> map(items: List<R>, coroutine: suspend (R) -> T): List<T> {
        return Parallel.map(items, maxParallel, coroutine)
    }

}