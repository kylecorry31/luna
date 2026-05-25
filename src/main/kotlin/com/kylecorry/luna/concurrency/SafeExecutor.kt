package com.kylecorry.luna.concurrency

import java.util.concurrent.Executor
import java.util.concurrent.Executors

class SafeExecutor(
    private val delegate: Executor,
    private val onError: (Throwable) -> Unit = defaultThrowableHandler
) : Executor {
    override fun execute(command: Runnable) {
        delegate.execute {
            try {
                command.run()
            } catch (e: Throwable) {
                onError(e)
            }
        }
    }

    companion object {

        private val defaultThrowableHandler: (Throwable) -> Unit = {
            it.printStackTrace()
        }

        fun newSingleThreadExecutor(onError: (Throwable) -> Unit = defaultThrowableHandler): SafeExecutor {
            return SafeExecutor(Executors.newSingleThreadExecutor(), onError)
        }
    }
}