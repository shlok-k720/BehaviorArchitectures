package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.Status
import java.util.function.Consumer

/**
 * Runs [handler] after the wrapped command ends, with the reason it ended.
 * Built by [Command.finallyDo] and [Command.handleInterrupt].
 */
class FinallyDoCommand(
    wrapped: ICommand,
    private val handler: Consumer<EndCondition>
) : WrapperCommand(wrapped) {
    private var hookRan = false

    override fun execute(): Status {
        val status = wrapped.execute()
        if (!status.isRunning() && !hookRan) {
            hookRan = true
            handler.accept(EndCondition.NATURALLY)
        }
        return status
    }

    override fun invalidate() {
        if (!hookRan) {
            hookRan = true
            handler.accept(EndCondition.INTERRUPTED)
        }
        super.invalidate()
    }
}

/** Overrides the wrapped command's interruption behavior. */
class InterruptBehaviorCommand(
    wrapped: ICommand,
    behavior: InterruptionBehavior
) : WrapperCommand(wrapped) {
    init {
        interruptionBehavior = behavior
    }
}

/** Overrides whether the wrapped command may run while the OpMode is inactive. */
class RunsWhenDisabledCommand(
    wrapped: ICommand,
    ignore: Boolean
) : WrapperCommand(wrapped) {
    init {
        runsWhenDisabled = ignore
    }
}

/** Overrides the wrapped command's name. */
class NamedCommand(
    wrapped: ICommand,
    newName: String
) : WrapperCommand(wrapped) {
    init {
        name = newName
    }
}
