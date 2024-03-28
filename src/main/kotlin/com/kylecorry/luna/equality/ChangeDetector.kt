package com.kylecorry.luna.equality

interface ChangeDetector {
    fun hasChanges(values: Array<out Any?>): Boolean
    fun reset()
}