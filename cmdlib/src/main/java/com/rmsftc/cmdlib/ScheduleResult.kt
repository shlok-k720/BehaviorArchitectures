package com.rmsftc.cmdlib

/**
 * The outcome of asking the scheduler to schedule a command, so a dropped
 * schedule is visible instead of silent (WPILib v3's typed-result idea).
 */
enum class ScheduleResult {
    /** The command was accepted and is now running (or will run next tick). */
    SCHEDULED,

    /** The same command instance is already scheduled or queued. */
    ALREADY_RUNNING,

    /** A higher-priority command holds a token this command needs. */
    BLOCKED_BY_HIGHER_PRIORITY,

    /** A running command at the same priority refused to be interrupted
     * ([InterruptionBehavior.CANCEL_INCOMING]). */
    REJECTED_BY_RUNNING_COMMAND,

    /** The OpMode is not active and the command does not run when disabled. */
    REJECTED_WHILE_DISABLED,

    /** The command was parked in the scheduler's queue (its
     * [BlockedBehavior] is [BlockedBehavior.QUEUE]) and will be retried. */
    QUEUED
}
