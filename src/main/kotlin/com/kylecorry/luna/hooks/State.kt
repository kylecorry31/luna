package com.kylecorry.luna.hooks

import kotlin.reflect.KProperty

class State<T>(
    initialValue: T,
    private val onChange: () -> Unit
) {

    private var value: T = initialValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val hasChanges = this.value != value
        this.value = value
        if (hasChanges) {
            onChange()
        }
    }

}