package com.kylecorry.luna.extensions

inline fun tryOrNothing(block: () -> Unit) {
    try {
        block()
    } catch (_: Exception) {
    }
}

inline fun tryOrLog(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

inline fun <T> tryOrDefault(default: T, block: () -> T): T {
    return try {
        block()
    } catch (e: Exception) {
        default
    }
}