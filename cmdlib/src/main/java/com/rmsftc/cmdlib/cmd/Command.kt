package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.BlockedBehavior
import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.cmd.decorators.FinallyDoCommand
import com.rmsftc.cmdlib.cmd.decorators.InterruptBehaviorCommand
import com.rmsftc.cmdlib.cmd.decorators.NamedCommand
import com.rmsftc.cmdlib.cmd.decorators.RepeatCommand
import com.rmsftc.cmdlib.cmd.decorators.RunsWhenDisabledCommand
import com.rmsftc.cmdlib.cmd.forking.ProxyCommand
import com.rmsftc.cmdlib.cmd.groups.ParallelCommandGroup
import com.rmsftc.cmdlib.cmd.groups.ParallelDeadlineGroup
import com.rmsftc.cmdlib.cmd.groups.ParallelRaceGroup
import com.rmsftc.cmdlib.cmd.groups.SequentialCommandGroup
import com.rmsftc.cmdlib.cmd.selectors.ConditionalCommand
import com.rmsftc.cmdlib.scheduler.IScheduler
import com.rmsftc.util.blackboard.Blackboard
import java.util.function.BooleanSupplier
import java.util.function.Consumer

/**
 * The base class for commands, with the same started-flag lifecycle as
 * `BehaviorNode` in the behaviorTrees module:
 *
 * - [start] runs once, on the first [execute] tick.
 * - [loop] runs on every tick while the command is active.
 * - [status] reports whether the command is still running.
 * - [end] runs once when the command finishes ([EndCondition.NATURALLY]) or
 *   is aborted ([EndCondition.INTERRUPTED]).
 *
 * Subclasses declare their requirements in the constructor with
 * [addRequirements]. Use the decorator methods below (or the [Commands]
 * factories) to compose commands instead of subclassing where possible.
 */
abstract class Command : ICommand {
    private var isStarted = false
    private var board: Blackboard = Blackboard()
    private val requirementSet = LinkedHashSet<Any>()

    override val requirements: Set<Any>
        get() = requirementSet.toSet()
    override var priority: Int = 0
    override var interruptionBehavior: InterruptionBehavior = InterruptionBehavior.CANCEL_SELF
    override var blockedBehavior: BlockedBehavior = BlockedBehavior.CANCEL
    override var runsWhenDisabled: Boolean = false
    override var name: String = javaClass.simpleName

    /**
     * The shared blackboard: set by the scheduler when this command is
     * scheduled (see [attachBlackboard]), so commands can share data the
     * way BehaviorTrees nodes do. Unscheduled commands keep their own
     * private board. Composites override [attachBlackboard] to pass the
     * board down to their children.
     */
    protected fun blackboard(): Blackboard = board

    /** Called by the scheduler (same module) when this command is scheduled. */
    internal open fun attachBlackboard(board: Blackboard) {
        this.board = board
    }

    /** Declares the requirement tokens this command needs. Call from
     * the constructor. */
    protected fun addRequirements(vararg tokens: Any) {
        requirementSet.addAll(tokens)
    }

    override fun execute(): Status {
        if (!isStarted) {
            start()
            isStarted = true
        }
        loop()
        val status = status()
        if (!status.isRunning()) {
            end(EndCondition.NATURALLY)
            isStarted = false
        }
        return status
    }

    /** Runs once when the command first starts. */
    abstract fun start()

    /** Runs on every tick while the command is active. */
    abstract fun loop()

    /** Reports whether the command is still running. */
    abstract fun status(): Status

    /** Runs once when the command finishes or is aborted. */
    abstract fun end(endCondition: EndCondition)

    /**
     * Aborts this command: ends it with [EndCondition.INTERRUPTED] if it
     * started, and does nothing if it never started. Groups and wrappers
     * override this to abort their children first.
     */
    override fun invalidate() {
        if (isStarted) {
            end(EndCondition.INTERRUPTED)
            isStarted = false
        }
    }

    // ---- Decorators (each returns a new command; this command is consumed) ----

    /** Runs [next] after this command finishes. */
    fun andThen(vararg next: ICommand): Command = SequentialCommandGroup(this, *next)

    /** Runs [action] once after this command finishes. */
    fun andThen(action: Runnable, vararg tokens: Any): Command =
        andThen(Commands.runOnce(action, *tokens))

    /** Runs [before] before this command starts. */
    fun beforeStarting(before: ICommand): Command = SequentialCommandGroup(before, this)

    /** Runs [action] once before this command starts. */
    fun beforeStarting(action: Runnable, vararg tokens: Any): Command =
        beforeStarting(Commands.runOnce(action, *tokens))

    /** Runs this command and [parallel] at the same time; finishes when all finish. */
    fun alongWith(vararg parallel: ICommand): Command = ParallelCommandGroup(this, *parallel)

    /** Runs this command and [parallel] at the same time; finishes when the first finishes. */
    fun raceWith(vararg parallel: ICommand): Command = ParallelRaceGroup(this, *parallel)

    /** Runs this command alongside [deadline]; finishes when [deadline] finishes. */
    fun deadlineWith(deadline: ICommand): Command = ParallelDeadlineGroup(deadline, this)

    /** Runs this command as the deadline alongside [parallel]; finishes when this finishes. */
    fun withDeadline(vararg parallel: ICommand): Command = ParallelDeadlineGroup(this, *parallel)

    /** Finishes this command after [millis] milliseconds, even if it would run longer. */
    fun withTimeout(millis: Long): Command = ParallelRaceGroup(this, WaitCommand(millis))

    /** Finishes this command as soon as [condition] becomes true. */
    fun until(condition: BooleanSupplier): Command =
        ParallelRaceGroup(this, WaitUntilCommand(condition))

    /** Runs this command only while [condition] stays true. */
    fun onlyWhile(condition: BooleanSupplier): Command =
        ParallelRaceGroup(this, WaitUntilCommand(BooleanSupplier { !condition.asBoolean }))

    /** Runs this command only if [condition] is true when it starts. */
    fun onlyIf(condition: BooleanSupplier): Command =
        ConditionalCommand(this, Commands.none(), condition)

    /** Skips this command if [condition] is true when it starts. */
    fun unless(condition: BooleanSupplier): Command =
        ConditionalCommand(Commands.none(), this, condition)

    /** Restarts this command from the beginning every time it finishes,
     * until it is interrupted. */
    fun repeatedly(): Command = RepeatCommand(this)

    /** Schedules this command through the scheduler instead of running it
     * inline (for use inside groups); ends when the scheduled command ends. */
    fun asProxy(): Command = asProxy(IScheduler.instance)

    /** Like [asProxy], but on [scheduler] instead of the default scheduler. */
    fun asProxy(scheduler: IScheduler): Command = ProxyCommand(this, scheduler)

    /** Runs [handler] after this command ends, with the reason it ended. */
    fun finallyDo(handler: Consumer<EndCondition>): Command = FinallyDoCommand(this, handler)

    /** Runs [action] after this command ends, whatever the reason. */
    fun finallyDo(action: Runnable): Command =
        FinallyDoCommand(this, Consumer { _: EndCondition -> action.run() })

    /** Runs [handler] only if this command is interrupted. */
    fun handleInterrupt(handler: Runnable): Command =
        FinallyDoCommand(this, Consumer { c -> if (c == EndCondition.INTERRUPTED) handler.run() })

    /** Changes what happens to this command when a same-priority command
     * conflicts with it. */
    fun withInterruptBehavior(behavior: InterruptionBehavior): Command =
        InterruptBehaviorCommand(this, behavior)

    /** Whether this command may run while the OpMode is inactive. */
    fun ignoringDisable(ignore: Boolean): Command = RunsWhenDisabledCommand(this, ignore)

    /** Sets this command's name. */
    fun withName(name: String): Command = NamedCommand(this, name)
}
