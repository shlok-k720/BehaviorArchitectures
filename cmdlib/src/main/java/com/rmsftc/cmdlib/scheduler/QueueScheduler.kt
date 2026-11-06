package com.rmsftc.cmdlib.scheduler

import com.rmsftc.cmdlib.ScheduleResult
import com.rmsftc.cmdlib.cmd.ICommand
import java.util.ArrayDeque
import java.util.Queue

/**
 * The simplest scheduler: a FIFO queue that runs one command at a time, in
 * order, until it finishes. No requirement conflicts are possible (only the
 * head command runs) and no default commands or triggers. Useful for
 * strictly sequential autonomous routines; use [Scheduler] for parallel
 * work.
 */
class QueueScheduler : IScheduler {

    private val commandQueue: Queue<ICommand> = ArrayDeque()

    override fun schedule(command: ICommand): ScheduleResult {
        ComposedCommands.requireSchedulable(command)
        if (commandQueue.contains(command)) {
            return ScheduleResult.ALREADY_RUNNING
        }
        commandQueue.add(command)
        return ScheduleResult.QUEUED
    }

    override fun execute() {
        val command = commandQueue.firstOrNull() ?: return
        val status = command.execute()
        if (!status.isRunning()) {
            commandQueue.remove()
        }
    }

    override fun cancel(command: ICommand) {
        // invalidate() is a no-op on a command that never started, so this
        // is safe for queued commands too.
        command.invalidate()
        commandQueue.remove(command)
    }

    override fun cancelAll() {
        commandQueue.toList().forEach { it.invalidate() }
        commandQueue.clear()
    }

    override fun isScheduled(command: ICommand): Boolean = commandQueue.contains(command)
}
