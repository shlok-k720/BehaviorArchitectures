package com.rmsftc.cmdlib

import com.rmsftc.cmdlib.cmd.Commands
import com.rmsftc.cmdlib.cmd.ParallelCommandGroup
import com.rmsftc.cmdlib.cmd.ParallelDeadlineGroup
import com.rmsftc.cmdlib.cmd.ParallelRaceGroup
import com.rmsftc.cmdlib.cmd.RepeatCommand
import com.rmsftc.cmdlib.cmd.SequentialCommandGroup
import com.rmsftc.cmdlib.scheduler.Scheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroupsTest {
    private fun scheduler() = Scheduler()

    private fun runTicks(s: Scheduler, n: Int) {
        repeat(n) { s.execute() }
    }

    @Test
    fun `sequential runs children in order and advances in the same tick`() {
        val s = scheduler()
        val order = ArrayList<String>()
        val a = Commands.runOnce(Runnable { order.add("a") })
        val b = Commands.runOnce(Runnable { order.add("b") })
        val c = Commands.runOnce(Runnable { order.add("c") })
        val group = SequentialCommandGroup(a, b, c)
        s.schedule(group)
        s.execute()
        assertEquals(listOf("a", "b", "c"), order)
        assertFalse(s.isScheduled(group))
    }

    @Test
    fun `sequential waits for a running child before advancing`() {
        val s = scheduler()
        val order = ArrayList<String>()
        val slow = TestCommand(listOf(Status.RUNNING, Status.RUNNING, Status.FINISHED))
        val after = Commands.runOnce(Runnable { order.add("after") })
        val group = SequentialCommandGroup(slow, after)
        s.schedule(group)
        s.execute()
        assertTrue(order.isEmpty())
        s.execute()
        assertTrue(order.isEmpty())
        s.execute()
        assertEquals(listOf("after"), order)
        assertFalse(s.isScheduled(group))
    }

    @Test
    fun `sequential interruption invalidates the current child`() {
        val s = scheduler()
        val slow = RunForeverCommand()
        val after = Commands.runOnce(Runnable {})
        val group = SequentialCommandGroup(slow, after)
        s.schedule(group)
        s.execute()
        s.cancel(group)
        assertEquals(1, slow.ends)
        assertEquals(EndCondition.INTERRUPTED, slow.lastEndCondition)
    }

    @Test
    fun `parallel ends when the last child ends`() {
        val s = scheduler()
        val fast = TestCommand(listOf(Status.FINISHED))
        val slow = TestCommand(listOf(Status.RUNNING, Status.RUNNING, Status.FINISHED))
        val group = ParallelCommandGroup(fast, slow)
        s.schedule(group)
        s.execute()
        assertTrue(s.isScheduled(group))
        s.execute()
        assertTrue(s.isScheduled(group))
        s.execute()
        assertFalse(s.isScheduled(group))
        assertEquals(1, fast.ends)
        assertEquals(1, slow.ends)
    }

    @Test
    fun `parallel forbids two children sharing a token`() {
        val drive = "drive"
        val a = RunForeverCommand(drive)
        val b = RunForeverCommand(drive)
        assertThrows(IllegalArgumentException::class.java) { ParallelCommandGroup(a, b) }
    }

    @Test
    fun `race ends when the first child ends and interrupts the loser`() {
        val s = scheduler()
        val fast = TestCommand(listOf(Status.FINISHED))
        val slow = RunForeverCommand()
        val group = ParallelRaceGroup(fast, slow)
        s.schedule(group)
        s.execute()
        assertFalse(s.isScheduled(group))
        assertEquals(1, slow.ends)
        assertEquals(EndCondition.INTERRUPTED, slow.lastEndCondition)
        assertEquals(1, fast.ends)
        assertEquals(EndCondition.NATURALLY, fast.lastEndCondition)
    }

    @Test
    fun `deadline ends when the deadline ends and interrupts the others`() {
        val s = scheduler()
        val deadline = TestCommand(listOf(Status.RUNNING, Status.FINISHED))
        val other = RunForeverCommand()
        val group = ParallelDeadlineGroup(deadline, other)
        s.schedule(group)
        s.execute()
        assertTrue(s.isScheduled(group))
        s.execute()
        assertFalse(s.isScheduled(group))
        assertEquals(1, other.ends)
        assertEquals(EndCondition.INTERRUPTED, other.lastEndCondition)
        assertEquals(EndCondition.NATURALLY, deadline.lastEndCondition)
    }

    @Test
    fun `repeat restarts its child until interrupted`() {
        val s = scheduler()
        val child = TestCommand(listOf(Status.FINISHED))
        val repeat = RepeatCommand(child)
        s.schedule(repeat)
        s.execute()
        assertEquals(1, child.starts)
        s.execute()
        assertEquals(2, child.starts)
        s.execute()
        assertEquals(3, child.starts)
        assertTrue(s.isScheduled(repeat))
        // The child finishes naturally every tick, so cancelling between
        // iterations leaves its last end NATURALLY. Interruption shows when
        // the child is mid-run at cancel time:
        val running = TestCommand(listOf(Status.RUNNING), repeatLast = true)
        val repeatRunning = RepeatCommand(running)
        s.schedule(repeatRunning)
        s.execute()
        s.cancel(repeatRunning)
        assertEquals(EndCondition.INTERRUPTED, running.lastEndCondition)
    }

    @Test
    fun `andThen chains commands in order`() {
        val s = scheduler()
        val order = ArrayList<String>()
        val first = Commands.runOnce(Runnable { order.add("first") })
        val second = Commands.runOnce(Runnable { order.add("second") })
        val chained = first.andThen(second)
        s.schedule(chained)
        s.execute()
        assertEquals(listOf("first", "second"), order)
    }

    @Test
    fun `a composed command cannot be reused in another composition`() {
        val a = Commands.runOnce(Runnable {})
        val b = Commands.runOnce(Runnable {})
        Commands.sequence(a, b)
        val c = Commands.runOnce(Runnable {})
        assertThrows(IllegalStateException::class.java) { Commands.sequence(a, c) }
    }

    @Test
    fun `a decorated command cannot be scheduled directly afterwards`() {
        val s = scheduler()
        val base = Commands.runOnce(Runnable {})
        val decorated = base.andThen(Commands.runOnce(Runnable {}))
        assertThrows(IllegalStateException::class.java) { s.schedule(base) }
        s.schedule(decorated)
        s.execute()
        assertFalse(s.isScheduled(decorated))
    }
}
