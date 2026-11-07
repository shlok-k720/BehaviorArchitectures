package com.rmsftc.ftcImpl.fsm

import com.rmsftc.fsm.Case
import com.rmsftc.fsm.State
import com.rmsftc.ftcImpl.StateMachines.JavaBuilder
import com.rmsftc.ftcImpl.StateMachines.KotlinBuilder
import com.rmsftc.ftcImpl.stateMachine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class BuildersTest {

    @Test
    fun `java builder builds an ordered machine with initial by state`() {
        val a = TestCase(State.of("a"))
        val b = TestCase(State.of("b"))
        b.transitionRequest = State.of("a")
        val m = JavaBuilder.stateMachine { builder ->
            builder.addCase(a)
            builder.addCase(b)
            builder.initial(State.of("b"))
        }
        m.start()
        assertEquals(b, m.getCurrentCase())
        m.update()
        assertEquals(a, m.getCurrentCase())
    }

    @Test
    fun `java builder rejects an initial state with no case`() {
        val a = TestCase(State.of("a"))
        assertThrows(IllegalArgumentException::class.java) {
            JavaBuilder.stateMachine { builder ->
                builder.addCase(a)
                builder.initial(State.of("nope"))
            }
        }
    }

    @Test
    fun `java builder inline conveniences build the built-in cases`() {
        var ran = 0
        var started = 0
        var ended = 0
        val clock = FakeClock()
        val m = JavaBuilder.stateMachine { builder ->
            builder
                .instant(State.of("say")) { }
                .waitMs(State.of("wait"), 50)
                .run(State.of("run")) { ran++ }
                .startEnd(State.of("hold"), { started++ }, { ended++ })
                .print(State.of("print"), "done")
                .clock(clock.supplier())
        }
        // The run case never finishes on its own, so drive it manually:
        // instant -> wait -> run (stays), then force past it.
        m.start()
        m.update()
        assertEquals(State.of("wait"), m.getCurrentState())
        clock.advanceMs(50)
        m.update()
        assertEquals(State.of("run"), m.getCurrentState())
        m.update()
        assertEquals(1, ran)
        m.transitionTo(State.of("hold"))
        m.update()
        assertEquals(1, started)
        assertEquals(State.of("hold"), m.getCurrentState())
        m.transitionTo(State.of("print"))
        m.update()
        assertEquals(1, ended)
        // The print case asked to advance in onStart; it applies on the next tick.
        m.update()
        assertTrue(m.isFinished())
    }

    @Test
    fun `java builder caseBuilder block configures a lambda case`() {
        var ticked = 0
        val m = JavaBuilder.stateMachine { builder ->
            builder.caseBuilder(State.of("a")) { c ->
                c.setOnExecute {
                    ticked++
                    if (ticked == 2) c.transitionToNext()
                }
            }
            builder.addCase(TestCase(State.of("b")))
        }
        m.start()
        m.update()
        assertEquals(State.of("a"), m.getCurrentState())
        m.update()
        assertEquals(State.of("b"), m.getCurrentState())
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
    fun `kotlin dsl rejects duplicate names and unknown initial states`() {
        assertThrows(IllegalArgumentException::class.java) {
            stateMachine {
                case("a") {}
                case("a") {}
            }
        }
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
        assertTrue(fired)
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

    @Test
    fun `kotlin dsl consumer overloads work from Java compatible callers`() {
        val m = KotlinBuilder.stateMachine(Consumer { root ->
            root.case(State.of("a"), Consumer { dsl ->
                dsl.onExecute(Consumer { d -> d.transitionTo("b") })
            })
            root.case(State.of("b"), Consumer { })
        })
        m.start()
        assertEquals(State.of("a"), m.getCurrentState())
        m.update()
        assertEquals(State.of("b"), m.getCurrentState())
    }
}
