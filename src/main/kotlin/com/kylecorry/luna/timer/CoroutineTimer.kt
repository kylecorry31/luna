package com.kylecorry.luna.timer

import com.kylecorry.luna.coroutines.CoroutineQueueRunner
import kotlinx.coroutines.*
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * A timer based on coroutine delays (based on uptime)
 * @param scope The scope to run the timer on
 * @param observeOn The context to observe the action on
 * @param actionBehavior The behavior to use when the action is already running when the timer is triggered (periodic only).
 * @param action The action to run
 */
class CoroutineTimer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val observeOn: CoroutineContext = Dispatchers.Main,
    private val actionBehavior: TimerActionBehavior = TimerActionBehavior.Wait,
    private val action: suspend () -> Unit
) : ITimer {

    private val timerRunner = CoroutineQueueRunner(dispatcher = observeOn)
    private val actionRunner = CoroutineQueueRunner(dispatcher = observeOn)

    private var _isRunning = false

    private suspend fun run() {
        if (actionBehavior == TimerActionBehavior.Wait) {
            withContext(observeOn) {
                action()
            }
        } else {
            // Run it in the background
            scope.launch {
                if (actionBehavior == TimerActionBehavior.Skip) {
                    actionRunner.skipIfRunning { action() }
                } else if (actionBehavior == TimerActionBehavior.Replace) {
                    actionRunner.replace { action() }
                }
            }
        }
    }

    override fun interval(period: Duration, initialDelay: Duration) {
        interval(period.toMillis(), initialDelay.toMillis())
    }

    override fun interval(periodMillis: Long, initialDelayMillis: Long) {
        _isRunning = true
        scope.launch {
            timerRunner.replace {
                if (initialDelayMillis > 0) {
                    delay(initialDelayMillis)
                }
                while (isRunning()) {
                    run()
                    if (periodMillis > 0) {
                        delay(periodMillis)
                    }
                }

            }
        }
    }

    override fun once(delay: Duration) {
        once(delay.toMillis())
    }

    override fun once(delayMillis: Long) {
        _isRunning = true
        scope.launch {
            timerRunner.replace {
                if (delayMillis > 0) {
                    delay(delayMillis)
                }
                withContext(observeOn) {
                    action()
                }
            }
        }
    }

    override fun stop() {
        _isRunning = false
        timerRunner.cancel()
        actionRunner.cancel()
    }

    override fun isRunning(): Boolean {
        return _isRunning
    }
}