package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.subsystem.Subsystem
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * A command built from lambdas: the primitive every factory in [Commands]
 * is built on (WPILib v2's `FunctionalCommand`). Prefer the [Commands]
 * factories over constructing this directly.
 *
 * @param onStart run once when the command starts.
 * @param onLoop run on every tick while the command is active.
 * @param statusSupplier reports whether the command is still running.
 * @param onEnd run once when the command finishes or is aborted.
 * @param requiredSubsystems the subsystems this command needs.
 */
class FunctionalCommand(
    private val onStart: Runnable = Runnable {},
    private val onLoop: Runnable = Runnable {},
    private val statusSupplier: Supplier<Status> = Supplier { Status.FINISHED },
    private val onEnd: Consumer<EndCondition> = Consumer { },
    vararg requiredSubsystems: Subsystem
) : Command() {
    init {
        addRequirements(*requiredSubsystems)
    }

    override fun start() {
        onStart.run()
    }

    override fun loop() {
        onLoop.run()
    }

    override fun status(): Status = statusSupplier.get()

    override fun end(endCondition: EndCondition) {
        onEnd.accept(endCondition)
    }
}
