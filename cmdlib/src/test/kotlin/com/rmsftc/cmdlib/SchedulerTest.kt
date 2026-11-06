package com.rmsftc.cmdlib

import com.rmsftc.cmdlib.cmd.Commands
import com.rmsftc.cmdlib.cmd.InstantCommand
import com.rmsftc.cmdlib.cmd.RunCommand
import com.rmsftc.cmdlib.scheduler.QueueScheduler
import com.rmsftc.cmdlib.scheduler.Scheduler
import com.rmsftc.cmdlib.scheduler.SchedulerEventListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.BooleanSupplier

class SchedulerTest {
    private fun scheduler(active: Boolean = true): Scheduler =
        Scheduler(clock = java.util.function.Supplier { System.nanoTime() },
            isOpModeActive = BooleanSupplier { active })

    @Test
    fun `schedule runs a command to completion and removes it`() {
        val s = scheduler()
        val cmd = TestCommand(listOf(Status.RUNNING, Status.FINISHED))
        assertEquals(ScheduleResult.SCHEDULED, s.schedule(cmd))
        s.execute()
        assertTrue(s.isScheduled(cmd))
        assertEquals(1, cmd.loops)
        s.execute()
        assertFalse(s.isScheduled(cmd))
        assertEquals(1, cmd.ends)
        assertEquals(EndCondition.NATURALLY, cmd.lastEndCondition)
    }

    @Test
    fun `an instant command initializes executes and ends in one tick`() {
        val s = scheduler()
        var ran = false
        val cmd = InstantCommand(Runnable { ran = true })
        s.schedule(cmd)
        s.execute()
        assertTrue(ran)
        assertFalse(s.isScheduled(cmd))
    }

    @Test
    fun `cancel interrupts a running command`() {
        val s = scheduler()
        val cmd = RunForeverCommand()
        s.schedule(cmd)
        s.execute()
        assertEquals(1, cmd.loops)
        s.cancel(cmd)
        assertFalse(s.isScheduled(cmd))
        assertEquals(1, cmd.ends)
        assertEquals(EndCondition.INTERRUPTED, cmd.lastEndCondition)
    }

    @Test
    fun `double scheduling the same instance is rejected`() {
        val s = scheduler()
        val cmd = RunForeverCommand()
        assertEquals(ScheduleResult.SCHEDULED, s.schedule(cmd))
        assertEquals(ScheduleResult.ALREADY_RUNNING, s.schedule(cmd))
    }

    @Test
    fun `equal priority conflict with default behavior interrupts the running command`() {
        val s = scheduler()
        val drive = TestSubsystem("drive")
        val first = RunForeverCommand(drive)
        val second = RunForeverCommand(drive)
        s.schedule(first)
        s.execute()
        assertEquals(ScheduleResult.SCHEDULED, s.schedule(second))
        assertFalse(s.isScheduled(first))
        assertEquals(EndCondition.INTERRUPTED, first.lastEndCondition)
        assertTrue(s.isScheduled(second))
    }

    @Test
    fun `equal priority conflict with CANCEL_INCOMING rejects the newcomer`() {
        val s = scheduler()
        val drive = TestSubsystem("drive")
        val first = RunForeverCommand(drive)
        first.interruptionBehavior = InterruptionBehavior.CANCEL_INCOMING
        val second = RunForeverCommand(drive)
        s.schedule(first)
        assertEquals(ScheduleResult.REJECTED_BY_RUNNING_COMMAND, s.schedule(second))
        assertTrue(s.isScheduled(first))
        assertFalse(s.isScheduled(second))
    }

    @Test
    fun `higher priority interrupts lower, lower is blocked by higher`() {
        val s = scheduler()
        val drive = TestSubsystem("drive")
        val low = RunForeverCommand(drive)
        low.priority = 0
        val high = RunForeverCommand(drive)
        high.priority = 5
        s.schedule(low)
        s.execute()
        assertEquals(ScheduleResult.SCHEDULED, s.schedule(high))
        assertFalse(s.isScheduled(low))
        assertEquals(EndCondition.INTERRUPTED, low.lastEndCondition)

        val low2 = RunForeverCommand(drive)
        low2.priority = 0
        assertEquals(ScheduleResult.BLOCKED_BY_HIGHER_PRIORITY, s.schedule(low2))
        assertTrue(s.isScheduled(high))
    }

    @Test
    fun `a QUEUE blocked command is promoted when the holder finishes`() {
        val s = scheduler()
        val drive = TestSubsystem("drive")
        val holder = TestCommand(listOf(Status.RUNNING, Status.FINISHED), false, drive)
        holder.priority = 5
        val queued = RunForeverCommand(drive)
        queued.blockedBehavior = BlockedBehavior.QUEUE
        s.schedule(holder)
        assertEquals(ScheduleResult.QUEUED, s.schedule(queued))
        assertTrue(s.isScheduled(queued))
        assertFalse(s.runningCommands().contains(queued))
        assertTrue(s.queuedCommands().contains(queued))
        s.execute() // holder still running
        assertTrue(s.queuedCommands().contains(queued))
        s.execute() // holder finishes; queued promotes
        assertTrue(s.isScheduled(queued))
        assertTrue(s.runningCommands().contains(queued))
    }

    @Test
    fun `cancelling a queued command never calls end`() {
        val s = scheduler()
        val drive = TestSubsystem("drive")
        val holder = RunForeverCommand(drive)
        holder.priority = 5
        val queued = TestCommand(listOf(Status.FINISHED), false, drive)
        queued.blockedBehavior = BlockedBehavior.QUEUE
        s.schedule(holder)
        s.schedule(queued)
        s.cancel(queued)
        assertEquals(0, queued.ends)
        assertEquals(0, queued.starts)
        assertFalse(s.isScheduled(queued))
    }

    @Test
    fun `default command runs when nothing else requires the subsystem`() {
        val s = scheduler()
        val drive = TestSubsystem("drive")
        val idle = RunForeverCommand(drive)
        s.setDefaultCommand(drive, idle)
        s.execute()
        assertTrue(s.isScheduled(idle))
        s.execute()
        val other = RunForeverCommand(drive)
        s.schedule(other)
        s.execute()
        assertFalse(s.isScheduled(idle))
        assertEquals(EndCondition.INTERRUPTED, idle.lastEndCondition)
        assertTrue(s.isScheduled(other))
        s.cancel(other)
        s.execute()
        assertTrue(s.isScheduled(idle))
    }

    @Test
    fun `subsystem periodic runs every tick`() {
        val s = scheduler()
        val drive = TestSubsystem("drive")
        s.registerSubsystem(drive)
        s.execute()
        s.execute()
        assertEquals(2, drive.periodicCalls)
    }

    @Test
    fun `scheduling while inactive is rejected unless the command runs when disabled`() {
        val s = scheduler(active = false)
        val cmd = RunForeverCommand()
        assertEquals(ScheduleResult.REJECTED_WHILE_DISABLED, s.schedule(cmd))
        val allowed = RunForeverCommand()
        allowed.runsWhenDisabled = true
        assertEquals(ScheduleResult.SCHEDULED, s.schedule(allowed))
    }

    @Test
    fun `inactive scheduler cancels commands that do not run when disabled`() {
        var active = true
        val s = Scheduler(isOpModeActive = BooleanSupplier { active })
        val cmd = RunForeverCommand()
        s.schedule(cmd)
        s.execute()
        active = false
        s.execute()
        assertFalse(s.isScheduled(cmd))
        assertEquals(EndCondition.INTERRUPTED, cmd.lastEndCondition)
    }

    @Test
    fun `requiring reports the running command for a subsystem`() {
        val s = scheduler()
        val drive = TestSubsystem("drive")
        val cmd = RunForeverCommand(drive)
        assertNull(s.requiring(drive))
        s.schedule(cmd)
        assertEquals(cmd, s.requiring(drive))
        s.cancel(cmd)
        assertNull(s.requiring(drive))
    }

    @Test
    fun `event listeners see schedule finish and cancel`() {
        val s = scheduler()
        val events = ArrayList<String>()
        s.addEventListener(object : SchedulerEventListener {
            override fun onScheduled(command: com.rmsftc.cmdlib.cmd.ICommand) {
                events.add("scheduled")
            }

            override fun onFinished(command: com.rmsftc.cmdlib.cmd.ICommand) {
                events.add("finished")
            }

            override fun onCancelled(command: com.rmsftc.cmdlib.cmd.ICommand) {
                events.add("cancelled")
            }
        })
        val instant = InstantCommand()
        s.schedule(instant)
        s.execute()
        val forever = RunForeverCommand()
        s.schedule(forever)
        s.execute()
        s.cancel(forever)
        assertEquals(listOf("scheduled", "finished", "scheduled", "cancelled"), events)
    }

    @Test
    fun `a composed command cannot be scheduled directly`() {
        val s = scheduler()
        val a = InstantCommand()
        val b = InstantCommand()
        Commands.sequence(a, b)
        assertThrows(IllegalStateException::class.java) { s.schedule(a) }
    }

    @Test
    fun `cancelAll ends every running command`() {
        val s = scheduler()
        val a = RunForeverCommand()
        val b = RunForeverCommand()
        s.schedule(a)
        s.schedule(b)
        s.execute()
        s.cancelAll()
        assertFalse(s.isScheduled(a))
        assertFalse(s.isScheduled(b))
        assertEquals(EndCondition.INTERRUPTED, a.lastEndCondition)
        assertEquals(EndCondition.INTERRUPTED, b.lastEndCondition)
    }

    @Test
    fun `QueueScheduler runs one command at a time in order`() {
        val s = QueueScheduler()
        val order = ArrayList<String>()
        val first = Commands.runOnce(Runnable { order.add("first") })
        val second = Commands.runOnce(Runnable { order.add("second") })
        val third = Commands.runOnce(Runnable { order.add("third") })
        s.schedule(first)
        s.schedule(second)
        s.schedule(third)
        s.execute()
        assertEquals(listOf("first"), order)
        s.execute()
        assertEquals(listOf("first", "second"), order)
        s.execute()
        assertEquals(listOf("first", "second", "third"), order)
        assertFalse(s.isScheduled(first))
    }

    @Test
    fun `QueueScheduler cancel drops a queued command without ending it`() {
        val s = QueueScheduler()
        val head = RunForeverCommand()
        val queued = TestCommand(listOf(Status.FINISHED))
        s.schedule(head)
        s.schedule(queued)
        s.cancel(queued)
        assertEquals(0, queued.ends)
        assertFalse(s.isScheduled(queued))
    }

    @Test
    fun `RunCommand never finishes and keeps looping`() {
        val s = scheduler()
        var count = 0
        val cmd = RunCommand(Runnable { count++ })
        s.schedule(cmd)
        repeat(3) { s.execute() }
        assertEquals(3, count)
        assertTrue(s.isScheduled(cmd))
    }
}
