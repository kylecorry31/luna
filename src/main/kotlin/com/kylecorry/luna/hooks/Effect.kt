package com.kylecorry.luna.hooks

import com.kylecorry.luna.hash.HashChangeDetector

/**
 * An effect that runs an action if the state has changed (similar to the effect hook in React)
 */
class Effect {

    private val lock = Any()
    private val changeDetector = HashChangeDetector()

    /**
     * Run an action if the values have changed
     * @param dependencies the dependencies to check for changes (uses hash code)
     * @param action the action to run if the values have changed
     */
    fun runIfChanged(vararg dependencies: Any?, action: () -> Unit): Unit = synchronized(lock) {
        if (changeDetector.hasChanges(dependencies)) {
            action()
        }
    }

    fun reset(): Unit = synchronized(lock) {
        changeDetector.reset()
    }

}