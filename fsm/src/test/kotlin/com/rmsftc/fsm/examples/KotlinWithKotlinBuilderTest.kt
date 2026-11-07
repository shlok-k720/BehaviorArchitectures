package com.rmsftc.fsm.examples

import com.rmsftc.fsm.FakeClock
import com.rmsftc.fsm.State
import com.rmsftc.fsm.builders.stateMachine
import com.rmsftc.fsm.cases.WaitCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Example: the same intake -> wait -> shoot machine, built in Kotlin with
 * the Kotlin DSL. The intake case runs until the intake is full, the wait
 * case pauses 100 ms, and the shoot case runs until the shot is done.
 */
class KotlinWithKotlinBuilderTest {

    @Test
    fun `builds and runs an intake wait shoot machine`() {
        var full = false
        var shot = false
        val clock = FakeClock()

        val machine = stateMachine {
            case("intake") {
                onExecute { if (full) transitionTo("wait") }
            }
            add(WaitCase(State.of("wait"), 100))
            case("shoot") {
                onExecute { if (shot) transitionToNext() }
            }
            clock(clock.supplier())
        }

        machine.start()
        assertEquals(State.of("intake"), machine.getCurrentState())

        machine.update()
        assertEquals(State.of("intake"), machine.getCurrentState())

        full = true
        machine.update()
        assertEquals(State.of("wait"), machine.getCurrentState())

        clock.advanceMs(99)
        machine.update()
        assertEquals(State.of("wait"), machine.getCurrentState())

        clock.advanceMs(1)
        machine.update()
        assertEquals(State.of("shoot"), machine.getCurrentState())

        shot = true
        machine.update()
        assertTrue(machine.isFinished())
    }
}
