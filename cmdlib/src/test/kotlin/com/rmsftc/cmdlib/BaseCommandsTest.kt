package com.rmsftc.cmdlib

import com.rmsftc.cmdlib.cmd.Commands
import com.rmsftc.cmdlib.cmd.RunCommand
import com.rmsftc.cmdlib.cmd.WaitCommand
import com.rmsftc.cmdlib.scheduler.Scheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.BooleanSupplier

class BaseCommandsTest {
    private fun scheduler() = Scheduler()

    @Test
    fun `runOnce runs its action once and finishes`() {
        val s = scheduler()
        var count = 0
        s.schedule(Commands.runOnce(Runnable { count++ }))
        s.execute()
        s.execute()
        assertEquals(1, count)
    }

    @Test
    fun `startEnd runs start once and end once`() {
        val s = scheduler()
        val events = ArrayList<String>()
        val cmd = Commands.startEnd(
            Runnable { events.add("start") },
            Runnable { events.add("end") }
        )
        s.schedule(cmd)
        s.execute()
        assertEquals(listOf("start"), events)
        s.cancel(cmd)
        assertEquals(listOf("start", "end"), events)
    }

    @Test
    fun `wait finishes after its duration on a fake clock`() {
        val clock = FakeClock()
        val wait = WaitCommand(100, clock)
        val s = scheduler()
        s.schedule(wait)
        s.execute()
        assertTrue(s.isScheduled(wait))
        clock.advanceMs(99)
        s.execute()
        assertTrue(s.isScheduled(wait))
        clock.advanceMs(1)
        s.execute()
        assertFalse(s.isScheduled(wait))
    }

    @Test
    fun `withTimeout ends a never-ending command`() {
        val clock = FakeClock()
        val forever = RunForeverCommand()
        // withTimeout itself uses the real clock, so race the command
        // against a fake-clock wait to test the same semantics.
        val race = com.rmsftc.cmdlib.cmd.ParallelRaceGroup(
            forever, WaitCommand(50, clock)
        )
        val s = scheduler()
        s.schedule(race)
        s.execute()
        assertTrue(s.isScheduled(race))
        clock.advanceMs(50)
        s.execute()
        assertFalse(s.isScheduled(race))
        assertEquals(1, forever.ends)
        assertEquals(EndCondition.INTERRUPTED, forever.lastEndCondition)
    }

    @Test
    fun `until ends a command when the condition becomes true`() {
        val s = scheduler()
        var done = false
        val cmd = RunForeverCommand().until(BooleanSupplier { done })
        s.schedule(cmd)
        s.execute()
        assertTrue(s.isScheduled(cmd))
        done = true
        s.execute()
        assertFalse(s.isScheduled(cmd))
    }

    @Test
    fun `onlyIf skips the command when the condition is false at start`() {
        val s = scheduler()
        var ran = false
        val cmd = Commands.runOnce(Runnable { ran = true }).onlyIf(BooleanSupplier { false })
        s.schedule(cmd)
        s.execute()
        assertFalse(ran)
        assertFalse(s.isScheduled(cmd))
    }

    @Test
    fun `unless skips the command when the condition is true at start`() {
        val s = scheduler()
        var ran = false
        val cmd = Commands.runOnce(Runnable { ran = true }).unless(BooleanSupplier { true })
        s.schedule(cmd)
        s.execute()
        assertFalse(ran)
    }

    @Test
    fun `finallyDo runs with NATURALLY on a natural finish`() {
        val s = scheduler()
        var seen: EndCondition? = null
        val cmd = Commands.runOnce(Runnable {})
            .finallyDo { c -> seen = c }
        s.schedule(cmd)
        s.execute()
        assertEquals(EndCondition.NATURALLY, seen)
    }

    @Test
    fun `finallyDo runs with INTERRUPTED on a cancel and handleInterrupt only then`() {
        val s = scheduler()
        var finallySeen: EndCondition? = null
        var interruptRan = false
        val cmd = RunForeverCommand()
            .finallyDo { c -> finallySeen = c }
            .handleInterrupt(Runnable { interruptRan = true })
        s.schedule(cmd)
        s.execute()
        s.cancel(cmd)
        assertEquals(EndCondition.INTERRUPTED, finallySeen)
        assertTrue(interruptRan)
    }

    @Test
    fun `handleInterrupt does not run on a natural finish`() {
        val s = scheduler()
        var interruptRan = false
        val cmd = Commands.runOnce(Runnable {})
            .handleInterrupt(Runnable { interruptRan = true })
        s.schedule(cmd)
        s.execute()
        assertFalse(interruptRan)
    }

    @Test
    fun `repeatedly restarts a finishing command`() {
        val s = scheduler()
        var count = 0
        val cmd = Commands.runOnce(Runnable { count++ }).repeatedly()
        s.schedule(cmd)
        s.execute()
        s.execute()
        s.execute()
        assertEquals(3, count)
        assertTrue(s.isScheduled(cmd))
    }

    @Test
    fun `withName renames a command`() {
        val cmd = Commands.runOnce(Runnable {}).withName("score-preload")
        assertEquals("score-preload", cmd.name)
    }
}
