package com.kylecorry.luna.cache

import com.kylecorry.luna.hash.HashUtils

/**
 * An effect that runs an action if the state has changed (similar to the effect hook in React)
 */
class StateEffect {

    private val lock = Any()
    private var cachedHash: Int? = null

    /**
     * Run an action if the values have changed
     * @param values the values to check for changes (uses hash code)
     * @param action the action to run if the values have changed
     */
    fun runIfChanged(vararg values: Any?, action: () -> Unit): Unit = synchronized(lock) {
        val hash = HashUtils.hash(*values)
        if (cachedHash == null || cachedHash != hash) {
            action()
            cachedHash = hash
        }
    }

    fun reset(): Unit = synchronized(lock) {
        cachedHash = null
    }

}