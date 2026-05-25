package com.kylecorry.luna.concurrency.locks

class NoLock : Lock {
    override fun <T> withLock(key: Any?, block: () -> T): T {
        return block()
    }
}
