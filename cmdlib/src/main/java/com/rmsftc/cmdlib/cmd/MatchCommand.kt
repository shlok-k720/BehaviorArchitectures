package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.scheduler.ComposedCommands
import java.util.EnumMap
import java.util.function.Supplier

/**
 * An enum switch for commands (Ivy's `Match`): runs the command mapped to
 * the enum value [selector] returns, chosen **once, when the command
 * starts**. Throws if the value has no command.
 */
class MatchCommand<T : Enum<T>>(
    private val commands: EnumMap<T, ICommand>,
    private val selector: Supplier<T>
) : Command() {
    private var chosen: ICommand? = null
    private var lastStatus: Status = Status.RUNNING

    init {
        require(commands.isNotEmpty()) { "MatchCommand needs at least one command" }
        ComposedCommands.markAllComposed(commands.values.toTypedArray(), "MatchCommand")
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
            ?: throw IllegalStateException("MatchCommand: no command for '$key'")
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
