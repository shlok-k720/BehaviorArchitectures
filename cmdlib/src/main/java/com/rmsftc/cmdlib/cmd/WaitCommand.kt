package com.rmsftc.cmdlib.cmd

import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import java.time.Duration
import java.util.function.Supplier

/**
 * Finishes after [duration] has elapsed. The clock is a nanosecond
 * [Supplier] so tests can drive it with a fake clock instead of sleeping;
 * it defaults to `System.nanoTime()`.
 */
class WaitCommand(
    private val durationNanos: Long,
    private val clock: Supplier<Long>
) : Command() {
    constructor(duration: Duration) : this(duration.toNanos(), Supplier { System.nanoTime() })
    constructor(duration: Duration, clock: Supplier<Long>) : this(duration.toNanos(), clock)
    constructor(millis: Long) : this(Duration.ofMillis(millis).toNanos(), Supplier { System.nanoTime() })

    private var startNanos: Long = 0

    override fun start() {
        startNanos = clock.get()
    }

    override fun loop() {}

    override fun status(): Status =
        Status.of(clock.get() - startNanos < durationNanos)

    override fun end(endCondition: EndCondition) {}
}
