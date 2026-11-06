package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.subsystem.Subsystem

/**
 * Runs [action] once on start and finishes immediately (WPILib v2's
 * `InstantCommand`). This is what [Commands.runOnce] and [Commands.none]
 * build on.
 */
open class InstantCommand(
    private val action: Runnable = Runnable {},
    vararg requiredSubsystems: Subsystem
) : Command() {
    init {
        addRequirements(*requiredSubsystems)
    }

    override fun start() {
        action.run()
    }

    override fun loop() {}

    override fun status(): Status = Status.FINISHED

    override fun end(endCondition: EndCondition) {}
}
