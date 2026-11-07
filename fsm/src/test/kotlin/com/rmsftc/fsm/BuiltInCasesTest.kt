package com.rmsftc.fsm

import com.rmsftc.fsm.cases.InstantCase
import com.rmsftc.fsm.cases.PrintCase
import com.rmsftc.fsm.cases.RunCase
import com.rmsftc.fsm.cases.StartEndCase
import com.rmsftc.fsm.cases.WaitCase
import com.rmsftc.fsm.cases.WaitUntilCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuiltInCasesTest {

    @Test
    fun `wait case advances after its time, using the machine clock`() {
        val wait = WaitCase(State.of("wait"), 100)
        val next = TestCase(State.of("next"))
        val (m, clock) = machine(wait, next)
        m.start()
        clock.advanceMs(99)
        m.update()
        assertEquals(wait, m.getCurrentCase())
        clock.advanceMs(1)
        m.update()
        assertEquals(next, m.getCurrentCase())
    }

    @Test
    fun `wait case with a target jumps there instead of advancing`() {
        val wait = WaitCase(State.of("wait"), 10, State.of("done"))
        val middle = TestCase(State.of("middle"))
        val done = TestCase(State.of("done"))
        val (m, clock) = machine(wait, middle, done)
        m.start()
        clock.advanceMs(10)
        m.update()
        assertEquals(done, m.getCurrentCase())
        assertEquals(0, middle.starts)
    }

    @Test
    fun `wait until case advances when its condition holds`() {
        var full = false
        val wait = WaitUntilCase(State.of("wait"), { full })
        val next = TestCase(State.of("next"))
        val (m, _) = machine(wait, next)
        m.start()
        m.update()
        assertEquals(wait, m.getCurrentCase())
        full = true
        m.update()
        assertEquals(next, m.getCurrentCase())
    }

    @Test
    fun `run case ticks every tick and never finishes on its own`() {
        var ticks = 0
        val run = RunCase(State.of("run")) { ticks++ }
        val (m, _) = machine(run)
        m.start()
        repeat(3) { m.update() }
        assertEquals(3, ticks)
        assertEquals(run, m.getCurrentCase())
    }

    @Test
    fun `instant case runs once then advances on the first tick`() {
        var ran = 0
        val instant = InstantCase(State.of("instant")) { ran++ }
        val next = TestCase(State.of("next"))
        val (m, _) = machine(instant, next)
        m.start()
        assertEquals(1, ran)
        assertEquals(instant, m.getCurrentCase())
        m.update()
        assertEquals(next, m.getCurrentCase())
        assertEquals(1, ran)
    }

    @Test
    fun `start end case runs its end action when transitioned away`() {
        var started = 0
        var ended = 0
        val se = StartEndCase(State.of("se"), { started++ }, { ended++ })
        val next = TestCase(State.of("next"))
        val (m, _) = machine(se, next)
        m.start()
        assertEquals(1, started)
        assertEquals(0, ended)
        m.transitionToNext()
        m.update()
        assertEquals(next, m.getCurrentCase())
        assertEquals(1, ended)
    }

    @Test
    fun `print case prints then advances`() {
        val print = PrintCase(State.of("print"), "hello from the machine")
        val next = TestCase(State.of("next"))
        val (m, _) = machine(print, next)
        m.start()
        m.update()
        assertEquals(next, m.getCurrentCase())
    }

    @Test
    fun `built-in cases validate their arguments`() {
        assertThrows(IllegalArgumentException::class.java) { WaitCase(State.of("w"), -1) }
        assertThrows(IllegalArgumentException::class.java) {
            WaitUntilCase(State.of("w"), null as (() -> Boolean)?)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RunCase(State.of("r"), null as Runnable?)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstantCase(State.of("i"), null as Runnable?)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StartEndCase(State.of("s"), null as Runnable?, {})
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrintCase(State.of("p"), null as String?)
        }
    }

    @Test
    fun `a sequence of built-in cases runs end to end`() {
        val log = ArrayList<String>()
        val (m, clock) = machine(
            InstantCase(State.of("say")) { log.add("say") },
            WaitCase(State.of("wait"), 50),
            WaitUntilCase(State.of("until"), { log.size >= 1 }),
            RunCase(State.of("run")) { log.add("run") },
            clock = FakeClock()
        )
        m.start()
        m.update() // instant -> wait
        assertEquals(State.of("wait"), m.getCurrentState())
        clock.advanceMs(50)
        m.update() // wait -> until (condition already true)
        m.update() // until -> run
        assertEquals(State.of("run"), m.getCurrentState())
        m.update()
        assertTrue(log.contains("run"))
        assertTrue(m.isRunning())
    }
}
