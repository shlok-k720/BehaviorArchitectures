package com.rmsftc.cmdlib.scheduler

import com.rmsftc.cmdlib.ScheduleResult
import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.cmdlib.cmd.ICommand
import com.rmsftc.util.blackboard.Blackboard
import com.rmsftc.util.logger.Logger
import java.util.ArrayDeque
import java.util.Queue

/**
 * The simplest scheduler: a FIFO queue that runs one command at a time, in
 * order, until it finishes. No requirement conflicts are possible (only the
 * head command runs) and no default commands or triggers. Useful for
 * strictly sequential autonomous routines; use [Scheduler] for parallel
 * work.
 */
class QueueScheduler(private val blackboard: Blackboard = Blackboard()) : IScheduler {

    private val commandQueue: Queue<ICommand> = ArrayDeque()

    /** The shared [Blackboard]: attached to every command this scheduler runs. */
    fun blackboard(): Blackboard = blackboard

    override fun schedule(command: ICommand): ScheduleResult {
        ComposedCommands.requireSchedulable(command)
        if (commandQueue.contains(command)) {
            return ScheduleResult.ALREADY_RUNNING
        }
        (command as? Command)?.attachBlackboard(blackboard)
        commandQueue.add(command)
        Logger.log(Logger.CMD_SCHEDULED, "name=${command.name}")
        return ScheduleResult.QUEUED
    }

    override fun execute() {
        val command = commandQueue.firstOrNull() ?: return
        val status = command.execute()
        if (!status.isRunning()) {
            commandQueue.remove()
            Logger.log(Logger.CMD_FINISHED, "name=${command.name}")
        }
    }

    override fun cancel(command: ICommand) {
        // invalidate() is a no-op on a command that never started, so this
        // is safe for queued commands too.
        val wasQueued = commandQueue.remove(command)
        command.invalidate()
        if (wasQueued) {
            Logger.log(Logger.CMD_CANCELLED, "name=${command.name}")
        }
    }

    override fun cancelAll() {
        commandQueue.toList().forEach { cancel(it) }
    }

    override fun isScheduled(command: ICommand): Boolean = commandQueue.contains(command)
}
