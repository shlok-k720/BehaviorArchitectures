package com.rmsftc.cmdlib.trigger

import com.rmsftc.cmdlib.cmd.ICommand
import com.rmsftc.cmdlib.scheduler.IScheduler
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/**
 * A boolean condition with edge-tracked bindings (WPILib v2's `Trigger`).
 * The scheduler polls every registered trigger once per tick; a binding
 * fires when its edge happens. Bindings are created per-OpMode and die
 * with it (the OpMode's `stop()` cancels everything).
 *
 * Gamepad bindings belong in the `ftc` module over FTC `Gamepad`; this core
 * class only knows [BooleanSupplier]s.
 *
 * @param condition the underlying condition.
 * @param scheduler the scheduler bindings schedule on.
 * @param clock nanosecond clock for [debounce] (defaults to
 *   `System.nanoTime()`).
 */
class Trigger(
    private val condition: BooleanSupplier,
    private val scheduler: IScheduler = IScheduler.instance,
    private val clock: Supplier<Long> = Supplier { System.nanoTime() }
) {
    private class Binding(
        val onRising: Runnable?,
        val onFalling: Runnable?
    )

    private var previous: Boolean = condition.asBoolean
    private val bindings = ArrayList<Binding>()

    /** The condition's current value. */
    fun getAsBoolean(): Boolean = condition.asBoolean

    /** Schedules [command] when the condition goes false → true. */
    fun onTrue(command: ICommand): Trigger {
        addBinding(Binding(Runnable { scheduler.schedule(command) }, null))
        return this
    }

    /** Schedules [command] when the condition goes true → false. */
    fun onFalse(command: ICommand): Trigger {
        addBinding(Binding(null, Runnable { scheduler.schedule(command) }))
        return this
    }

    /** Schedules [command] while the condition is true, cancelling it when
     * it goes false. If the command ends on its own while the condition is
     * still true, it restarts (WPILib v2 semantics). */
    fun whileTrue(command: ICommand): Trigger {
        addBinding(
            Binding(
                Runnable { scheduler.schedule(command) },
                Runnable { scheduler.cancel(command) }
            )
        )
        return this
    }

    /** Schedules [command] while the condition is false, cancelling it when
     * it goes true. */
    fun whileFalse(command: ICommand): Trigger {
        addBinding(
            Binding(
                Runnable { scheduler.cancel(command) },
                Runnable { scheduler.schedule(command) }
            )
        )
        return this
    }

    /** Toggles [command] (schedule if stopped, cancel if running) on a
     * rising edge. */
    fun toggleOnTrue(command: ICommand): Trigger {
        addBinding(
            Binding(
                Runnable {
                    if (scheduler.isScheduled(command)) scheduler.cancel(command)
                    else scheduler.schedule(command)
                },
                null
            )
        )
        return this
    }

    /** Toggles [command] on a falling edge. */
    fun toggleOnFalse(command: ICommand): Trigger {
        addBinding(
            Binding(
                null,
                Runnable {
                    if (scheduler.isScheduled(command)) scheduler.cancel(command)
                    else scheduler.schedule(command)
                }
            )
        )
        return this
    }

    /** Schedules [command] on any edge, rising or falling. */
    fun onChange(command: ICommand): Trigger {
        addBinding(
            Binding(
                Runnable { scheduler.schedule(command) },
                Runnable { scheduler.schedule(command) }
            )
        )
        return this
    }

    /** Schedules [command] when the condition is true (same as [onTrue]). */
    fun ifTrue(command: ICommand): Trigger = onTrue(command)

    /** A trigger that is true when both this and [other] are true. */
    fun and(other: Trigger): Trigger =
        Trigger(BooleanSupplier { condition.asBoolean && other.condition.asBoolean }, scheduler, clock)

    /** A trigger that is true when either this or [other] is true. */
    fun or(other: Trigger): Trigger =
        Trigger(BooleanSupplier { condition.asBoolean || other.condition.asBoolean }, scheduler, clock)

    /** A trigger that is true when this one is false. */
    fun negate(): Trigger =
        Trigger(BooleanSupplier { !condition.asBoolean }, scheduler, clock)

    /**
     * A trigger whose output only changes after the input has been stable
     * for [millis] milliseconds (button debouncing). [type] picks which
     * edge waits: [DebounceType.RISING] only delays false → true,
     * [DebounceType.FALLING] only delays true → false, [DebounceType.BOTH]
     * delays both. Plain milliseconds, not `java.time.Duration`: `Duration`
     * is API 26+ on Android and this library targets minSdk 24.
     */
    fun debounce(millis: Long, type: DebounceType): Trigger {
        val nanos = millis * 1_000_000L
        var lastInput = condition.asBoolean
        var lastChangeNanos = clock.get()
        var output = lastInput
        val debounced = BooleanSupplier {
            val now = clock.get()
            val input = condition.asBoolean
            if (input != lastInput) {
                lastInput = input
                lastChangeNanos = now
            }
            val stableFor = now - lastChangeNanos
            when (type) {
                DebounceType.RISING -> {
                    if (!input) {
                        output = false
                    } else if (stableFor >= nanos) {
                        output = true
                    }
                }
                DebounceType.FALLING -> {
                    if (input) {
                        output = true
                    } else if (stableFor >= nanos) {
                        output = false
                    }
                }
                DebounceType.BOTH -> {
                    if (stableFor >= nanos) {
                        output = input
                    }
                }
            }
            output
        }
        return Trigger(debounced, scheduler, clock)
    }

    /** Called by the scheduler once per tick. Detects edges and fires bindings. */
    internal fun poll() {
        val now = condition.asBoolean
        val rising = now && !previous
        val falling = !now && previous
        previous = now
        if (rising) {
            bindings.forEach { it.onRising?.run() }
        }
        if (falling) {
            bindings.forEach { it.onFalling?.run() }
        }
    }

    private fun addBinding(binding: Binding) {
        bindings.add(binding)
        scheduler.addTrigger(this)
    }
}
