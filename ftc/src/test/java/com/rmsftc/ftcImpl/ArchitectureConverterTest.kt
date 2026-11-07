package com.rmsftc.ftcImpl

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.cmdlib.cmd.Commands
import com.rmsftc.fsm.State
import com.rmsftc.fsm.StateMachine
import com.rmsftc.ftcImpl.fsm.TestCase
import com.rmsftc.util.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A command that records its lifecycle, for the converter tests. */
private class RecordingCommand(private val runsForever: Boolean = true) : Command() {
    var starts = 0
    var loops = 0
    var ends = 0
    var lastEnd: EndCondition? = null

    override fun start() {
        starts++
    }

    override fun loop() {
        loops++
    }

    override fun status(): Status = Status.of(runsForever)

    override fun end(endCondition: EndCondition) {
        ends++
        lastEnd = endCondition
    }
}

class ArchitectureConverterTest {

    private fun advancingMachine(): Pair<StateMachine, List<TestCase>> {
        val a = TestCase(State.of("a")).apply { advanceOnExecute = true }
        val b = TestCase(State.of("b")).apply { advanceOnExecute = true }
        return StateMachine(a, b) to listOf(a, b)
    }

    @Test
    fun `state machine as command finishes when the machine runs out of cases`() {
        val (machine, cases) = advancingMachine()
        val command = ArchitectureConverter.toCommand(machine)

        assertTrue(command.execute().isRunning())
        assertFalse(command.execute().isRunning())
        assertTrue(machine.isFinished())
        assertEquals(listOf(1, 1), cases.map { it.starts })
        assertEquals(listOf(1, 1), cases.map { it.transitions })
    }

    @Test
    fun `state machine as command stops the machine when interrupted`() {
        val stuck = TestCase(State.of("stuck"))
        val machine = StateMachine(stuck)
        val command = ArchitectureConverter.toCommand(machine)

        assertTrue(command.execute().isRunning())
        command.invalidate()
        assertTrue(machine.isFinished())
        assertEquals(1, stuck.transitions)
    }

    @Test
    fun `single case as command finishes by advancing`() {
        val only = TestCase(State.of("only")).apply { advanceOnExecute = true }
        val command = ArchitectureConverter.toCommand(only)

        assertFalse(command.execute().isRunning())
        assertEquals(1, only.starts)
    }

    @Test
    fun `single case as command throws when the case jumps to an unknown state`() {
        val jumper = TestCase(State.of("jumper")).apply {
            transitionRequest = State.of("nope")
        }
        val command = ArchitectureConverter.toCommand(jumper)

        assertThrows(IllegalArgumentException::class.java) { command.execute() }
    }

    @Test
    fun `command as case advances when the command finishes`() {
        var ran = false
        val done = TestCase(State.of("done"))
        val machine = StateMachine(
            ArchitectureConverter.toCase(Commands.runOnce(Runnable { ran = true }), State.of("run")),
            done
        )

        machine.start()
        machine.update()
        assertTrue(ran)
        assertEquals(State.of("done"), machine.getCurrentState())
    }

    @Test
    fun `command as case interrupts the command when the machine stops`() {
        val command = RecordingCommand(runsForever = true)
        val machine = StateMachine(
            ArchitectureConverter.toCase(command, State.of("run")),
            TestCase(State.of("done"))
        )

        machine.start()
        machine.update()
        assertEquals(1, command.starts)
        assertEquals(0, command.ends)

        machine.stop()
        assertEquals(1, command.ends)
        assertEquals(EndCondition.INTERRUPTED, command.lastEnd)
    }

    @Test
    fun `tree as command finishes on success`() {
        val board = Blackboard()
        val tree = Trees.KotlinBuilder.rootTree(board) {
            action("ok", tick = { NodeStatus.SUCCESS })
        }
        val command = ArchitectureConverter.toCommand(tree)

        assertFalse(command.execute().isRunning())
    }

    @Test
    fun `tree as command stays running and interrupts the tree`() {
        val board = Blackboard()
        var ended = false
        val tree = Trees.KotlinBuilder.rootTree(board) {
            action("running", tick = { NodeStatus.RUNNING }, end = { ended = true })
        }
        val command = ArchitectureConverter.toCommand(tree)

        assertTrue(command.execute().isRunning())
        command.invalidate()
        assertTrue(ended)
        assertEquals(NodeStatus.FAILURE, tree.status)
    }

    @Test
    fun `command as node reports success when the command finishes`() {
        val board = Blackboard()
        var ran = false
        val node = ArchitectureConverter.toNode(Commands.runOnce(Runnable { ran = true }), board)
        val tree = Trees.KotlinBuilder.rootTree(board) { node(node) }

        assertEquals(NodeStatus.SUCCESS, tree.tick())
        assertTrue(ran)
    }

    @Test
    fun `command as node interrupts the command when the tree is invalidated`() {
        val board = Blackboard()
        val command = RecordingCommand(runsForever = true)
        val node = ArchitectureConverter.toNode(command, board)
        val tree = Trees.KotlinBuilder.rootTree(board) { node(node) }

        assertEquals(NodeStatus.RUNNING, tree.tick())
        tree.invalidate()
        assertEquals(1, command.ends)
        assertEquals(EndCondition.INTERRUPTED, command.lastEnd)
    }

    @Test
    fun `tree as case advances when the tree finishes`() {
        val board = Blackboard()
        val tree = Trees.KotlinBuilder.rootTree(board) {
            action("ok", tick = { NodeStatus.SUCCESS })
        }
        val done = TestCase(State.of("done"))
        val machine = StateMachine(
            ArchitectureConverter.toCase(tree, State.of("run")),
            done
        )

        machine.start()
        machine.update()
        assertEquals(State.of("done"), machine.getCurrentState())
    }

    @Test
    fun `machine as node reports success when the machine finishes`() {
        val board = Blackboard()
        val (machine, _) = advancingMachine()
        val node = ArchitectureConverter.toNode(machine, board)
        val tree = Trees.KotlinBuilder.rootTree(board) { node(node) }

        assertEquals(NodeStatus.RUNNING, tree.tick())
        assertEquals(NodeStatus.SUCCESS, tree.tick())
        assertTrue(machine.isFinished())
    }

    @Test
    fun `single case as node reports success when the case advances`() {
        val board = Blackboard()
        val only = TestCase(State.of("only")).apply { advanceOnExecute = true }
        val node = ArchitectureConverter.toNode(only, board)
        val tree = Trees.KotlinBuilder.rootTree(board) { node(node) }

        assertEquals(NodeStatus.SUCCESS, tree.tick())
        assertEquals(1, only.starts)
    }
}
