package com.kylecorry.luna.timer

import java.time.Duration

interface ITimer {

    fun interval(period: Duration, initialDelay: Duration = Duration.ZERO)
    fun interval(periodMillis: Long, initialDelayMillis: Long = 0L)

    fun once(delay: Duration)
    fun once(delayMillis: Long)

    fun stop()

    fun isRunning(): Boolean

}