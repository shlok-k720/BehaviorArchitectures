package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.scheduler.ComposedCommands
import com.rmsftc.cmdlib.subsystem.Subsystem

/**
 * Runs all its children at the same time, one tick each per loop. Ends when
 * the **last** child ends.
 *
 * Two children may not require the same subsystem (the constructor throws);
 * they would fight over one mechanism for the group's whole duration.
 */
class ParallelCommandGroup(vararg commands: ICommand) : Command() {
    private val children: List<ICommand> = commands.toList()
    private val finished = BooleanArray(commands.size)

    init {
        require(commands.isNotEmpty()) { "ParallelCommandGroup needs at least one command" }
        ComposedCommands.markAllComposed(commands, "ParallelCommandGroup")
        val seen = HashMap<Subsystem, ICommand>()
        for (child in commands) {
            for (requirement in child.requirements) {
                val previous = seen.putIfAbsent(requirement, child)
                if (previous != null) {
                    throw IllegalArgumentException(
                        "ParallelCommandGroup: '${previous.name}' and '${child.name}' " +
                            "both require subsystem '${requirement.getName()}'"
                    )
                }
            }
        }
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

    override fun status(): Status = Status.of(finished.any { !it })

    override fun end(endCondition: EndCondition) {
        if (endCondition == EndCondition.INTERRUPTED) {
            for (i in children.indices) {
                if (!finished[i]) {
                    children[i].invalidate()
                }
            }
        }
    }
}
