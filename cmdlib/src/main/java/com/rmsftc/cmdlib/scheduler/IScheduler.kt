package com.rmsftc.cmdlib.scheduler

import com.rmsftc.cmdlib.ScheduleResult
import com.rmsftc.cmdlib.cmd.ICommand
import com.rmsftc.cmdlib.trigger.Trigger

/**
 * Runs commands. The OpMode loop calls [execute] once per loop; commands are
 * added with [schedule] and removed with [cancel] (or run to completion).
 *
 * The default scheduler is [instance]; create a `new Scheduler()` for tests
 * so they do not share state with the OpMode.
 */
interface IScheduler {
    /** Schedules [command], resolving requirement conflicts. Never silent:
     * the result says what happened. */
    fun schedule(command: ICommand): ScheduleResult

    /** Runs one tick: trigger polling, scheduled commands, queue promotion. */
    fun execute()

    /** Cancels [command] if it is running (its `end(INTERRUPTED)` runs).
     * A merely queued command is dropped without `end()` ever running. */
    fun cancel(command: ICommand)

    /** Cancels every running command and drops the queue. */
    fun cancelAll()

    /** Whether [command] is currently running or queued. */
    fun isScheduled(command: ICommand): Boolean

    /** The running command that currently holds [token], or null. */
    fun requiring(token: Any): ICommand? = null

    /** Registers [trigger] for polling on every tick. */
    fun addTrigger(trigger: Trigger) {}

    /** Registers a lifecycle-event listener. */
    fun addEventListener(listener: SchedulerEventListener) {}

    /** Cancels everything (same as [cancelAll]). */
    fun reset() {
        cancelAll()
    }

    companion object {
        /** The default scheduler, shared by OpMode code. */
        @JvmStatic
        val instance: IScheduler = Scheduler()
    }
}
