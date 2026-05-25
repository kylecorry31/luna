package com.kylecorry.luna.concurrency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

class SafeExecutorTest {

    @Test
    fun testExecuteRunsCommand() {
        val executionCount = AtomicInteger(0)
        val safeExecutor = SafeExecutor(Executor { it.run() })

        safeExecutor.execute {
            executionCount.incrementAndGet()
        }

        assertEquals(1, executionCount.get())
    }

    @Test
    fun testExecuteInvokesErrorHandlerWhenCommandThrows() {
        var captured: Throwable? = null
        val expected = IllegalStateException("boom")
        val safeExecutor = SafeExecutor(
            delegate = Executor { it.run() },
            onError = { captured = it }
        )

        safeExecutor.execute {
            throw expected
        }

        assertSame(expected, captured)
    }

    @Test
    fun testExecuteDoesNotInvokeErrorHandlerWhenCommandSucceeds() {
        val errorCount = AtomicInteger(0)
        val safeExecutor = SafeExecutor(
            delegate = Executor { it.run() },
            onError = { errorCount.incrementAndGet() }
        )

        safeExecutor.execute {
            // no-op
        }

        assertEquals(0, errorCount.get())
    }

    @Test
    fun testExecuteCatchesThrowables() {
        var captured: Throwable? = null
        val expected = AssertionError("fatal")
        val safeExecutor = SafeExecutor(
            delegate = Executor { it.run() },
            onError = { captured = it }
        )

        safeExecutor.execute {
            throw expected
        }

        assertSame(expected, captured)
        assertNull(captured?.cause)
    }
}
