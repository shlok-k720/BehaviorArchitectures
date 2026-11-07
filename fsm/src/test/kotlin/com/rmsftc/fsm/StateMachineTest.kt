package com.rmsftc.fsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StateMachineTest {

    @Test
    fun `start runs the first case onStart and update ticks it`() {
        val a = TestCase(State.of(TestStates.IDLE))
        val b = TestCase(State.of(TestStates.WORKING))
        val (m, _) = machine(a, b)
        assertNull(m.getCurrentCase())
        assertNull(m.getCurrentState())
        m.start()
        assertEquals(a, m.getCurrentCase())
        assertEquals(State.of(TestStates.IDLE), m.getCurrentState())
        assertEquals(1, a.starts)
        assertTrue(m.isRunning())
        assertFalse(m.isFinished())
        m.update()
        m.update()
        assertEquals(2, a.ticks)
        assertEquals(0, b.ticks)
    }

    @Test
    fun `transition by state switches cases, onTransition sees the incoming case`() {
        val a = TestCase(State.of("a"))
        val b = TestCase(State.of("b"))
        val (m, _) = machine(a, b)
        m.start()
        a.transitionRequest = State.of("b")
        m.update()
        assertEquals(b, m.getCurrentCase())
        assertEquals(1, a.transitions)
        assertEquals(b, a.lastTransitionOther)
        assertEquals(1, b.starts)
        assertEquals(1, a.ticks)
        assertEquals(0, b.ticks)
    }

    @Test
    fun `transitionToNext advances, past the end finishes with onTransition null`() {
        val a = TestCase(State.of("a"))
        val b = TestCase(State.of("b"))
        val (m, _) = machine(a, b)
        m.start()
        a.advanceOnExecute = true
        m.update()
        assertEquals(b, m.getCurrentCase())
        assertEquals(1, a.transitions)
        b.advanceOnExecute = true
        m.update()
        assertTrue(m.isFinished())
        assertFalse(m.isRunning())
        assertNull(m.getCurrentCase())
        assertEquals(1, b.transitions)
        assertNull(b.lastTransitionOther)
        m.update()
        assertEquals(1, b.ticks)
    }

    @Test
    fun `transitionTo from outside switches at the end of the tick`() {
        val a = TestCase(State.of("a"))
        val b = TestCase(State.of("b"))
        val (m, _) = machine(a, b)
        m.start()
        m.transitionTo(State.of("b"))
        assertEquals(a, m.getCurrentCase())
        m.update()
        assertEquals(b, m.getCurrentCase())
        assertEquals(1, a.transitions)
    }

    @Test
    fun `stop runs onTransition null and reset plus start begins again`() {
        val a = TestCase(State.of("a"))
        val b = TestCase(State.of("b"))
        val (m, _) = machine(a, b)
        m.start()
        m.update()
        m.stop()
        assertTrue(m.isFinished())
        assertEquals(1, a.transitions)
        assertNull(a.lastTransitionOther)
        m.update()
        assertEquals(1, a.ticks)
        m.reset()
        m.start()
        assertEquals(a, m.getCurrentCase())
        assertEquals(2, a.starts)
        m.update()
        assertEquals(2, a.ticks)
    }

    @Test
    fun `start at a state begins mid-list, unknown state throws`() {
        val a = TestCase(State.of("a"))
        val b = TestCase(State.of("b"))
        val (m, _) = machine(a, b)
        m.start(State.of("b"))
        assertEquals(b, m.getCurrentCase())
        assertEquals(0, a.starts)
        assertThrows(IllegalArgumentException::class.java) { m.start(State.of("nope")) }
    }

    @Test
    fun `duplicate state in the list throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            machine(TestCase(State.of("a")), TestCase(State.of("a")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            StateMachine(TestCase(State.of(TestStates.IDLE)), TestCase(State.of("IDLE")))
        }
    }

    @Test
    fun `transition to an unknown state throws`() {
        val a = TestCase(State.of("a"))
        val (m, _) = machine(a)
        m.start()
        assertThrows(IllegalArgumentException::class.java) { m.transitionTo(State.of("nope")) }
        assertThrows(IllegalArgumentException::class.java) { a.transition(State.of("nope")) }
    }

    @Test
    fun `transition while not running throws, and a detached case cannot transition`() {
        val a = TestCase(State.of("a"))
        val b = TestCase(State.of("b"))
        val (m, _) = machine(a, b)
        assertThrows(IllegalStateException::class.java) { m.transitionTo(State.of("b")) }
        assertThrows(IllegalStateException::class.java) { m.transitionToNext() }
        val detached = TestCase(State.of("detached"))
        assertThrows(IllegalStateException::class.java) { detached.transition(State.of("a")) }
        assertThrows(IllegalStateException::class.java) { detached.transitionToNext() }
    }

    @Test
    fun `self-transition restarts the case`() {
        val a = TestCase(State.of("a"))
        val (m, _) = machine(a)
        m.start()
        a.transitionRequest = State.of("a")
        m.update()
        assertEquals(a, m.getCurrentCase())
        assertEquals(1, a.transitions)
        assertEquals(a, a.lastTransitionOther)
        assertEquals(2, a.starts)
    }

    @Test
    fun `caseBuilder runs its lambdas and can transition through itself`() {
        val a = CaseBuilder(State.of("a"))
        var entered = 0
        var ticked = 0
        var exitedOther: Case? = null
        a.setOnStart { entered++ }
        a.setOnExecute {
            ticked++
            if (ticked == 2) a.transitionToNext()
        }
        a.setOnTransition { other -> exitedOther = other }
        val b = TestCase(State.of("b"))
        val (m, _) = machine(a, b)
        m.start()
        assertEquals(1, entered)
        m.update()
        assertEquals(a, m.getCurrentCase())
        m.update()
        assertEquals(b, m.getCurrentCase())
        assertEquals(b, exitedOther)
    }

    @Test
    fun `caseBuilder defaults are no-ops and run forever until transitioned away`() {
        val a = CaseBuilder.build(State.of("a"))
        val b = TestCase(State.of("b"))
        val (m, _) = machine(a, b)
        m.start()
        repeat(5) { m.update() }
        assertEquals(a, m.getCurrentCase())
        m.transitionTo(State.of("b"))
        m.update()
        assertEquals(b, m.getCurrentCase())
    }

    @Test
    fun `last transition request in a tick wins`() {
        val a = TestCase(State.of("a"))
        val b = TestCase(State.of("b"))
        val c = TestCase(State.of("c"))
        val (m, _) = machine(a, b, c)
        m.start()
        m.transitionTo(State.of("b"))
        m.transitionTo(State.of("c"))
        m.update()
        assertEquals(c, m.getCurrentCase())
    }
}
