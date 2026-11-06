package com.rmsftc.cmdlib.cmd.selectors

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.cmdlib.cmd.ICommand
import com.rmsftc.cmdlib.scheduler.ComposedCommands
import java.util.function.Supplier

/**
 * Runs one command from [commands], chosen by [selector] **once, when the
 * command starts**. Throws if the selector returns a key with no command.
 */
class SelectCommand<K>(
    private val commands: Map<K, ICommand>,
    private val selector: Supplier<K>
) : Command() {
    private var chosen: ICommand? = null
    private var lastStatus: Status = Status.RUNNING

    init {
        require(commands.isNotEmpty()) { "SelectCommand needs at least one command" }
        ComposedCommands.markAllComposed(commands.values.toTypedArray(), "SelectCommand")
        val allRequirements = LinkedHashSet<Any>()
        var maxPriority = 0
        var anyCancelSelf = false
        var allRunWhenDisabled = true
        for (child in commands.values) {
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
        val key = selector.get()
        chosen = commands[key]
            ?: throw IllegalStateException("SelectCommand: no command for key '$key'")
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
