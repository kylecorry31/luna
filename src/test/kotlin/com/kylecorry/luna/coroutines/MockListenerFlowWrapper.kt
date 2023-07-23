package com.kylecorry.luna.coroutines

internal class MockListenerFlowWrapper(replay: Boolean) : ListenerFlowWrapper<Int>(replay) {

    var isRunning = false
        private set

    var timesStarted = 0
        private set

    var count = 0
        private set

    override fun start() {
        isRunning = true
        timesStarted++
    }

    override fun stop() {
        isRunning = false
    }

    fun tick() {
        if (!isRunning) return
        emit(++count)
    }

}