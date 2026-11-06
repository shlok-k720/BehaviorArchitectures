package com.rmsftc.cmdlib.cmd.groups

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.cmdlib.cmd.ICommand
import com.rmsftc.cmdlib.scheduler.ComposedCommands

/**
 * Runs all its children at the same time. Ends when the **first** child
 * ends; the losers are interrupted (`end(INTERRUPTED)`). This is what
 * [Command.withTimeout], [Command.until] and [Command.onlyWhile] build on.
 */
class ParallelRaceGroup(vararg commands: ICommand) : Command() {
    private val children: List<ICommand> = commands.toList()
    private val finished = BooleanArray(commands.size)

    init {
        require(commands.isNotEmpty()) { "ParallelRaceGroup needs at least one command" }
        ComposedCommands.markAllComposed(commands, "ParallelRaceGroup")
        val allRequirements = LinkedHashSet<Any>()
        var maxPriority = 0
        var anyCancelSelf = false
        var allRunWhenDisabled = true
        for (child in commands) {
            allRequirements.addAll(child.requirements)
            maxPriority = maxOf(maxPriority, child.priority)
            if (child.interruptionBehavior == InterruptionBehavior.CANCEL_SELF) {
                anyCancelSelf = true
            }
            if (!child.runsWhenDisabled) {
                allRunWhenDisabled = false
            }
        }
        addRequirements(*allRequirements.toTypedArray())
        priority = maxPriority
        interruptionBehavior =
            if (anyCancelSelf) InterruptionBehavior.CANCEL_SELF
            else InterruptionBehavior.CANCEL_INCOMING
        runsWhenDisabled = allRunWhenDisabled
    }

    override fun start() {
        finished.fill(false)
    }

    override fun loop() {
        for (i in children.indices) {
            if (!finished[i]) {
                val status = children[i].execute()
                if (!status.isRunning()) {
                    finished[i] = true
                }
            }
        }
    }

    override fun status(): Status = Status.of(finished.none { it })

    override fun end(endCondition: EndCondition) {
        // However the race ends, every child that is still running is
        // interrupted — the losers on a natural finish, all of them on a
        // cancel.
        for (i in children.indices) {
            if (!finished[i]) {
                children[i].invalidate()
            }
        }
    }
}
