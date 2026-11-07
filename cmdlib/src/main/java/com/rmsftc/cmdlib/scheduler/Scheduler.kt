package com.rmsftc.cmdlib.scheduler

import com.rmsftc.cmdlib.BlockedBehavior
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.ScheduleResult
import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.cmdlib.cmd.ICommand
import com.rmsftc.cmdlib.trigger.Trigger
import com.rmsftc.util.blackboard.Blackboard
import com.rmsftc.util.logger.Logger
import java.util.ArrayDeque
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/**
 * The scheduler. Runs commands in parallel, one tick per [execute] call:
 *
 * 1. Trigger polling (bindings schedule/cancel commands).
 * 2. Each running command's `execute()`; finished commands are removed.
 * 3. Queued commands are promoted when their requirements free up
 *    (priority is re-checked on every retry).
 *
 * Requirements are untyped tokens (Ivy-style): any object, usually a string
 * naming a mechanism or the mechanism itself. No two running commands may
 * hold the same token.
 *
 * Conflict rule (WPILib v2 + Ivy hybrid): scheduling is decided by integer
 * [ICommand.priority] first — a higher-priority holder blocks the incoming
 * command, a lower-priority holder is interrupted. At equal priority the
 * *running* command's [InterruptionBehavior]
 * decides: `CANCEL_SELF` (default) ends it, `CANCEL_INCOMING` rejects the
 * newcomer. A rejected command with [BlockedBehavior.QUEUE] is parked and
 * retried every tick instead of dropped.
 *
 * @param clock nanosecond clock, injected for tests (defaults to
 *   `System.nanoTime()`). Used by triggers' debounce.
 * @param isOpModeActive whether the OpMode is active; commands that do not
 *   run when disabled are cancelled while it is false. The OpMode sets this.
 */
class Scheduler @JvmOverloads constructor(
    private val clock: Supplier<Long> = Supplier { System.nanoTime() },
    private val isOpModeActive: BooleanSupplier = BooleanSupplier { true },
    private val blackboard: Blackboard = Blackboard()
) : IScheduler {

    private val running = LinkedHashSet<ICommand>()
    private val queued = ArrayDeque<ICommand>()
    private val requirements = HashMap<Any, ICommand>()
    private val triggers = ArrayList<Trigger>()
    private val listeners = ArrayList<SchedulerEventListener>()

    override fun schedule(command: ICommand): ScheduleResult {
        ComposedCommands.requireSchedulable(command)
        if (running.contains(command) || queued.contains(command)) {
            return ScheduleResult.ALREADY_RUNNING
        }
        if (!isOpModeActive.asBoolean && !command.runsWhenDisabled) {
            return ScheduleResult.REJECTED_WHILE_DISABLED
        }
        val holders = conflictingHolders(command)
        if (holders.isEmpty()) {
            start(command)
            return ScheduleResult.SCHEDULED
        }
        val incomingPriority = command.priority
        if (holders.any { it.priority > incomingPriority }) {
            return parkOrReject(command, ScheduleResult.BLOCKED_BY_HIGHER_PRIORITY)
        }
        if (holders.any {
                it.priority == incomingPriority &&
                    it.interruptionBehavior == InterruptionBehavior.CANCEL_INCOMING
            }
        ) {
            return parkOrReject(command, ScheduleResult.REJECTED_BY_RUNNING_COMMAND)
        }
        // Every holder is lower priority, or equal priority and willing to
        // be interrupted: end them all and schedule the newcomer.
        holders.toList().forEach { interrupt(it) }
        start(command)
        return ScheduleResult.SCHEDULED
    }

    override fun execute() {
        if (!isOpModeActive.asBoolean) {
            // Cancel commands that may not run while disabled; the rest keep
            // running below.
            running.filter { !it.runsWhenDisabled }.toList().forEach { cancel(it) }
        }
        triggers.toList().forEach { it.poll() }
        // Run a snapshot: commands may schedule/cancel others mid-tick.
        running.toList().forEach { command ->
            val status = command.execute()
            if (!status.isRunning()) {
                finish(command)
            }
        }
        promoteQueued()
    }

    override fun cancel(command: ICommand) {
        if (running.remove(command)) {
            releaseRequirements(command)
            command.invalidate()
            Logger.log(Logger.CMD_CANCELLED, "name=${command.name}")
            listeners.forEach { it.onCancelled(command) }
        } else if (queued.remove(command)) {
            // Never started: drop it without ever calling end().
        }
    }

    override fun cancelAll() {
        running.toList().forEach { cancel(it) }
        queued.clear()
    }

    override fun isScheduled(command: ICommand): Boolean =
        running.contains(command) || queued.contains(command)

    override fun requiring(token: Any): ICommand? = requirements[token]

    override fun addTrigger(trigger: Trigger) {
        if (!triggers.contains(trigger)) {
            triggers.add(trigger)
        }
    }

    override fun addEventListener(listener: SchedulerEventListener) {
        listeners.add(listener)
    }

    override fun reset() {
        cancelAll()
        triggers.clear()
    }

    /** The shared [Blackboard]: attached to every command this scheduler runs. */
    fun blackboard(): Blackboard = blackboard

    /** The clock this scheduler was built with (triggers use it). */
    fun clock(): Supplier<Long> = clock

    /** All commands currently running (snapshot). */
    fun runningCommands(): Set<ICommand> = running.toSet()

    /** All commands currently parked in the queue (snapshot). */
    fun queuedCommands(): List<ICommand> = queued.toList()

    private fun conflictingHolders(command: ICommand): Set<ICommand> {
        val holders = LinkedHashSet<ICommand>()
        for (requirement in command.requirements) {
            requirements[requirement]?.let { holders.add(it) }
        }
        return holders
    }

    private fun parkOrReject(command: ICommand, reason: ScheduleResult): ScheduleResult {
        return if (command.blockedBehavior == BlockedBehavior.QUEUE) {
            queued.add(command)
            ScheduleResult.QUEUED
        } else {
            reason
        }
    }

    private fun start(command: ICommand) {
        (command as? Command)?.attachBlackboard(blackboard)
        running.add(command)
        for (requirement in command.requirements) {
            requirements[requirement] = command
        }
        Logger.log(Logger.CMD_SCHEDULED, "name=${command.name}")
        listeners.forEach { it.onScheduled(command) }
    }

    /** Ends [command] as interrupted and frees its requirements. */
    private fun interrupt(command: ICommand) {
        running.remove(command)
        releaseRequirements(command)
        command.invalidate()
        Logger.log(Logger.CMD_INTERRUPTED, "name=${command.name}")
        listeners.forEach { it.onInterrupted(command) }
    }

    /** Removes a naturally finished [command] and frees its requirements. */
    private fun finish(command: ICommand) {
        running.remove(command)
        releaseRequirements(command)
        Logger.log(Logger.CMD_FINISHED, "name=${command.name}")
        listeners.forEach { it.onFinished(command) }
    }

    private fun releaseRequirements(command: ICommand) {
        for (requirement in command.requirements) {
            if (requirements[requirement] === command) {
                requirements.remove(requirement)
            }
        }
    }

    /** Moves queued commands whose requirements are free (and whose priority
     * still wins) into the running set. */
    private fun promoteQueued() {
        if (queued.isEmpty()) return
        val iterator = queued.iterator()
        while (iterator.hasNext()) {
            val command = iterator.next()
            if (!isOpModeActive.asBoolean && !command.runsWhenDisabled) {
                iterator.remove()
                continue
            }
            val holders = conflictingHolders(command)
            val blocked =
                holders.any { it.priority > command.priority } ||
                    holders.any {
                        it.priority == command.priority &&
                            it.interruptionBehavior == InterruptionBehavior.CANCEL_INCOMING
                    }
            if (!blocked) {
                holders.toList().forEach { interrupt(it) }
                iterator.remove()
                start(command)
            }
        }
    }
}
