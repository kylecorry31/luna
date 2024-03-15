package com.kylecorry.luna.cache

class Hooks {

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

    /**
     * Memoize a value
     * @param key The key for the memo (should be unique for each memo)
     * @param values The values that the memo depends on
     * @param value The value function that should be memoized
     * @return The memoized value
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> memo(key: String, vararg values: Any?, value: () -> T): T {
        val memo = synchronized(memoLock) {
            memos.getOrPut(key) { MemoizedValue<T>() }
        } as MemoizedValue<T>
        return memo.getOrPut(*values, value = value)
    }

    fun resetEffects(except: List<String> = emptyList()) {
        synchronized(effectLock) {
            effects.keys.removeAll { it !in except }
        }
    }

    fun resetMemos(except: List<String> = emptyList()) {
        synchronized(memoLock) {
            memos.keys.removeAll { it !in except }
        }
    }

}