package com.kylecorry.luna.hooks

import com.kylecorry.luna.timer.CoroutineTimer
import kotlin.coroutines.CoroutineContext

class StateManager(
    observeOn: CoroutineContext,
    private var throttleTimeMs: Long,
    private val shouldTriggerOnStart: Boolean = true,
    private val onChange: () -> Unit
) {
    private var lastUpdateTime = 0L

    private val timer = CoroutineTimer(observeOn = observeOn) {
        synchronized(lock) {
            hasPendingUpdate = false
            lastUpdateTime = System.currentTimeMillis()
        }
        onChange()
    }

    private var isRunning = false
    private var hasPendingUpdate = false
    private val lock = Any()


    fun start() {
        synchronized(lock) {
            isRunning = true
            if (shouldTriggerOnStart) {
                hasPendingUpdate = true
                timer.once(0)
            } else {
                hasPendingUpdate = false
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            isRunning = false
            hasPendingUpdate = false
            timer.stop()
        }
    }

    fun <T> state(initialValue: T): State<T> {
        return State(initialValue, this::scheduleChange)
    }

    fun setThrottle(milliseconds: Long){
        throttleTimeMs = milliseconds
    }

    private fun scheduleChange() {
        synchronized(lock) {
            if (hasPendingUpdate || !isRunning) {
                // There's already an update scheduled or it is not running
                return
            }

            // Otherwise, an update needs to be scheduled, ensuring that it's throttled
            val timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTime
            val timeToNextUpdate = if (timeSinceLastUpdate < throttleTimeMs) {
                throttleTimeMs - timeSinceLastUpdate
            } else {
                0
            }.coerceIn(0, throttleTimeMs)

            hasPendingUpdate = true
            timer.once(timeToNextUpdate)
        }
    }
}