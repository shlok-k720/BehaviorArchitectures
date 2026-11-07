package com.rmsftc.fsm

import com.rmsftc.fsm.builders.JavaBuilder
import com.rmsftc.fsm.builders.stateMachine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BuildersTest {

    @Test
    fun `java builder builds an ordered machine with initial by state`() {
        val a = TestCase(State.of("a"))
        val b = TestCase(State.of("b"))
        b.transitionRequest = State.of("a")
        val m = JavaBuilder.stateMachine()
            .addCase(a)
            .addCase(b)
            .initial(State.of("b"))
            .build()
        m.start()
        assertEquals(b, m.getCurrentCase())
        m.update()
        assertEquals(a, m.getCurrentCase())
    }

    @Test
    fun `java builder rejects an initial state with no case`() {
        val a = TestCase(State.of("a"))
        assertThrows(IllegalArgumentException::class.java) {
            JavaBuilder.stateMachine().addCase(a).initial(State.of("nope")).build()
        }
    }

    @Test
    fun `kotlin dsl builds a machine with string, enum and state cases`() {
        var full = false
        val clock = FakeClock()
        val m = stateMachine {
            case("intake") {
                onExecute { if (full) transitionTo(TestStates.WORKING) }
            }
            case(TestStates.WORKING) {
                onExecute { transitionToNext() }
            }
            case(State.of("done")) {}
            clock(clock.supplier())
        }
        m.start()
        assertEquals(State.of("intake"), m.getCurrentState())
        m.update()
        assertEquals(State.of("intake"), m.getCurrentState())
        full = true
        m.update()
        assertEquals(State.of("WORKING"), m.getCurrentState())
        m.update()
        assertEquals(State.of("done"), m.getCurrentState())
    }

    @Test
    fun `kotlin dsl initial picks the start case by name and enum`() {
        val m = stateMachine {
            case("a") {}
            case(TestStates.WORKING) {}
            initial(TestStates.WORKING)
        }
        m.start()
        assertEquals(State.of("WORKING"), m.getCurrentState())
    }

    @Test
    fun `kotlin dsl rejects an unknown initial state`() {
        assertThrows(IllegalArgumentException::class.java) {
            stateMachine {
                case("a") {}
                initial("nope")
            }
        }
    }

    @Test
    fun `kotlin dsl mixes prebuilt cases with declared ones`() {
        val prebuilt = TestCase(State.of("prebuilt"))
        var fired = false
        val m = stateMachine {
            add(prebuilt)
            case("next") {
                onExecute { fired = true }
            }
        }
        prebuilt.advanceOnExecute = true
        m.start()
        assertEquals(State.of("prebuilt"), m.getCurrentState())
        m.update()
        assertEquals(State.of("next"), m.getCurrentState())
        m.update()
        assertEquals(true, fired)
    }

    @Test
    fun `kotlin dsl onTransition receives the incoming case`() {
        var seen: Case? = null
        val m = stateMachine {
            case("a") {
                onTransition { other -> seen = other }
                onExecute { transitionTo("b") }
            }
            case("b") {}
        }
        m.start()
        m.update()
        assertEquals(State.of("b"), seen?.state)
    }
}
