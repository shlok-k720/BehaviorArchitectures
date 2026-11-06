package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status

/**
 * Runs [onStart] once when it starts and [onEnd] once when it ends, and
 * never finishes on its own (WPILib v2's `StartEndCommand`). The classic use
 * is "spin the flywheel until cancelled": `startEnd({ flywheel.on() },
 * { flywheel.off() }, flywheel)`.
 */
class StartEndCommand(
    private val onStart: Runnable,
    private val onEnd: Runnable,
    vararg tokens: Any
) : Command() {
    init {
        addRequirements(*tokens)
    }

    override fun start() {
        onStart.run()
    }

    override fun loop() {}

    override fun status(): Status = Status.RUNNING

    override fun end(endCondition: EndCondition) {
        onEnd.run()
    }
}
