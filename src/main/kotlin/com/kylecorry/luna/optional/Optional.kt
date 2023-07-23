package com.kylecorry.luna.optional

class Optional<T> private constructor(private val value: T?) {

    val isEmpty: Boolean
        get() = value == null

    fun get(): T {
        return value!!
    }

    fun <U> map(fn: (T) -> U): Optional<U> {
        return if (isEmpty) {
            empty()
        } else {
            of(fn(value!!))
        }
    }

    fun ifPresent(fn: (T) -> Unit) {
        if (!isEmpty) {
            fn(value!!)
        }
    }

    companion object {
        fun <T> empty(): Optional<T> {
            return Optional(null)
        }

        fun <T> of(value: T): Optional<T> {
            return Optional(value)
        }
    }
}
