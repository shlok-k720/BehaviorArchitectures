package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.utils.Clock

/**
 * Finishes after [durationMillis] milliseconds have elapsed. The clock is a
 * nanosecond [Clock] so tests can drive it with a fake clock instead of
 * sleeping; it defaults to `System.nanoTime()`.
 *
 * Plain milliseconds, not `java.time.Duration`: `Duration` is API 26+ on
 * Android and this library targets minSdk 24.
 */
class WaitCommand @JvmOverloads constructor(
    durationMillis: Long,
    private val clock: Clock = Clock.SYSTEM
) : Command() {
    private val durationNanos: Long = durationMillis * 1_000_000L

    private var startNanos: Long = 0

    override fun start() {
        startNanos = clock.nowNanos()
    }

    override fun loop() {}

    override fun status(): Status =
        Status.of(clock.nowNanos() - startNanos < durationNanos)

    override fun end(condition: EndCondition) {}
}
