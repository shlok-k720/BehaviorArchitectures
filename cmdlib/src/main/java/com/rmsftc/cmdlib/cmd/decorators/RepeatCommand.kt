package com.rmsftc.cmdlib.cmd.decorators

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.util.blackboard.Blackboard
import com.rmsftc.cmdlib.scheduler.ComposedCommands

/**
 * Restarts [child] from the beginning every time it finishes, until this
 * command is interrupted (built by [Command.repeatedly]). Never finishes
 * on its own.
 */
class RepeatCommand(private val child: Command) : Command() {
    init {
        ComposedCommands.markComposed(child, "RepeatCommand")
        addRequirements(*child.requirements.toTypedArray())
        priority = child.priority
        interruptionBehavior = child.interruptionBehavior
        blockedBehavior = child.blockedBehavior
        runsWhenDisabled = child.runsWhenDisabled
    }

    /** Passes the scheduler's board down to the child, so it shares it. */
    override fun attachBlackboard(board: Blackboard) {
        super.attachBlackboard(board)
        child.attachBlackboard(board)
    }

    override fun start() {}

    override fun loop() {
        child.execute()
    }

    override fun status(): Status = Status.RUNNING

    override fun end(endCondition: EndCondition) {
        if (endCondition == EndCondition.INTERRUPTED) {
            child.invalidate()
        }
    }
}
