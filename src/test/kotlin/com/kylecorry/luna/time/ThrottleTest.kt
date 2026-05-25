package com.kylecorry.luna.time

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThrottleTest {

    @Test
    fun firstCallIsNotThrottled() {
        val throttle = Throttle(50)

        assertFalse(throttle.isThrottled())
    }

    @Test
    fun secondImmediateCallIsThrottled() {
        val throttle = Throttle(50)

        throttle.isThrottled()

        assertTrue(throttle.isThrottled())
    }

    @Test
    fun callAfterThrottleWindowIsNotThrottled() {
        val throttle = Throttle(50)

        throttle.isThrottled()
        Thread.sleep(70)

        assertFalse(throttle.isThrottled())
    }

    @Test
    fun zeroWindowNeverThrottles() {
        val throttle = Throttle(0)

        throttle.isThrottled()

        assertFalse(throttle.isThrottled())
    }

}
