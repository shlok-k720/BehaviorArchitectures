package com.rmsftc.cmdlib.scheduler

import com.rmsftc.cmdlib.cmd.ICommand
import java.util.Collections
import java.util.WeakHashMap

/**
 * Tracks commands that have been consumed by a composition (a group or a
 * decorator). A composed command cannot be scheduled on its own or added to
 * another composition afterwards (WPILib v2's composed-command registry);
 * reusing one throws with the site of the original composition.
 *
 * Internal: users never touch this. `ProxyCommand`/`ScheduleCommand` do not
 * mark their inner command composed, because they schedule it directly.
 */
internal object ComposedCommands {
    private val composed =
        Collections.synchronizedMap(WeakHashMap<ICommand, String>())

    /** Records [command] as part of the composition built at [site]. */
    fun markComposed(command: ICommand, site: String) {
        val existing = composed.putIfAbsent(command, site)
        if (existing != null) {
            throw IllegalStateException(
                "Command '${command.name}' cannot be used in '$site': it was " +
                    "already composed at '$existing'. A command instance can only " +
                    "belong to one composition; build a fresh one instead."
            )
        }
    }

    /** Marks every command in [commands] as composed at [site]. */
    fun markAllComposed(commands: Array<out ICommand>, site: String) {
        for (command in commands) {
            markComposed(command, site)
        }
    }

    fun isComposed(command: ICommand): Boolean = composed.containsKey(command)

    /** Throws if [command] was already composed into something else. */
    fun requireSchedulable(command: ICommand) {
        val site = composed[command]
        if (site != null) {
            throw IllegalStateException(
                "Command '${command.name}' cannot be scheduled directly: it was " +
                    "already composed at '$site'. Schedule the composition instead."
            )
        }
    }
}
