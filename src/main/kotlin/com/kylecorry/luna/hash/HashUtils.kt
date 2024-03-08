package com.kylecorry.luna.hash

object HashUtils {

    fun hash(vararg values: Any?): Int {
        var hash = 0
        for (value in values) {
            hash = hash * 31 + (value?.hashCode() ?: 0)
        }
        return hash
    }

}