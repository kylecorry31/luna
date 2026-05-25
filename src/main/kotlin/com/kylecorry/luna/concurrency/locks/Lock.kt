package com.kylecorry.luna.concurrency.locks

interface Lock {
    fun <T> withLock(key: Any?, block: () -> T): T
}
