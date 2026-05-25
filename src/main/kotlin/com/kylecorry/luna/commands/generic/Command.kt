package com.kylecorry.luna.commands.generic

interface Command<T> {
    fun execute(value: T)
}
