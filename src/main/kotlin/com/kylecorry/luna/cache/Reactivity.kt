package com.kylecorry.luna.cache

class Reactivity {

    private val effects = mutableMapOf<String, StateEffect>()
    private val effectLock = Any()

    private val memos = mutableMapOf<String, MemoizedValue<*>>()
    private val memoLock = Any()

    /**
     * Run an effect only if the state changes
     * @param key The key for the effect (should be unique for each effect)
     * @param values The values that the effect depends on
     * @param action The action to run if the state changes
     */
    fun effect(key: String, vararg values: Any?, action: () -> Unit) {
        val effect = synchronized(effectLock) {
            effects.getOrPut(key) { StateEffect() }
        }
        effect.runIfChanged(*values, action = action)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> memo(key: String, vararg values: Any?, value: () -> T): T {
        val memo = synchronized(memoLock) {
            memos.getOrPut(key) { MemoizedValue<T>() }
        } as MemoizedValue<T>
        return memo.getOrPut(*values, value = value)
    }

}