package com.rmsftc.cmdlib

import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.cmdlib.subsystem.Subsystem

/**
 * A scripted command for tests (the `TestBehaviorNode` of cmdlib): returns
 * the given statuses in order, one per tick, then [Status.FINISHED] forever
 * (or repeats the last result when [repeatLast] is true). Records lifecycle
 * calls so tests can assert start/loop/end counts and the end reason.
 */
class TestCommand(
    private val results: List<Status>,
    private val repeatLast: Boolean = false,
    vararg requiredSubsystems: Subsystem
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
        addRequirements(*requiredSubsystems)
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
class RunForeverCommand(vararg requiredSubsystems: Subsystem) : Command() {
    var loops = 0
        private set
    var ends = 0
        private set
    var lastEndCondition: EndCondition? = null
        private set

    init {
        addRequirements(*requiredSubsystems)
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

class TestSubsystem(name: String = "test") : com.rmsftc.cmdlib.subsystem.SubsystemBase() {
    var periodicCalls = 0
        private set

    init {
        setName(name)
    }

    override fun periodic() {
        periodicCalls++
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
