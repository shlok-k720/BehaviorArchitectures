package com.rmsftc.cmdlib.scheduler

import com.rmsftc.cmdlib.cmd.ICommand

interface IScheduler {
    fun schedule(command: ICommand)
    fun execute()
    fun cancel(command: ICommand)

    companion object {
        @JvmStatic
        val instance : IScheduler = Scheduler()
    }
}