package com.kylecorry.luna.text

import java.util.*

fun String.capitalizeCompat(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

fun String.capitalizeWords(): String {
    val words = split(" ")
    return words.joinToString(" ") { it.capitalizeCompat() }
}

fun String.toDoubleCompat(): Double? {
    val asDouble = try {
        this.replace(",", ".").toDoubleOrNull()
    } catch (e: Exception) {
        null
    }
    asDouble ?: return null
    if (asDouble.isNaN() || asDouble.isInfinite()) {
        return null
    }
    return asDouble
}

fun String.toFloatCompat(): Float? {
    val asFloat = try {
        this.replace(",", ".").toFloatOrNull()
    } catch (e: Exception) {
        null
    }
    asFloat ?: return null
    if (asFloat.isNaN() || asFloat.isInfinite()) {
        return null
    }
    return asFloat
}

fun String.toIntCompat(): Int? {
    return try {
        this.replace(",", ".").toIntOrNull()
    } catch (e: Exception) {
        null
    }
}

fun String.toLongCompat(): Long? {
    return try {
        this.replace(",", ".").toLongOrNull()
    } catch (e: Exception) {
        null
    }
}