package com.kylecorry.luna.commands.generic

interface CoroutineValueCommand<T, S> {

    suspend fun execute(value: T): S

}
