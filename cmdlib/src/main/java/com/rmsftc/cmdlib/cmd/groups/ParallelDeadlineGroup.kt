package com.rmsftc.cmdlib.cmd.groups

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.cmdlib.cmd.ICommand
import com.rmsftc.cmdlib.scheduler.ComposedCommands

/**
 * Runs [deadline] alongside [others]. Ends only when the **deadline**
 * finishes; the others are interrupted even on a natural finish (they were
 * only ever running "until the deadline").
 */
class ParallelDeadlineGroup(
    private val deadline: ICommand,
    vararg others: ICommand
) : Command() {
    private val children: List<ICommand> = listOf(deadline) + others.toList()
    private val finished = BooleanArray(children.size)

    init {
        ComposedCommands.markAllComposed(children.toTypedArray(), "ParallelDeadlineGroup")
        val allRequirements = LinkedHashSet<Any>()
        var maxPriority = 0
        var anyCancelSelf = false
        var allRunWhenDisabled = true
        for (child in children) {
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

    override fun status(): Status = Status.of(!finished[0])

    override fun end(endCondition: EndCondition) {
        for (i in children.indices) {
            if (!finished[i]) {
                children[i].invalidate()
            }
        }
    }
}
