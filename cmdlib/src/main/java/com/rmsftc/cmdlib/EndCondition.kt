package com.rmsftc.cmdlib

/**
 * Why a command's [cmd.Command.end] is being called. Passed to `end()` so a
 * command can tell a natural finish apart from being cancelled.
 */
enum class EndCondition {
    /** The command finished on its own (its status reported not-running). */
    NATURALLY,

    /** The command was cancelled, lost a requirement conflict, or its parent
     * group/command was interrupted. */
    INTERRUPTED
}
