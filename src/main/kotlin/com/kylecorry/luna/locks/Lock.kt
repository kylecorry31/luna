package com.kylecorry.luna.locks

interface Lock {
    fun <T> withLock(key: Any?, block: () -> T): T
}
