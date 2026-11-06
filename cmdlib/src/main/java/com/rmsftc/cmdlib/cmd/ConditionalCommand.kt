package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.scheduler.ComposedCommands
import java.util.function.BooleanSupplier

/**
 * Runs [onTrue] or [onFalse], chosen **once, when the command starts**.
 * A later change of [condition] does not switch branches mid-run — use
 * [Command.until]/[Command.onlyWhile] or a [BranchCommand] for that.
 */
class ConditionalCommand(
    private val onTrue: ICommand,
    private val onFalse: ICommand,
    private val condition: BooleanSupplier
) : Command() {
    private var chosen: ICommand? = null
    private var lastStatus: Status = Status.RUNNING

    init {
        ComposedCommands.markAllComposed(arrayOf(onTrue, onFalse), "ConditionalCommand")
        val allRequirements = onTrue.requirements + onFalse.requirements
        addRequirements(*allRequirements.toTypedArray())
        priority = maxOf(onTrue.priority, onFalse.priority)
        interruptionBehavior =
            if (onTrue.interruptionBehavior == InterruptionBehavior.CANCEL_SELF ||
                onFalse.interruptionBehavior == InterruptionBehavior.CANCEL_SELF
            ) InterruptionBehavior.CANCEL_SELF else InterruptionBehavior.CANCEL_INCOMING
        runsWhenDisabled = onTrue.runsWhenDisabled && onFalse.runsWhenDisabled
    }

    override fun start() {
        chosen = if (condition.asBoolean) onTrue else onFalse
        lastStatus = Status.RUNNING
    }

    override fun loop() {
        chosen?.let { lastStatus = it.execute() }
    }

    override fun status(): Status = lastStatus

    override fun end(endCondition: EndCondition) {
        if (endCondition == EndCondition.INTERRUPTED) {
            chosen?.invalidate()
        }
    }
}
