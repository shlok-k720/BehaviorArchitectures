package com.rmsftc.cmdlib

import com.rmsftc.cmdlib.cmd.Command

/**
 * A scripted command for tests (the `TestBehaviorNode` of cmdlib): returns
 * the given statuses in order, one per tick, then [Status.FINISHED] forever
 * (or repeats the last result when [repeatLast] is true). Records lifecycle
 * calls so tests can assert start/loop/end counts and the end reason.
 */
class TestCommand(
    private val results: List<Status>,
    private val repeatLast: Boolean = false,
    vararg tokens: Any
) : Command() {
    var starts = 0
        private set
    var loops = 0
        private set
    var ends = 0
        private set
    var lastEndCondition: EndCondition? = null
        private set

    private var index = 0

    init {
        addRequirements(*tokens)
    }

    override fun start() {
        starts++
    }

    override fun loop() {
        loops++
    }

    override fun status(): Status {
        if (index < results.size) {
            return results[index++]
        }
        return if (repeatLast && results.isNotEmpty()) results.last() else Status.FINISHED
    }

    override fun end(endCondition: EndCondition) {
        ends++
        lastEndCondition = endCondition
    }
}

/** A command that runs forever (status always RUNNING). */
class RunForeverCommand(vararg tokens: Any) : Command() {
    var loops = 0
        private set
    var ends = 0
        private set
    var lastEndCondition: EndCondition? = null
        private set

    init {
        addRequirements(*tokens)
    }

    override fun start() {}
    override fun loop() {
        loops++
    }

    override fun status(): Status = Status.RUNNING

    override fun end(endCondition: EndCondition) {
        ends++
        lastEndCondition = endCondition
    }
}

/** A nanosecond clock tests can advance by hand (no sleeping). */
class FakeClock(var nanos: Long = 0) : java.util.function.Supplier<Long> {
    override fun get(): Long = nanos
    fun advance(durationNanos: Long) {
        nanos += durationNanos
    }

    fun advanceMs(millis: Long) {
        nanos += millis * 1_000_000L
    }
}
