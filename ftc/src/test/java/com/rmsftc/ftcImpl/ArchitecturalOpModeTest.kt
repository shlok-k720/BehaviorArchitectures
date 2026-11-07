package com.rmsftc.ftcImpl

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.fsm.State
import com.rmsftc.fsm.StateMachine
import com.rmsftc.ftcImpl.fsm.TestCase
import org.firstinspires.ftc.robotcore.external.RecordingTelemetry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class TestOpMode : ArchitecturalOpMode() {
    val events = mutableListOf<String>()
    var treeTicks = 0
    var commandRuns = 0
    var machine: StateMachine? = null
    var stuckCase: TestCase? = null
    var interruptAfterFirstLoop = false

    override fun onInit() {
        events.add("init")
    }

    override fun build(manager: ArchitectureManager) {
        events.add("build")
        manager.tree {
            action("count", tick = {
                treeTicks++
                NodeStatus.RUNNING
            })
        }
        val stuck = TestCase(State.of("stuck"))
        stuckCase = stuck
        machine = manager.stateMachine { add(stuck) }
        manager.commands {
            runOnce(Runnable { commandRuns++ })
        }
    }

    override fun onLoop() {
        events.add("loop")
        if (interruptAfterFirstLoop && events.count { it == "loop" } == 1) {
            interrupt()
        }
    }

    override fun onStop() {
        events.add("stop")
        super.onStop()
    }
}

class ArchitecturalOpModeTest {

    @Test
    fun `runOpMode runs init build start loop and stop in order`() {
        val opMode = TestOpMode()
        opMode.telemetry = RecordingTelemetry()
        opMode.activeTicks = 3

        opMode.runOpMode()

        assertEquals(listOf("init", "build", "loop", "loop", "loop", "stop"), opMode.events)
        assertEquals(3, opMode.treeTicks)
        assertEquals(1, opMode.commandRuns)
        assertEquals(3, opMode.stuckCase!!.ticks)
        assertEquals(3, (opMode.telemetry as RecordingTelemetry).updateCalls)
        // onStop invalidated the machine: it finished and its case was exited.
        assertTrue(opMode.machine!!.isFinished())
        assertEquals(1, opMode.stuckCase!!.transitions)
    }

    @Test
    fun `interrupt aborts everything and ends the run early`() {
        val opMode = TestOpMode()
        opMode.interruptAfterFirstLoop = true
        opMode.activeTicks = 100

        opMode.runOpMode()

        assertEquals(listOf("init", "build", "loop", "stop"), opMode.events)
        assertEquals(1, opMode.treeTicks)
        assertTrue(opMode.machine!!.isFinished())
    }

    @Test
    fun `base class with no overrides runs cleanly`() {
        val opMode = ArchitecturalOpMode()
        opMode.activeTicks = 2
        opMode.runOpMode()
    }
}
