package com.rmsftc.cmdlib.scheduler

import com.rmsftc.cmdlib.cmd.ICommand

/**
 * Observes scheduler lifecycle events (WPILib v3's `SchedulerEvent` idea,
 * without any telemetry transport). Register with
 * [Scheduler.addEventListener]. All methods are no-ops by default.
 */
interface SchedulerEventListener {
    /** A command was accepted and started running. */
    fun onScheduled(command: ICommand) {}

    /** A command finished on its own. */
    fun onFinished(command: ICommand) {}

    /** A running command was ended because another command took its
     * requirements (a conflict loss). Explicit cancels report through
     * [onCancelled] instead. */
    fun onInterrupted(command: ICommand) {}

    /** A command was explicitly cancelled through [Scheduler.cancel]. */
    fun onCancelled(command: ICommand) {}
}
