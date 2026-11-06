package com.rmsftc.cmdlib


/**
 * The result of one [cmd.ICommand.execute] tick: is the command still running,
 * or has it finished? Mirrors `NodeStatus` in the behaviorTrees module.
 */
fun interface Status {
    fun isRunning(): Boolean

    companion object {
        /** A status that reports the command is still running. */
        @JvmField
        val RUNNING: Status = Status { true }

        /** A status that reports the command has finished. */
        @JvmField
        val FINISHED: Status = Status { false }

        /** Returns [RUNNING] when [running] is true, [FINISHED] otherwise. */
        @JvmStatic
        fun of(running: Boolean): Status = if (running) RUNNING else FINISHED
    }
}
