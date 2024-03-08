package com.kylecorry.luna.cache

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

}