package com.rmsftc.fsm

import java.util.function.Supplier

/** A scripted Case for tests: counts lifecycle calls, optionally requests a transition. */
class TestCase(name: String? = null) : Case(name) {
    var enters = 0
        private set
    var ticks = 0
        private set
    var exits = 0
        private set

    /** If set, execute() requests a transition to this case once, then clears it. */
    var transitionTarget: Case? = null

    override fun execute() {
        ticks++
        transitionTarget?.let {
            transition(it)
            transitionTarget = null
        }
    }

    override fun onEnter() {
        enters++
    }

    override fun onExit() {
        exits++
    }

    /** Calls the protected transition(null) to prove it throws. */
    fun requestNullTransition() {
        transition(null as Case?)
    }
}

/** A fake nanosecond clock: tests advance time instead of sleeping. */
class FakeClock {
    var nanos = 0L
        private set

    fun advanceMs(ms: Long) {
        nanos += ms * 1_000_000L
    }

    fun supplier(): Supplier<Long> = Supplier { nanos }
}

fun machine(vararg cases: Case, clock: FakeClock = FakeClock()): Pair<StateMachine, FakeClock> =
    StateMachine(clock.supplier(), *cases) to clock
