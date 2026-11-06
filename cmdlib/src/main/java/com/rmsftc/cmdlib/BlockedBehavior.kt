package com.rmsftc.cmdlib

/**
 * What happens to an *incoming* command when it cannot be scheduled right
 * now (a higher-priority command holds a token it needs, or a running
 * command at the same priority refuses to be interrupted).
 */
enum class BlockedBehavior {
    /** The incoming command is dropped. This is the default. */
    CANCEL,

    /** The incoming command is parked in the scheduler's queue and retried
     * every tick until its requirements are free (priority is re-checked on
     * every retry). */
    QUEUE
}
