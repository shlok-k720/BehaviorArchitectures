package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status

/**
 * Runs [action] once on start and finishes immediately (WPILib v2's
 * `InstantCommand`). This is what [Commands.runOnce] and [Commands.none]
 * build on.
 */
open class InstantCommand(
    private val action: Runnable = Runnable {},
    vararg tokens: Any
) : Command() {
    init {
        addRequirements(*tokens)
    }

    override fun start() {
        action.run()
    }

    override fun loop() {}

    override fun status(): Status = Status.FINISHED

    override fun end(endCondition: EndCondition) {}
}
