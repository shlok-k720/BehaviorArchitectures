package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.scheduler.ComposedCommands
import com.rmsftc.cmdlib.subsystem.Subsystem
import java.util.function.Supplier

/**
 * Builds the real command lazily, when it starts, instead of when it is
 * constructed (WPILib v2's `DeferredCommand`, Ivy's `Lazy` — fixed: the
 * requirements must be declared **up front** in the constructor, and the
 * built command is rejected at start time if it needs more than declared,
 * so a deferred command can never run with no requirements).
 */
class DeferredCommand(
    private val supplier: Supplier<ICommand>,
    vararg requiredSubsystems: Subsystem
) : Command() {
    private var inner: ICommand? = null
    private var lastStatus: Status = Status.RUNNING

    init {
        addRequirements(*requiredSubsystems)
    }

    override fun start() {
        val built = supplier.get()
        val undeclared = built.requirements - requirements
        if (undeclared.isNotEmpty()) {
            throw IllegalStateException(
                "DeferredCommand: deferred command '${built.name}' requires subsystems " +
                    "not declared up front: ${undeclared.map { it.getName() }}"
            )
        }
        ComposedCommands.markComposed(built, "DeferredCommand")
        inner = built
        lastStatus = Status.RUNNING
    }

    override fun loop() {
        inner?.let { lastStatus = it.execute() }
    }

    override fun status(): Status = lastStatus

    override fun end(endCondition: EndCondition) {
        if (endCondition == EndCondition.INTERRUPTED) {
            inner?.invalidate()
        }
    }
}
