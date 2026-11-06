package com.rmsftc.fsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StateMachineTest {

    @Test
    fun `start runs the first case onEnter and update ticks it`() {
        val a = TestCase("a")
        val b = TestCase("b")
        val (m, _) = machine(a, b)
        assertNull(m.getCurrentCase())
        m.start()
        assertEquals(a, m.getCurrentCase())
        assertEquals(1, a.enters)
        assertTrue(m.isRunning())
        assertFalse(m.isFinished())
        m.update()
        m.update()
        assertEquals(2, a.ticks)
        assertEquals(0, b.ticks)
    }

    @Test
    fun `explicit transition switches cases with exit and enter`() {
        val a = TestCase("a")
        val b = TestCase("b")
        val (m, _) = machine(a, b)
        m.start()
        a.transitionTarget = b
        m.update()
        assertEquals(b, m.getCurrentCase())
        assertEquals(1, a.exits)
        assertEquals(1, b.enters)
        assertEquals(1, a.ticks)
        // The new case ticks from the next update, not the same one.
        assertEquals(0, b.ticks)
        m.update()
        assertEquals(1, b.ticks)
    }

    @Test
    fun `declarative transition fires on its condition, first match wins`() {
        val a = TestCase("a")
        val b = TestCase("b")
        val c = TestCase("c")
        var first = false
        var second = false
        a.addTransition({ first }, b)
        a.addTransition({ second }, c)
        val (m, _) = machine(a, b, c)
        m.start()
        m.update()
        assertEquals(a, m.getCurrentCase())
        second = true
        m.update()
        assertEquals(c, m.getCurrentCase())
        // First match wins even when both are true.
        val (m2, _) = machine(a, b, c)
        first = true
        m2.start()
        m2.update()
        assertEquals(b, m2.getCurrentCase())
    }

    @Test
    fun `minTime gates declarative transitions`() {
        val a = TestCase("a")
        val b = TestCase("b")
        var ready = true
        a.minTimeMs(100).addTransition({ ready }, b)
        val (m, clock) = machine(a, b)
        m.start()
        m.update()
        assertEquals(a, m.getCurrentCase())
        clock.advanceMs(99)
        m.update()
        assertEquals(a, m.getCurrentCase())
        clock.advanceMs(1)
        m.update()
        assertEquals(b, m.getCurrentCase())
    }

    @Test
    fun `maxTime goes to the fallback`() {
        val a = TestCase("a")
        val b = TestCase("b")
        val fallback = TestCase("fallback")
        a.maxTimeMs(50).fallback(fallback)
        val (m, clock) = machine(a, b, fallback)
        m.start()
        clock.advanceMs(49)
        m.update()
        assertEquals(a, m.getCurrentCase())
        clock.advanceMs(1)
        m.update()
        assertEquals(fallback, m.getCurrentCase())
    }

    @Test
    fun `maxTime with no fallback advances to the next case`() {
        val a = TestCase("a")
        val b = TestCase("b")
        a.maxTimeMs(50)
        val (m, clock) = machine(a, b)
        m.start()
        clock.advanceMs(50)
        m.update()
        assertEquals(b, m.getCurrentCase())
    }

    @Test
    fun `null-target transition advances, past the end finishes the machine`() {
        val a = TestCase("a")
        val b = TestCase("b")
        a.addTransition({ true })
        b.addTransition({ true })
        val (m, _) = machine(a, b)
        var finishedEvents = 0
        m.addListener(object : StateMachineListener {
            override fun onFinished(machine: StateMachine) {
                finishedEvents++
            }
        })
        m.start()
        m.update()
        assertEquals(b, m.getCurrentCase())
        assertEquals(1, a.exits)
        m.update()
        assertTrue(m.isFinished())
        assertFalse(m.isRunning())
        assertNull(m.getCurrentCase())
        assertEquals(1, b.exits)
        assertEquals(1, finishedEvents)
        // Updates after finishing are no-ops.
        m.update()
        assertEquals(1, b.ticks)
    }

    @Test
    fun `stop exits the current case and reset plus start begins again`() {
        val a = TestCase("a")
        val b = TestCase("b")
        val (m, _) = machine(a, b)
        var stopped = 0
        m.addListener(object : StateMachineListener {
            override fun onStopped(machine: StateMachine) {
                stopped++
            }
        })
        m.start()
        m.update()
        m.stop()
        assertTrue(m.isFinished())
        assertEquals(1, a.exits)
        assertEquals(1, stopped)
        m.update()
        assertEquals(1, a.ticks)
        m.reset()
        m.start()
        assertEquals(a, m.getCurrentCase())
        assertEquals(2, a.enters)
        m.update()
        assertEquals(2, a.ticks)
    }

    @Test
    fun `start at a given case begins mid-list, unknown case throws`() {
        val a = TestCase("a")
        val b = TestCase("b")
        val outsider = TestCase("outsider")
        val (m, _) = machine(a, b)
        m.start(b)
        assertEquals(b, m.getCurrentCase())
        assertEquals(0, a.enters)
        assertThrows(IllegalArgumentException::class.java) { m.start(outsider) }
    }

    @Test
    fun `listeners see started, transitions and finished in order`() {
        val a = TestCase("a")
        val b = TestCase("b")
        val events = ArrayList<String>()
        val (m, _) = machine(a, b)
        m.addListener(object : StateMachineListener {
            override fun onStarted(machine: StateMachine) {
                events.add("started")
            }

            override fun onTransition(machine: StateMachine, from: Case, to: Case) {
                events.add("transition ${from.name} -> ${to.name}")
            }

            override fun onFinished(machine: StateMachine) {
                events.add("finished")
            }
        })
        a.addTransition({ true })
        b.addTransition({ true })
        m.start()
        m.update()
        m.update()
        assertEquals(listOf("started", "transition a -> b", "finished"), events)
    }

    @Test
    fun `transitionTo from outside switches at the end of the tick`() {
        val a = TestCase("a")
        val b = TestCase("b")
        val (m, _) = machine(a, b)
        m.start()
        m.transitionTo(b)
        assertEquals(a, m.getCurrentCase())
        m.update()
        assertEquals(b, m.getCurrentCase())
        assertEquals(1, a.exits)
        assertEquals(1, b.enters)
    }

    @Test
    fun `self-transition restarts the case with exit and enter`() {
        val a = TestCase("a")
        val (m, clock) = machine(a)
        a.minTimeMs(100)
        m.start()
        clock.advanceMs(100)
        a.transitionTarget = a
        m.update()
        assertEquals(a, m.getCurrentCase())
        assertEquals(1, a.exits)
        assertEquals(2, a.enters)
        // The timer restarted: minTime gates again.
        a.addTransition({ true }, a)
        m.update()
        assertEquals(1, a.exits)
    }

    @Test
    fun `transition to a case outside the machine throws`() {
        val a = TestCase("a")
        val outsider = TestCase("outsider")
        val (m, _) = machine(a)
        m.start()
        a.transitionTarget = outsider
        assertThrows(IllegalStateException::class.java) { m.update() }
    }

    @Test
    fun `case validation rejects bad timing and null targets`() {
        val a = TestCase("a")
        assertThrows(IllegalArgumentException::class.java) { a.minTimeMs(-1) }
        assertThrows(IllegalArgumentException::class.java) { a.maxTimeMs(-1) }
        a.minTimeMs(100)
        assertThrows(IllegalArgumentException::class.java) { a.maxTimeMs(50) }
        assertThrows(IllegalArgumentException::class.java) { a.requestNullTransition() }
        assertThrows(IllegalArgumentException::class.java) { a.addTransition(null as java.util.function.Supplier<Boolean>?) }
    }

    @Test
    fun `simple case runs its lambdas`() {
        var entered = 0
        var ticked = 0
        var exited = 0
        val c = SimpleCase("s", { entered++ }, { ticked++ }, { exited++ })
        val (m, _) = machine(c)
        m.start()
        assertEquals(1, entered)
        m.update()
        m.update()
        assertEquals(2, ticked)
        m.stop()
        assertEquals(1, exited)
    }

    @Test
    fun `explicit transition wins over a declarative one on the same tick`() {
        val a = TestCase("a")
        val b = TestCase("b")
        val c = TestCase("c")
        a.addTransition({ true }, c)
        val (m, _) = machine(a, b, c)
        m.start()
        a.transitionTarget = b
        m.update()
        assertEquals(b, m.getCurrentCase())
    }
}
