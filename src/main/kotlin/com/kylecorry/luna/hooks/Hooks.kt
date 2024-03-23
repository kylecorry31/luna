package com.kylecorry.luna.hooks

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * A set of hooks for managing state
 * @param stateDispatcher The dispatcher to use for state updates (default is main)
 * @param stateThrottleMs The time to throttle state updates (default is 20ms)
 * @param stateTriggerOnStart True if the state should trigger on start (default is true)
 * @param stateOnChange The action to run when the state changes (default is no action)
 */
class Hooks(
    stateDispatcher: CoroutineContext = Dispatchers.Main,
    stateThrottleMs: Long = 20,
    stateTriggerOnStart: Boolean = true,
    stateOnChange: () -> Unit = {}
) {

    private val effects = mutableMapOf<String, Effect>()
    private val effectLock = Any()

    private val memos = mutableMapOf<String, MemoizedValue<*>>()
    private val memoLock = Any()

    private val stateManager = StateManager(stateDispatcher, stateThrottleMs, stateTriggerOnStart, stateOnChange)

    /**
     * Run an effect only if the state changes
     * @param key The key for the effect (should be unique for each effect)
     * @param values The values that the effect depends on
     * @param action The action to run if the state changes
     */
    fun effect(key: String, vararg values: Any?, action: () -> Unit) {
        val effect = synchronized(effectLock) {
            effects.getOrPut(key) { Effect() }
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

    /**
     * Create a new tracked state
     * @param initialValue The initial value of the state
     * @return The state
     */
    fun <T> state(initialValue: T): State<T> {
        return stateManager.state(initialValue)
    }

    /**
     * Resets effects
     * @param keys The keys to reset, if null all effects are reset
     * @param except The keys to not reset
     */
    fun resetEffects(keys: List<String>? = null, except: List<String> = emptyList()) {
        synchronized(effectLock) {
            if (keys == null) {
                effects.keys.removeAll { it !in except }
            } else {
                effects.keys.removeAll { it !in except && it in keys }
            }
        }
    }

    /**
     * Resets memos
     * @param keys The keys to reset, if null all memos are reset
     * @param except The keys to not reset
     */
    fun resetMemos(keys: List<String>? = null, except: List<String> = emptyList()) {
        synchronized(memoLock) {
            if (keys == null) {
                memos.keys.removeAll { it !in except }
            } else {
                memos.keys.removeAll { it !in except && it in keys }
            }
        }
    }

    /**
     * Starts state updates
     */
    fun startStateUpdates() {
        stateManager.start()
    }

    /**
     * Stops state updates
     */
    fun stopStateUpdates() {
        stateManager.stop()
    }

}