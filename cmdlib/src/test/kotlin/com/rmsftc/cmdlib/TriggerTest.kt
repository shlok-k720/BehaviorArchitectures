package com.rmsftc.cmdlib

import com.rmsftc.cmdlib.cmd.Commands
import com.rmsftc.cmdlib.scheduler.Scheduler
import com.rmsftc.cmdlib.trigger.DebounceType
import com.rmsftc.cmdlib.trigger.Trigger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.function.BooleanSupplier

class TriggerTest {
    private var value = false
    private fun trigger(s: Scheduler) =
        Trigger(BooleanSupplier { value }, s)

    @Test
    fun `onTrue fires once on a rising edge`() {
        val s = Scheduler()
        var count = 0
        trigger(s).onTrue(Commands.runOnce(Runnable { count++ }))
        s.execute()
        assertEquals(0, count)
        value = true
        s.execute()
        assertEquals(1, count)
        s.execute()
        assertEquals(1, count)
    }

    @Test
    fun `onFalse fires once on a falling edge`() {
        val s = Scheduler()
        var count = 0
        value = true
        trigger(s).onFalse(Commands.runOnce(Runnable { count++ }))
        s.execute()
        value = false
        s.execute()
        assertEquals(1, count)
    }

    @Test
    fun `whileTrue schedules on rising and cancels on falling`() {
        val s = Scheduler()
        val cmd = RunForeverCommand()
        trigger(s).whileTrue(cmd)
        value = true
        s.execute()
        assertTrue(s.isScheduled(cmd))
        s.execute()
        assertTrue(s.isScheduled(cmd))
        value = false
        s.execute()
        assertFalse(s.isScheduled(cmd))
        assertEquals(EndCondition.INTERRUPTED, cmd.lastEndCondition)
    }

    @Test
    fun `toggleOnTrue toggles the command on each rising edge`() {
        val s = Scheduler()
        val cmd = RunForeverCommand()
        trigger(s).toggleOnTrue(cmd)
        value = true
        s.execute()
        assertTrue(s.isScheduled(cmd))
        value = false
        s.execute()
        value = true
        s.execute()
        assertFalse(s.isScheduled(cmd))
    }

    @Test
    fun `and requires both conditions`() {
        val s = Scheduler()
        var other = false
        var count = 0
        val combined = trigger(s).and(Trigger(BooleanSupplier { other }, s))
        combined.onTrue(Commands.runOnce(Runnable { count++ }))
        value = true
        s.execute()
        assertEquals(0, count)
        other = true
        s.execute()
        assertEquals(1, count)
    }

    @Test
    fun `debounce RISING waits for the input to settle true`() {
        val clock = FakeClock()
        val s = Scheduler(clock = clock)
        var count = 0
        val debounced = Trigger(BooleanSupplier { value }, s, clock)
            .debounce(Duration.ofMillis(100), DebounceType.RISING)
        debounced.onTrue(Commands.runOnce(Runnable { count++ }))
        value = true
        s.execute()
        assertEquals(0, count)
        clock.advanceMs(99)
        s.execute()
        assertEquals(0, count)
        clock.advanceMs(1)
        s.execute()
        assertEquals(1, count)
    }

    @Test
    fun `debounce RISING drops the output the moment the input goes false`() {
        val clock = FakeClock()
        val s = Scheduler(clock = clock)
        var count = 0
        val debounced = Trigger(BooleanSupplier { value }, s, clock)
            .debounce(Duration.ofMillis(100), DebounceType.RISING)
        debounced.onTrue(Commands.runOnce(Runnable { count++ }))
        value = true
        s.execute()
        clock.advanceMs(100)
        s.execute()
        assertEquals(1, count)
        // Input drops: output drops at once, so a fresh rise needs the full
        // debounce time again before it fires.
        value = false
        s.execute()
        value = true
        s.execute()
        assertEquals(1, count)
        clock.advanceMs(100)
        s.execute()
        assertEquals(2, count)
    }
}
