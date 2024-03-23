package com.kylecorry.luna.cache

import com.kylecorry.luna.hooks.Hooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HooksTest {

    @Test
    fun effect() {
        val hooks = Hooks()
        var count1 = 0
        var count2 = 0

        // First call
        hooks.effect("test", 1, "Test") {
            count1++
        }

        // No change
        hooks.effect("test", 1, "Test") {
            count1++
        }

        assertEquals(1, count1)

        // Change
        hooks.effect("test", 2, "Test") {
            count1++
        }

        assertEquals(2, count1)
        assertEquals(0, count2)

        // New key
        hooks.effect("test2", 2, "Something else", 1f) {
            count2++
        }

        assertEquals(2, count1)
        assertEquals(1, count2)
    }

    @Test
    fun memo() {
        val hooks = Hooks()
        var count = 0
        var count2 = 0

        val value = hooks.memo("test", 1, "Test") {
            count++
            "Hello"
        }

        assertEquals("Hello", value)
        assertEquals(1, count)

        val value2 = hooks.memo("test", 1, "Test") {
            count++
            "Hello2"
        }

        assertEquals("Hello", value2)
        assertEquals(1, count)

        val value3 = hooks.memo("test", 2, "Test") {
            count++
            "Hello2"
        }

        assertEquals("Hello2", value3)
        assertEquals(2, count)
        assertEquals(0, count2)

        val value4 = hooks.memo("test2", 2, "Something else", 1f) {
            count2++
            "Hello3"
        }

        assertEquals("Hello3", value4)
        assertEquals(2, count)
        assertEquals(1, count2)
    }

    @Test
    fun memoWithNullValue() {
        val hooks = Hooks()
        var count = 0

        val value = hooks.memo<Int?>("test", 1, "Test") {
            count++
            null
        }

        assertNull(value)
        assertEquals(1, count)

        val value2 = hooks.memo<Int?>("test", 1, "Test") {
            count++
            null
        }

        assertNull(value2)
        assertEquals(1, count)
    }

    @Test
    fun memoWithNoDependencies() {
        val hooks = Hooks()
        var count = 0

        val value = hooks.memo("test") {
            count++
            1
        }

        assertEquals(1, value)
        assertEquals(1, count)

        val value2 = hooks.memo("test") {
            count++
            1
        }

        assertEquals(1, value2)
        assertEquals(1, count)
    }

    @Test
    fun effectWithNoDependencies() {
        val hooks = Hooks()
        var count = 0

        hooks.effect("test") {
            count++
        }

        hooks.effect("test") {
            count++
        }

        assertEquals(1, count)
    }

    @Test
    fun resetEffects() {
        val hooks = Hooks()
        var count = 0
        var count2 = 0

        hooks.effect("test", 1, "Test") {
            count++
        }

        hooks.resetEffects()

        hooks.effect("test", 1, "Test") {
            count++
        }

        hooks.effect("test2", 1, "Test") {
            count2++
        }

        assertEquals(2, count)
        assertEquals(1, count2)

        hooks.resetEffects(keys = listOf("test"))

        hooks.effect("test", 1, "Test") {
            count++
        }

        hooks.effect("test2", 1, "Test") {
            count2++
        }

        assertEquals(3, count)
        assertEquals(1, count2)

        hooks.resetEffects(except = listOf("test"))

        hooks.effect("test", 1, "Test") {
            count++
        }

        hooks.effect("test2", 1, "Test") {
            count2++
        }

        assertEquals(3, count)
        assertEquals(2, count2)
    }

    @Test
    fun resetMemos() {
        val hooks = Hooks()
        var count = 0
        var count2 = 0

        hooks.memo("test", 1, "Test") {
            count++
            "Hello"
        }

        hooks.resetMemos()

        hooks.memo("test", 1, "Test") {
            count++
            "Hello"
        }

        hooks.memo("test2", 1, "Test") {
            count2++
            "Hello"
        }

        assertEquals(2, count)
        assertEquals(1, count2)

        hooks.resetMemos(keys = listOf("test"))

        hooks.memo("test", 1, "Test") {
            count++
            "Hello"
        }

        hooks.memo("test2", 1, "Test") {
            count2++
            "Hello"
        }

        assertEquals(3, count)
        assertEquals(1, count2)

        hooks.resetMemos(except = listOf("test"))

        hooks.memo("test", 1, "Test") {
            count++
            "Hello"
        }

        hooks.memo("test2", 1, "Test") {
            count2++
            "Hello"
        }

        assertEquals(3, count)
        assertEquals(2, count2)
    }

    @Test
    fun state() = runBlocking {
        val delayTime = 100L
        var count = 0
        val hooks = Hooks(
            stateDispatcher = Dispatchers.Default,
            stateThrottleMs = 0,
            stateTriggerOnStart = false
        ) {
            count++
        }
        var state by hooks.state(1)

        hooks.startStateUpdates()

        state = 2
        delay(delayTime)
        assertEquals(1, count)

        // No change to state
        state = 2
        delay(delayTime)
        assertEquals(1, count)

        state = 1
        delay(delayTime)
        assertEquals(2, count)

        hooks.stopStateUpdates()

        // Updates are stopped
        state = 2
        delay(delayTime)
        assertEquals(2, count)
    }

    @Test
    fun stateThrottle() = runBlocking {
        val delayTime = 100L
        var count = 0
        val hooks = Hooks(
            stateDispatcher = Dispatchers.Default,
            stateThrottleMs = 50,
            stateTriggerOnStart = false
        ) {
            count++
        }
        var state by hooks.state(1)

        hooks.startStateUpdates()

        state = 1
        state = 3
        delay(delayTime)
        assertEquals(1, count)

        state = 2
        delay(delayTime)
        assertEquals(2, count)

        hooks.stopStateUpdates()
    }

    @Test
    fun stateWithInitialUpdate() = runBlocking {
        val delayTime = 100L
        var count = 0
        val hooks = Hooks(
            stateDispatcher = Dispatchers.Default,
            stateThrottleMs = 10,
            stateTriggerOnStart = true
        ) {
            count++
        }

        hooks.startStateUpdates()

        delay(delayTime)
        assertEquals(1, count)

        hooks.stopStateUpdates()
    }

    @Test
    fun stateWithoutInitialUpdate() = runBlocking {
        val delayTime = 100L
        var count = 0
        val hooks = Hooks(
            stateDispatcher = Dispatchers.Default,
            stateThrottleMs = 10,
            stateTriggerOnStart = false
        ) {
            count++
        }

        hooks.startStateUpdates()

        delay(delayTime)
        assertEquals(0, count)

        hooks.stopStateUpdates()
    }

}