package com.rmsftc.fsm

import com.rmsftc.fsm.builders.JavaBuilder
import com.rmsftc.fsm.builders.stateMachine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildersTest {

    @Test
    fun `java builder builds an ordered machine with initial and listener`() {
        val a = TestCase("a")
        val b = TestCase("b")
        val seen = ArrayList<String>()
        val m = JavaBuilder.stateMachine()
            .addCase(a)
            .addCase(b)
            .initial(b)
            .onTransition { from, to -> seen.add("${from.name}->${to.name}") }
            .build()
        b.addTransition({ true }, a)
        m.start()
        assertEquals(b, m.getCurrentCase())
        m.update()
        assertEquals(a, m.getCurrentCase())
        assertEquals(listOf("b->a"), seen)
    }

    @Test
    fun `java builder rejects an initial case that was never added`() {
        val a = TestCase("a")
        val outsider = TestCase("outsider")
        assertThrows(IllegalArgumentException::class.java) {
            JavaBuilder.stateMachine().addCase(a).initial(outsider).build()
        }
    }

    @Test
    fun `kotlin dsl builds a machine with named transitions and timing`() {
        var full = false
        val clock = FakeClock()
        val m = stateMachine {
            case("intake") {
                onEnter { }
                transition({ full }, "shoot")
                maxTimeMs(3000)
                fallback("shoot")
            }
            case("shoot") {
                onTick { }
            }
            clock(clock.supplier())
        }
        m.start()
        assertEquals("intake", m.getCurrentCase()?.name)
        clock.advanceMs(2999)
        m.update()
        assertEquals("intake", m.getCurrentCase()?.name)
        clock.advanceMs(1)
        m.update()
        assertEquals("shoot", m.getCurrentCase()?.name)
        // And the declarative path: a fresh machine fires on the condition.
        full = true
        val m2 = stateMachine {
            case("intake") {
                transition({ full }, "shoot")
            }
            case("shoot") {}
        }
        m2.start()
        m2.update()
        assertEquals("shoot", m2.getCurrentCase()?.name)
    }

    @Test
    fun `kotlin dsl initial picks the start case by name`() {
        val m = stateMachine {
            case("a") {}
            case("b") {}
            initial("b")
        }
        m.start()
        assertEquals("b", m.getCurrentCase()?.name)
    }

    @Test
    fun `kotlin dsl rejects duplicate names and unknown targets`() {
        assertThrows(IllegalArgumentException::class.java) {
            stateMachine {
                case("a") {}
                case("a") {}
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            stateMachine {
                case("a") {
                    transition({ true }, "nope")
                }
                case("b") {}
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
        val prebuilt = TestCase("prebuilt")
        var fired = false
        val m = stateMachine {
            add(prebuilt)
            case("next") {
                onTick { fired = true }
            }
        }
        prebuilt.addTransition({ true })
        m.start()
        assertEquals("prebuilt", m.getCurrentCase()?.name)
        m.update()
        assertEquals("next", m.getCurrentCase()?.name)
        m.update()
        assertTrue(fired)
    }
}
