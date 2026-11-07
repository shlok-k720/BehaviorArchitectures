package com.rmsftc.ftcImpl

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.cmdlib.cmd.Commands
import com.rmsftc.fsm.State
import com.rmsftc.ftcImpl.fsm.TestCase
import com.rmsftc.util.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A command that records which blackboard the scheduler attached to it. */
private class BoardProbe : Command() {
    var seen: Blackboard? = null

    override fun start() {}
    override fun loop() {
        seen = blackboard()
    }

    override fun status(): Status = Status.FINISHED
    override fun end(endCondition: EndCondition) {}
}

class ArchitectureManagerTest {

    @Test
    fun `tree machine and scheduled command share one blackboard`() {
        val manager = ArchitectureManager()

        manager.tree {
            action("set", tick = {
                manager.blackboard().set("flag", "go")
                NodeStatus.SUCCESS
            })
        }
        manager.tickAll()
        assertEquals("go", manager.blackboard().getIfPresent("flag", String::class.java))

        val machine = manager.stateMachine {
            case("check") { onExecute { } }
        }
        assertSame(manager.blackboard(), machine.blackboard())

        val probe = BoardProbe()
        manager.schedule(probe)
        manager.tickAll()
        assertSame(manager.blackboard(), probe.seen)
    }

    @Test
    fun `tickAll ticks trees machines and the scheduler`() {
        val manager = ArchitectureManager()
        var treeTicks = 0
        var commandRuns = 0

        manager.tree {
            action("count", tick = {
                treeTicks++
                NodeStatus.RUNNING
            })
        }
        val counting = TestCase(State.of("count"))
        manager.stateMachine {
            add(counting)
        }
        manager.schedule(Commands.runOnce(Runnable { commandRuns++ }))
        manager.startAll()

        manager.tickAll()
        assertEquals(1, treeTicks)
        assertEquals(1, counting.ticks)
        assertEquals(1, commandRuns)

        manager.tickAll()
        assertEquals(2, treeTicks)
        assertEquals(2, counting.ticks)
        assertEquals(1, commandRuns)
    }

    @Test
    fun `resetAndInitialize clears everything and gives a fresh board`() {
        val manager = ArchitectureManager()
        val oldBoard = manager.blackboard()

        manager.tree {
            action("ok", tick = { NodeStatus.SUCCESS })
        }
        manager.stateMachine {
            case("check") { onExecute { } }
        }
        assertEquals(1, manager.trees().size)
        assertEquals(1, manager.machines().size)

        manager.resetAndInitialize()
        assertTrue(manager.trees().isEmpty())
        assertTrue(manager.machines().isEmpty())
        assertNotSame(oldBoard, manager.blackboard())
    }

    @Test
    fun `invalidateAll stops a running machine`() {
        val manager = ArchitectureManager()
        val stuck = TestCase(State.of("stuck"))
        val machine = manager.stateMachine {
            add(stuck)
        }

        manager.startAll()
        manager.tickAll()
        assertEquals(State.of("stuck"), machine.getCurrentState())

        manager.invalidateAll()
        assertTrue(machine.isFinished())
        assertEquals(1, stuck.transitions)
    }
}
