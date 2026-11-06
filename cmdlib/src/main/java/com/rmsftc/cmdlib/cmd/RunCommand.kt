package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.subsystem.Subsystem

/**
 * Runs [action] on every tick and never finishes on its own (WPILib v2's
 * `RunCommand`). End it by cancelling it, or wrap it in a group/decorator
 * that ends it (for example [Command.withTimeout] or [Command.until]).
 */
open class RunCommand(
    private val action: Runnable = Runnable {},
    vararg requiredSubsystems: Subsystem
) : Command() {
    init {
        addRequirements(*requiredSubsystems)
    }

    override fun start() {}

    override fun loop() {
        action.run()
    }

    override fun status(): Status = Status.RUNNING

    override fun end(endCondition: EndCondition) {}
}
