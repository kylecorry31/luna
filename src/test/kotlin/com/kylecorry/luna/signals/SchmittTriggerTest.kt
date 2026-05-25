package com.kylecorry.luna.signals

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SchmittTriggerTest {

    @Test
    fun updateAppliesHysteresisWhenTriggeringAboveThreshold() {
        val trigger = SchmittTrigger(threshold = 10f, hysteresisAmount = 1f)

        assertFalse(trigger.update(9f))
        assertFalse(trigger.update(10f))
        assertTrue(trigger.update(11f))
        assertTrue(trigger.update(9f))
        assertFalse(trigger.update(8f))
    }

    @Test
    fun updateCanTriggerWhenValueIsBelowThreshold() {
        val trigger = SchmittTrigger(threshold = 10f, hysteresisAmount = 2f, trueIfAbove = false)

        assertFalse(trigger.update(11f))
        assertTrue(trigger.update(11f))
        assertFalse(trigger.update(9f))
    }
}
