package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.subsystem.Subsystem
import java.time.Duration
import java.util.EnumMap
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Static factories for building commands from lambdas (WPILib v2's
 * `Commands`). These are the usual way to make commands; subclass [Command]
 * only when a factory cannot express what you need.
 *
 * Every factory that takes requirements declares them on the returned
 * command, so the scheduler enforces them.
 */
object Commands {
    /** A command that finishes immediately and does nothing. */
    @JvmStatic
    fun none(): Command = InstantCommand()

    /** A command that does nothing and never finishes, holding [requirements]. */
    @JvmStatic
    fun idle(vararg requirements: Subsystem): Command =
        RunCommand(Runnable {}, *requirements)

    /** Runs [action] once, then finishes. */
    @JvmStatic
    fun runOnce(action: Runnable, vararg requirements: Subsystem): Command =
        InstantCommand(action, *requirements)

    /** Runs [action] on every tick, forever. */
    @JvmStatic
    fun run(action: Runnable, vararg requirements: Subsystem): Command =
        RunCommand(action, *requirements)

    /** Runs [start] once on start and [end] once on end, never finishing alone. */
    @JvmStatic
    fun startEnd(start: Runnable, end: Runnable, vararg requirements: Subsystem): Command =
        StartEndCommand(start, end, *requirements)

    /** Runs [run] on every tick and [end] once on end, never finishing alone. */
    @JvmStatic
    fun runEnd(run: Runnable, end: Runnable, vararg requirements: Subsystem): Command =
        FunctionalCommand(
            onLoop = run,
            statusSupplier = Supplier { Status.RUNNING },
            onEnd = Consumer { _: EndCondition -> end.run() },
            requiredSubsystems = requirements
        )

    /** Runs [start] once on start, then [run] on every tick, never finishing alone. */
    @JvmStatic
    fun startRun(start: Runnable, run: Runnable, vararg requirements: Subsystem): Command =
        FunctionalCommand(
            onStart = start,
            onLoop = run,
            statusSupplier = Supplier { Status.RUNNING },
            requiredSubsystems = requirements
        )

    /** Prints [message] once, then finishes. */
    @JvmStatic
    fun print(message: String): Command = PrintCommand(message)

    /** Finishes after [millis] milliseconds. */
    @JvmStatic
    fun waitMs(millis: Long): Command = WaitCommand(millis)

    /** Finishes after [duration] has elapsed. */
    @JvmStatic
    fun waitMs(duration: Duration): Command = WaitCommand(duration)

    /** Finishes as soon as [condition] becomes true. */
    @JvmStatic
    fun waitUntil(condition: BooleanSupplier): Command = WaitUntilCommand(condition)

    /** Runs [onTrue] or [onFalse], chosen once at start. */
    @JvmStatic
    fun either(onTrue: ICommand, onFalse: ICommand, selector: BooleanSupplier): Command =
        ConditionalCommand(onTrue, onFalse, selector)

    /** Runs the command mapped to the key [selector] returns, chosen once at start. */
    @JvmStatic
    fun <K> select(commands: Map<K, ICommand>, selector: Supplier<K>): Command =
        SelectCommand(commands, selector)

    /** Runs the first branch whose condition is true, checked in map order once at start. */
    @JvmStatic
    fun branch(branches: Map<BooleanSupplier, ICommand>): Command =
        BranchCommand(branches)

    /** Runs the command mapped to the enum value [selector] returns, chosen once at start. */
    @JvmStatic
    fun <T : Enum<T>> match(commands: EnumMap<T, ICommand>, selector: Supplier<T>): Command =
        MatchCommand(commands, selector)

    /** Runs the command mapped to the enum value [selector] returns, chosen once at start. */
    @JvmStatic
    fun <T : Enum<T>> match(
        keyType: Class<T>,
        commands: Map<T, ICommand>,
        selector: Supplier<T>
    ): Command {
        val enumMap = EnumMap<T, ICommand>(keyType)
        enumMap.putAll(commands)
        return MatchCommand(enumMap, selector)
    }

    /**
     * Builds the command lazily at start time. The requirements must be
     * declared up front; the built command is rejected if it needs more.
     */
    @JvmStatic
    fun defer(supplier: Supplier<ICommand>, vararg requirements: Subsystem): Command =
        DeferredCommand(supplier, *requirements)

    /** Like [defer], but the built command is scheduled through the scheduler. */
    @JvmStatic
    fun deferredProxy(supplier: Supplier<ICommand>, vararg requirements: Subsystem): Command =
        ProxyCommand(DeferredCommand(supplier, *requirements))

    /** Runs [commands] in order, one at a time. */
    @JvmStatic
    fun sequence(vararg commands: ICommand): Command = SequentialCommandGroup(*commands)

    /** Runs [commands] in order, then starts over, until interrupted. */
    @JvmStatic
    fun repeatingSequence(vararg commands: ICommand): Command =
        SequentialCommandGroup(*commands).repeatedly()

    /** Runs [commands] at the same time; finishes when all finish. */
    @JvmStatic
    fun parallel(vararg commands: ICommand): Command = ParallelCommandGroup(*commands)

    /** Runs [commands] at the same time; finishes when the first finishes. */
    @JvmStatic
    fun race(vararg commands: ICommand): Command = ParallelRaceGroup(*commands)

    /** Runs [deadline] alongside [others]; finishes when [deadline] finishes. */
    @JvmStatic
    fun deadline(deadline: ICommand, vararg others: ICommand): Command =
        ParallelDeadlineGroup(deadline, *others)
}
