package com.rmsftc.cmdlib

import com.rmsftc.cmdlib.cmd.Commands
import com.rmsftc.cmdlib.cmd.DeferredCommand
import com.rmsftc.cmdlib.cmd.ProxyCommand
import com.rmsftc.cmdlib.cmd.ScheduleCommand
import com.rmsftc.cmdlib.scheduler.Scheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.EnumMap
import java.util.function.BooleanSupplier
import java.util.function.Supplier

class SelectorsTest {
    private fun scheduler() = Scheduler()

    @Test
    fun `either runs the true branch when the selector is true`() {
        val s = scheduler()
        val ran = ArrayList<String>()
        val cmd = Commands.either(
            Commands.runOnce(Runnable { ran.add("true") }),
            Commands.runOnce(Runnable { ran.add("false") }),
            BooleanSupplier { true }
        )
        s.schedule(cmd)
        s.execute()
        assertEquals(listOf("true"), ran)
    }

    @Test
    fun `either picks once at start and does not switch mid-run`() {
        val s = scheduler()
        var selector = true
        val ran = ArrayList<String>()
        val cmd = Commands.either(
            TestCommand(listOf(Status.RUNNING, Status.FINISHED)),
            Commands.runOnce(Runnable { ran.add("false") }),
            BooleanSupplier { selector }
        )
        s.schedule(cmd)
        s.execute()
        selector = false
        s.execute()
        assertEquals(emptyList<String>(), ran)
        assertFalse(s.isScheduled(cmd))
    }

    @Test
    fun `select runs the command for the selected key`() {
        val s = scheduler()
        val ran = ArrayList<String>()
        val cmd = Commands.select(
            mapOf(
                "score" to Commands.runOnce(Runnable { ran.add("score") }),
                "park" to Commands.runOnce(Runnable { ran.add("park") })
            ),
            Supplier { "park" }
        )
        s.schedule(cmd)
        s.execute()
        assertEquals(listOf("park"), ran)
    }

    @Test
    fun `branch runs the first true branch in order`() {
        val s = scheduler()
        val ran = ArrayList<String>()
        val cmd = Commands.branch(
            linkedMapOf(
                BooleanSupplier { false } to Commands.runOnce(Runnable { ran.add("first") }),
                BooleanSupplier { true } to Commands.runOnce(Runnable { ran.add("second") }),
                BooleanSupplier { true } to Commands.runOnce(Runnable { ran.add("third") })
            )
        )
        s.schedule(cmd)
        s.execute()
        assertEquals(listOf("second"), ran)
    }

    @Test
    fun `branch with no true condition finishes without running anything`() {
        val s = scheduler()
        var ran = false
        val cmd = Commands.branch(
            linkedMapOf(BooleanSupplier { false } to Commands.runOnce(Runnable { ran = true }))
        )
        s.schedule(cmd)
        s.execute()
        assertFalse(ran)
        assertFalse(s.isScheduled(cmd))
    }

    private enum class AutoMode { SCORE, PARK }

    @Test
    fun `match runs the command for the selected enum value`() {
        val s = scheduler()
        val ran = ArrayList<String>()
        val commands = EnumMap<AutoMode, com.rmsftc.cmdlib.cmd.ICommand>(AutoMode::class.java)
        commands[AutoMode.SCORE] = Commands.runOnce(Runnable { ran.add("score") })
        commands[AutoMode.PARK] = Commands.runOnce(Runnable { ran.add("park") })
        val cmd = Commands.match(commands, Supplier { AutoMode.SCORE })
        s.schedule(cmd)
        s.execute()
        assertEquals(listOf("score"), ran)
    }

    @Test
    fun `defer builds its command lazily at start time`() {
        val s = scheduler()
        var built = false
        val cmd = Commands.defer(
            Supplier {
                built = true
                Commands.runOnce(Runnable {})
            }
        )
        s.schedule(cmd)
        assertFalse(built)
        s.execute()
        assertTrue(built)
        assertFalse(s.isScheduled(cmd))
    }

    @Test
    fun `defer rejects a built command that needs undeclared tokens`() {
        val s = scheduler()
        val drive = "drive"
        val cmd = DeferredCommand(
            Supplier { RunForeverCommand(drive) }
        )
        s.schedule(cmd)
        assertThrows(IllegalStateException::class.java) { s.execute() }
    }

    @Test
    fun `proxy schedules its inner command and ends when it ends`() {
        val s = scheduler()
        val inner = TestCommand(listOf(Status.RUNNING, Status.FINISHED))
        val proxy = ProxyCommand(inner, s)
        s.schedule(proxy)
        s.execute()
        assertTrue(s.isScheduled(proxy))
        assertTrue(s.isScheduled(inner))
        s.execute()
        assertTrue(s.isScheduled(inner))
        s.execute()
        assertFalse(s.isScheduled(inner))
        // The proxy runs before the inner command each tick, so it notices
        // the inner command ended one tick later.
        assertTrue(s.isScheduled(proxy))
        s.execute()
        assertFalse(s.isScheduled(proxy))
    }

    @Test
    fun `proxy finishes immediately when the inner command cannot be scheduled`() {
        val s = scheduler()
        val drive = "drive"
        val holder = RunForeverCommand(drive)
        holder.interruptionBehavior = InterruptionBehavior.CANCEL_INCOMING
        s.schedule(holder)
        val inner = RunForeverCommand(drive)
        val proxy = ProxyCommand(inner, s)
        s.schedule(proxy)
        s.execute()
        assertFalse(s.isScheduled(proxy))
        assertFalse(s.isScheduled(inner))
    }

    @Test
    fun `schedule command finishes immediately while the forked command keeps running`() {
        val s = scheduler()
        val forked = RunForeverCommand()
        val cmd = ScheduleCommand(forked, s)
        s.schedule(cmd)
        s.execute()
        assertFalse(s.isScheduled(cmd))
        assertTrue(s.isScheduled(forked))
    }

    @Test
    fun `asProxy ends when the proxied command ends`() {
        val s = scheduler()
        val inner = TestCommand(listOf(Status.FINISHED))
        val proxy = inner.asProxy(s)
        s.schedule(proxy)
        s.execute()
        assertTrue(s.isScheduled(proxy))
        s.execute()
        assertTrue(s.isScheduled(proxy))
        s.execute()
        assertFalse(s.isScheduled(proxy))
    }
}
