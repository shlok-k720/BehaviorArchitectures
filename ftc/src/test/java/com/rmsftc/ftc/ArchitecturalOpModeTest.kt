package com.rmsftc.ftc

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.fsm.State
import com.rmsftc.fsm.StateMachine
import com.rmsftc.ftc.fsm.TestCase
import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.robocol.TelemetryMessage
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeServices
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

    /** Drives the runOpMode lifecycle without a real OpMode start: init,
     * build, then [ticks] loops (stopping early if stop is requested). */
    fun runForTest(ticks: Int) {
        onInit()
        build(manager)
        var left = ticks
        runLoop { left-- > 0 && !isStopRequested() }
    }
}

class ArchitecturalOpModeTest {

    @Test
    fun `runOpMode runs init build start loop and stop in order`() {
        val opMode = TestOpMode()
        opMode.telemetry = RecordingTelemetry()
        opMode.runForTest(3)


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
        installFakeOpModeServices(opMode)
        opMode.interruptAfterFirstLoop = true
        opMode.runForTest(100)


        assertEquals(listOf("init", "build", "loop", "stop"), opMode.events)
        assertEquals(1, opMode.treeTicks)
        assertTrue(opMode.machine!!.isFinished())
    }

    @Test
    fun `base class with no overrides runs cleanly`() {
        val opMode = ArchitecturalOpMode()
        var left = 2
        opMode.runLoop { left-- > 0 }
    }
}

/** Installs a fake [OpModeServices] so `requestOpModeStop()` works without
 * a real OpMode manager: it just flips the stopRequested flag, like the
 * runtime would. The field lives on the SDK's package-private
 * OpModeInternal, hence reflection. */
private fun installFakeOpModeServices(opMode: ArchitecturalOpMode) {
    val services = object : OpModeServices {
        override fun refreshUserTelemetry(telemetry: TelemetryMessage, sInterval: Double) {}

        override fun requestOpModeStop(opMode: OpMode) {
            setOpModeField(opMode, "stopRequested", true)
        }
    }
    setOpModeField(opMode, "internalOpModeServices", services)
}

private fun setOpModeField(opMode: OpMode, name: String, value: Any) {
    var cls: Class<*>? = opMode.javaClass
    while (cls != null) {
        try {
            val field = cls.getDeclaredField(name)
            field.isAccessible = true
            field.set(opMode, value)
            return
        } catch (e: NoSuchFieldException) {
            cls = cls.superclass
        }
    }
    throw NoSuchFieldException(name)
}
