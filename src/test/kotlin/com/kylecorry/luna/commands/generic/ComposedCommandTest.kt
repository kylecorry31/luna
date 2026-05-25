package com.kylecorry.luna.commands.generic

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ComposedCommandTest {

    @Test
    fun executeRunsAllCommandsInOrderWithSameValue() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val command1 = object : Command<Int> {
            override fun execute(value: Int) {
                calls.add(1 to value)
            }
        }
        val command2 = object : Command<Int> {
            override fun execute(value: Int) {
                calls.add(2 to value)
            }
        }
        val command3 = object : Command<Int> {
            override fun execute(value: Int) {
                calls.add(3 to value)
            }
        }

        val composed = ComposedCommand(command1, command2, command3)
        composed.execute(42)

        assertEquals(listOf(1 to 42, 2 to 42, 3 to 42), calls)
    }

    @Test
    fun executeWithNoCommandsDoesNothing() {
        val composed = ComposedCommand<Int>()

        assertDoesNotThrow {
            composed.execute(42)
        }
    }
}
