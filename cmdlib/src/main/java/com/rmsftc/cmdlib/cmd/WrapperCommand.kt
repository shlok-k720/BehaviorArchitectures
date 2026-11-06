package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.scheduler.ComposedCommands

/**
 * The decorator base (WPILib v2's `WrapperCommand`): runs another command
 * and adds behavior around it. The wrapped command is driven through its
 * own [ICommand.execute], so its lifecycle stays correct; the wrapper's
 * [start]/[loop]/[status]/[end] just delegate (they are never reached
 * through the wrapper's own [execute]).
 *
 * The wrapped command's requirements, priority, interruption behavior,
 * disabled behavior and name are copied at construction time.
 */
abstract class WrapperCommand(protected val wrapped: ICommand) : Command() {
    init {
        ComposedCommands.markComposed(wrapped, "WrapperCommand")
        addRequirements(*wrapped.requirements.toTypedArray())
        priority = wrapped.priority
        interruptionBehavior = wrapped.interruptionBehavior
        blockedBehavior = wrapped.blockedBehavior
        runsWhenDisabled = wrapped.runsWhenDisabled
        name = wrapped.name
    }

    /** Drives the wrapped command directly, so its own lifecycle applies. */
    override fun execute(): Status = wrapped.execute()

    /** Aborts the wrapped command (a no-op if it never started). */
    override fun invalidate() {
        wrapped.invalidate()
    }

    override fun start() {
        (wrapped as? Command)?.start()
    }

    override fun loop() {
        (wrapped as? Command)?.loop()
    }

    override fun status(): Status =
        (wrapped as? Command)?.status() ?: Status.FINISHED

    override fun end(endCondition: EndCondition) {
        (wrapped as? Command)?.end(endCondition)
    }
}
