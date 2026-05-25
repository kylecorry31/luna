package com.kylecorry.luna.commands.generic

interface CoroutineCommand<T> {

    suspend fun execute(value: T)

}
