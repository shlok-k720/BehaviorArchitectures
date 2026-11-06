package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.scheduler.IScheduler

/**
 * Fire-and-forget: schedules [command] and finishes immediately (WPILib
 * v2's `ScheduleCommand`). The scheduled command keeps running on its own
 * after this one is gone — unlike [ProxyCommand], which stays alive until
 * the inner command ends.
 */
class ScheduleCommand(
    private val command: ICommand,
    private val scheduler: IScheduler = IScheduler.instance
) : Command() {
    override fun start() {
        scheduler.schedule(command)
    }

    override fun loop() {}

    override fun status(): Status = Status.FINISHED

    override fun end(endCondition: EndCondition) {}
}
