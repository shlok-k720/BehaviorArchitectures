package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.BlockedBehavior
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.ScheduleResult
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.scheduler.IScheduler

/**
 * A unit of work the scheduler can run. One [execute] call is one tick: the
 * command does its work for this loop and reports whether it is still
 * running. [invalidate] aborts it (the scheduler calls this on cancel).
 *
 * Most commands extend [Command], which owns the start/loop/status/end
 * lifecycle. This interface exists so groups and the scheduler can also
 * drive lightweight command implementations directly.
 */
interface ICommand {
    /** Runs one tick of this command and reports whether it is still running. */
    fun execute(): Status

    /** Aborts this command. Must be safe to call on a command that never
     * started (in that case it must do nothing, and must not call `end()`). */
    fun invalidate()

    /** The requirement tokens this command needs (Ivy-style untyped
     * tokens: any object — usually a string naming a mechanism, or the
     * mechanism itself). No two running commands may hold the same token.
     * Empty by default. */
    val requirements: Set<Any>
        get() = emptySet()

    /** Scheduling priority; higher interrupts lower at the same conflict
     * (see [BlockedBehavior]). Defaults to 0. */
    val priority: Int
        get() = 0

    /** What happens to *this* command when another command at the same
     * priority needs one of its subsystems. Defaults to
     * [InterruptionBehavior.CANCEL_SELF]. */
    val interruptionBehavior: InterruptionBehavior
        get() = InterruptionBehavior.CANCEL_SELF

    /** What happens to *this* command when it cannot be scheduled right now.
     * Defaults to [BlockedBehavior.CANCEL] (dropped). */
    val blockedBehavior: BlockedBehavior
        get() = BlockedBehavior.CANCEL

    /** Whether this command may run while the OpMode is inactive. Defaults
     * to false. */
    val runsWhenDisabled: Boolean
        get() = false

    /** A human-readable name, used in scheduler events and error messages. */
    val name: String
        get() = javaClass.simpleName

    /** Schedules this command on the default scheduler
     * ([IScheduler.instance]). */
    fun schedule(): ScheduleResult = IScheduler.instance.schedule(this)

    /** Cancels this command on the default scheduler. */
    fun cancel() {
        IScheduler.instance.cancel(this)
    }

    /** Whether this command is currently scheduled on the default scheduler. */
    fun isScheduled(): Boolean = IScheduler.instance.isScheduled(this)
}
