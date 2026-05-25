package com.kylecorry.luna.commands

interface CoroutineValueCommand<T> {

    suspend fun execute(): T

}
