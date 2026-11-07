package com.rmsftc.ftcImpl.fsm

import com.rmsftc.fsm.Case
import com.rmsftc.fsm.State
import com.rmsftc.fsm.StateMachine

import java.util.function.Supplier

/**
 * Test helpers copied from the fsm module's tests (fsm/src/test/.../TestCase.kt):
 * test sources are not shared across modules, so the moved builder tests and
 * the converter/manager tests use this copy. Keep the two in sync.
 */

/** States for the tests, as an enum (string form = the constant name). */
enum class TestStates {
    IDLE, WORKING, DONE
}

/** A scripted Case for tests: counts lifecycle calls, optionally requests a transition. */
class TestCase(state: State) : Case(state) {
    var starts = 0
        private set
    var ticks = 0
        private set
    var transitions = 0
        private set
    var lastTransitionOther: Case? = null
        private set

    /** If set, onExecute() requests a transition to this state once, then clears it. */
    var transitionRequest: State? = null

    /** If true, onExecute() requests an advance once, then clears it. */
    var advanceOnExecute = false

    override fun onStart() {
        starts++
    }

    override fun onExecute() {
        ticks++
        transitionRequest?.let {
            transition(it)
            transitionRequest = null
        }
        if (advanceOnExecute) {
            transitionToNext()
            advanceOnExecute = false
        }
    }

    override fun onTransition(other: Case?) {
        transitions++
        lastTransitionOther = other
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
