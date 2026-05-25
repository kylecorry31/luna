package com.kylecorry.luna.commands

class ComposedCommand(vararg val commands: Command) : Command {
    override fun execute() {
        commands.forEach(Command::execute)
    }
}
