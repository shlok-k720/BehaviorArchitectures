package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status

/**
 * Runs [action] on every tick and never finishes on its own (WPILib v2's
 * `RunCommand`). End it by cancelling it, or wrap it in a group/decorator
 * that ends it (for example [Command.withTimeout] or [Command.until]).
 */
open class RunCommand(
    private val action: Runnable = Runnable {},
    vararg tokens: Any
) : Command() {
    init {
        addRequirements(*tokens)
    }

    override fun start() {}

    override fun loop() {
        action.run()
    }

    override fun status(): Status = Status.RUNNING

    override fun end(endCondition: EndCondition) {}
}
