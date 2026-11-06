package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.ScheduleResult
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.scheduler.IScheduler

/**
 * Schedules [inner] through the scheduler and ends when it ends (WPILib
 * v2's `ProxyCommand`). Unlike a group, the inner command runs as its own
 * scheduled unit, so it can be cancelled independently and its requirements
 * are enforced by the scheduler. If the inner command cannot be scheduled
 * at all, the proxy finishes immediately.
 */
class ProxyCommand(
    private val inner: ICommand,
    private val scheduler: IScheduler = IScheduler.instance
) : Command() {
    private var scheduledOk = false

    init {
        addRequirements(*inner.requirements.toTypedArray())
        priority = inner.priority
        interruptionBehavior = inner.interruptionBehavior
        blockedBehavior = inner.blockedBehavior
        runsWhenDisabled = inner.runsWhenDisabled
    }

    override fun start() {
        val result = scheduler.schedule(inner)
        scheduledOk =
            result == ScheduleResult.SCHEDULED || result == ScheduleResult.QUEUED
    }

    override fun loop() {}

    override fun status(): Status =
        Status.of(scheduledOk && scheduler.isScheduled(inner))

    override fun end(endCondition: EndCondition) {
        if (endCondition == EndCondition.INTERRUPTED && scheduler.isScheduled(inner)) {
            scheduler.cancel(inner)
        }
    }
}
