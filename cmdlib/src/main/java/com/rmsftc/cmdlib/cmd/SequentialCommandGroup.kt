package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.scheduler.ComposedCommands
import com.rmsftc.cmdlib.subsystem.Subsystem

/**
 * Runs its children in order, one at a time. When a child finishes, the
 * next one starts **in the same tick**. Ends when the last child ends.
 *
 * The group holds the union of its children's requirements for its whole
 * duration, so no other command can take a child's subsystem mid-sequence.
 * Its interruption behavior is `CANCEL_SELF` if any child has it.
 */
class SequentialCommandGroup(vararg commands: ICommand) : Command() {
    private val children: List<ICommand> = commands.toList()
    private var currentIndex = 0

    init {
        require(commands.isNotEmpty()) { "SequentialCommandGroup needs at least one command" }
        ComposedCommands.markAllComposed(commands, "SequentialCommandGroup")
        val allRequirements = LinkedHashSet<Subsystem>()
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
        currentIndex = 0
    }

    override fun loop() {
        while (currentIndex < children.size) {
            val status = children[currentIndex].execute()
            if (status.isRunning()) {
                break
            }
            currentIndex++
        }
    }

    override fun status(): Status = Status.of(currentIndex < children.size)

    override fun end(endCondition: EndCondition) {
        if (endCondition == EndCondition.INTERRUPTED && currentIndex < children.size) {
            children[currentIndex].invalidate()
        }
    }
}
