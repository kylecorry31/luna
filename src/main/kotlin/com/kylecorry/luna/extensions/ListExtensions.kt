package com.kylecorry.luna.extensions

fun <T> List<T>.filterIndices(indices: List<Int>): List<T> {
    return filterIndexed { index, _ -> indices.contains(index) }
}