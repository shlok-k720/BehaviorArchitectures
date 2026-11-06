package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import java.util.function.Supplier

/**
 * Finishes after [durationMillis] milliseconds have elapsed. The clock is a
 * nanosecond [Supplier] so tests can drive it with a fake clock instead of
 * sleeping; it defaults to `System.nanoTime()`.
 *
 * Plain milliseconds, not `java.time.Duration`: `Duration` is API 26+ on
 * Android and this library targets minSdk 24.
 */
class WaitCommand @JvmOverloads constructor(
    durationMillis: Long,
    private val clock: Supplier<Long> = Supplier { System.nanoTime() }
) : Command() {
    private val durationNanos: Long = durationMillis * 1_000_000L

    private var startNanos: Long = 0

    override fun start() {
        startNanos = clock.get()
    }

    override fun loop() {}

    override fun status(): Status =
        Status.of(clock.get() - startNanos < durationNanos)

    override fun end(condition: EndCondition) {}
}
