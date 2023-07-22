package com.kylecorry.tasks

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TaskRunnerTest {

    @Test
    fun testReplace() = runBlocking {
        val runner = TaskRunner()

        var task1Complete = false
        var task2Complete = false

        runner.replace {
            delay(50)
            task1Complete = true
        }

        runner.replace {
            delay(50)
            task2Complete = true
        }

        delay(150)

        assertEquals(false, task1Complete)
        assertEquals(true, task2Complete)
    }

    @Test
    fun testSkipIfRunning() = runBlocking {
        val runner = TaskRunner()

        var task1Complete = false
        var task2Complete = false

        runner.skipIfRunning {
            delay(50)
            task1Complete = true
        }

        runner.skipIfRunning {
            delay(50)
            task2Complete = true
        }

        delay(150)

        assertEquals(true, task1Complete)
        assertEquals(false, task2Complete)
    }

    @Test
    fun testCancel() = runBlocking {
        val runner = TaskRunner()

        var task1Complete = false

        runner.skipIfRunning {
            delay(50)
            task1Complete = true
        }
        runner.cancel()

        delay(150)

        assertEquals(false, task1Complete)
    }

    @Test
    fun testEnqueue() = runBlocking {
        val runner = TaskRunner(1)

        var task1Complete = false
        var task2Complete = false
        var task3Complete = false

        runner.enqueue {
            delay(50)
            task1Complete = true
        }

        runner.enqueue {
            delay(50)
            task2Complete = true
        }

        runner.enqueue {
            delay(50)
            task3Complete = true
        }

        delay(200)

        assertEquals(true, task1Complete)
        assertEquals(true, task2Complete)
        assertEquals(false, task3Complete)
    }

    @Test
    fun testCancelWithQueue() = runBlocking {
        val runner = TaskRunner(1)

        var task1Complete = false
        var task2Complete = false
        var task3Complete = false

        runner.enqueue {
            delay(50)
            task1Complete = true
        }

        runner.enqueue {
            delay(50)
            task2Complete = true
        }

        runner.enqueue {
            delay(50)
            task3Complete = true
        }

        runner.cancel()

        delay(200)

        assertEquals(false, task1Complete)
        assertEquals(false, task2Complete)
        assertEquals(false, task3Complete)
    }

    @Test
    fun testRunWithExceptionNotIgnored() = runBlocking {
        val runner = TaskRunner(2)

        var task1Complete = false
        var task2Complete = false
        var task3Complete = false

        runner.enqueue {
            delay(50)
            task1Complete = true
        }

        runner.enqueue {
            delay(50)
            task2Complete = true
            throw Exception("Test")
        }

        runner.enqueue {
            delay(50)
            task3Complete = true
        }

        delay(200)

        assertEquals(true, task1Complete)
        assertEquals(true, task2Complete)
        assertEquals(false, task3Complete)
    }

    @Test
    fun testRunWithExceptionsIgnored() = runBlocking {
        val runner = TaskRunner(2, ignoreExceptions = true)

        var task1Complete = false
        var task2Complete = false
        var task3Complete = false

        runner.enqueue {
            delay(50)
            task1Complete = true
        }

        runner.enqueue {
            delay(50)
            task2Complete = true
            throw Exception("Test")
        }

        runner.enqueue {
            delay(50)
            task3Complete = true
        }

        delay(200)

        assertEquals(true, task1Complete)
        assertEquals(true, task2Complete)
        assertEquals(true, task3Complete)
    }

}