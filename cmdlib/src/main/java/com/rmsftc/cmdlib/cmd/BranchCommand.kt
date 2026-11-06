package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.scheduler.ComposedCommands
import java.util.function.BooleanSupplier

/**
 * Runs the first branch whose condition is true, checked in map order
 * **once, when the command starts** (Ivy's `Branch` — genuinely more
 * useful than a two-way conditional for autonomous mode selection). If no
 * condition is true, it finishes immediately without running anything.
 */
class BranchCommand(
    branches: Map<BooleanSupplier, ICommand>
) : Command() {
    private val branches: LinkedHashMap<BooleanSupplier, ICommand> = LinkedHashMap(branches)
    private var chosen: ICommand? = null
    private var lastStatus: Status = Status.RUNNING

    init {
        require(branches.isNotEmpty()) { "BranchCommand needs at least one branch" }
        ComposedCommands.markAllComposed(branches.values.toTypedArray(), "BranchCommand")
        val allRequirements = LinkedHashSet<Any>()
        var maxPriority = 0
        var anyCancelSelf = false
        var allRunWhenDisabled = true
        for (child in branches.values) {
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
        chosen = branches.entries.firstOrNull { it.key.asBoolean }?.value
        lastStatus = if (chosen == null) Status.FINISHED else Status.RUNNING
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
