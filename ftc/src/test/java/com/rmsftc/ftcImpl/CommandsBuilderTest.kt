package com.rmsftc.ftcImpl

import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.cmd.Commands
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.BooleanSupplier

class CommandsBuilderTest {

    @Test
    fun `empty builder builds a command that finishes immediately`() {
        val manager = ArchitectureManager()
        val cmd = CommandsBuilder().build()
        manager.schedule(cmd)
        manager.tickAll()
        assertFalse(manager.scheduler().isScheduled(cmd))
    }

    @Test
    fun `single step is returned as-is`() {
        val step = Commands.runOnce(Runnable { })
        val built = CommandsBuilder().command(step).build()
        assertSame(step, built)
    }

    @Test
    fun `a finished step advances to the next step in the same tick`() {
        val manager = ArchitectureManager()
        val order = mutableListOf<Int>()

        val cmd = CommandsBuilder()
            .runOnce(Runnable { order.add(1) })
            .runOnce(Runnable { order.add(2) })
            .build()
        manager.schedule(cmd)

        manager.tickAll()
        assertEquals(listOf(1, 2), order)
        assertFalse(manager.scheduler().isScheduled(cmd))
    }

    @Test
    fun `steps run in order across ticks`() {
        val manager = ArchitectureManager()
        val order = mutableListOf<Int>()

        // The middle step never finishes, so the last step must wait
        // behind it no matter how many ticks pass.
        val cmd = CommandsBuilder()
            .runOnce(Runnable { order.add(1) })
            .run(Runnable { })
            .runOnce(Runnable { order.add(2) })
            .build()
        manager.schedule(cmd)

        manager.tickAll()
        assertEquals(listOf(1), order)
        manager.tickAll()
        assertEquals(listOf(1), order)
        assertTrue(manager.scheduler().isScheduled(cmd))
        manager.cancel(cmd)
    }

    @Test
    fun `waitUntil holds the sequence until the condition is true`() {
        val manager = ArchitectureManager()
        var flag = false
        val order = mutableListOf<Int>()

        val cmd = CommandsBuilder()
            .runOnce(Runnable { order.add(1) })
            .waitUntil(BooleanSupplier { flag })
            .runOnce(Runnable { order.add(2) })
            .build()
        manager.schedule(cmd)

        manager.tickAll()
        manager.tickAll()
        assertEquals(listOf(1), order)

        flag = true
        manager.tickAll()
        manager.tickAll()
        assertEquals(listOf(1, 2), order)
    }

    @Test
    fun `waitMs step keeps the sequence running instead of finishing`() {
        val manager = ArchitectureManager()
        val cmd = CommandsBuilder()
            .runOnce(Runnable { })
            .waitMs(60_000)
            .runOnce(Runnable { })
            .build()
        manager.schedule(cmd)
        manager.tickAll()
        assertTrue(manager.scheduler().isScheduled(cmd))
        manager.cancel(cmd)
    }

    @Test
    fun `nested parallel runs its steps on the same tick`() {
        val manager = ArchitectureManager()
        val ran = mutableListOf<Int>()

        val cmd = CommandsBuilder()
            .parallel {
                runOnce(Runnable { ran.add(1) })
                runOnce(Runnable { ran.add(2) })
            }
            .build()
        manager.schedule(cmd)
        manager.tickAll()
        assertEquals(listOf(1, 2), ran)
    }

    @Test
    fun `nested sequence runs its steps in order inside the outer sequence`() {
        val manager = ArchitectureManager()
        val order = mutableListOf<Int>()

        val cmd = CommandsBuilder()
            .sequence {
                runOnce(Runnable { order.add(1) })
                runOnce(Runnable { order.add(2) })
            }
            .runOnce(Runnable { order.add(3) })
            .build()
        manager.schedule(cmd)

        manager.tickAll()
        manager.tickAll()
        manager.tickAll()
        assertEquals(listOf(1, 2, 3), order)
    }

    @Test
    fun `deadline ends the other steps when it finishes`() {
        val manager = ArchitectureManager()
        var otherStarted = false
        var otherEnded = false

        val cmd = CommandsBuilder()
            .deadline(Commands.runOnce(Runnable { })) {
                startEnd(Runnable { otherStarted = true }, Runnable { otherEnded = true })
            }
            .build()
        manager.schedule(cmd)
        manager.tickAll()
        manager.tickAll()
        assertTrue(otherStarted)
        assertTrue(otherEnded)
        assertFalse(manager.scheduler().isScheduled(cmd))
    }

    @Test
    fun `priority and interrupt behavior are applied to the built command`() {
        val cmd = CommandsBuilder()
            .runOnce(Runnable { })
            .priority(3)
            .interruptBehavior(InterruptionBehavior.CANCEL_INCOMING)
            .build()
        assertEquals(3, cmd.priority)
        assertEquals(InterruptionBehavior.CANCEL_INCOMING, cmd.interruptionBehavior)
    }

    @Test
    fun `manager commands builds and schedules`() {
        val manager = ArchitectureManager()
        var runs = 0

        val cmd = manager.commands {
            runOnce(Runnable { runs++ })
        }
        assertTrue(manager.scheduler().isScheduled(cmd))
        manager.tickAll()
        assertEquals(1, runs)
        assertFalse(manager.scheduler().isScheduled(cmd))
    }

    @Test
    fun `manager commands is rejected while the OpMode is inactive`() {
        val manager = ArchitectureManager(
            isOpModeActive = BooleanSupplier { false }
        )
        val cmd = manager.commands {
            runOnce(Runnable { })
        }
        assertFalse(manager.scheduler().isScheduled(cmd))
    }
}
