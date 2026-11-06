package com.rmsftc.cmdlib.scheduler

import com.rmsftc.cmdlib.cmd.ICommand
import java.util.ArrayDeque
import java.util.Queue

class QueueScheduler: IScheduler {

    private val commandQueue: Queue<ICommand> = ArrayDeque()

    override fun schedule(command: ICommand) {
        commandQueue.add(command)
    }

    override fun execute() {
        val command = commandQueue.firstOrNull()
        val status = command?.execute()
        if (status != null) {
            if (!status.isRunning()) commandQueue.remove()
        }
    }

    override fun cancel(command: ICommand) {
        command.invalidate()
        commandQueue.remove(command)
    }
}