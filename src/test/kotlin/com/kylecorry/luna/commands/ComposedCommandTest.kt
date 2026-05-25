package com.kylecorry.luna.commands

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ComposedCommandTest {

    @Test
    fun executeRunsAllCommandsInOrder() {
        val calls = mutableListOf<Int>()
        val command1 = object : Command {
            override fun execute() {
                calls.add(1)
            }
        }
        val command2 = object : Command {
            override fun execute() {
                calls.add(2)
            }
        }
        val command3 = object : Command {
            override fun execute() {
                calls.add(3)
            }
        }

        val composed = ComposedCommand(command1, command2, command3)
        composed.execute()

        assertEquals(listOf(1, 2, 3), calls)
    }

    @Test
    fun executeWithNoCommandsDoesNothing() {
        val composed = ComposedCommand()

        assertDoesNotThrow {
            composed.execute()
        }
    }
}
