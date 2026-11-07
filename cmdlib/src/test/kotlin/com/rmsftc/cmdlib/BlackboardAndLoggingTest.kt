package com.rmsftc.cmdlib

import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.cmdlib.cmd.groups.SequentialCommandGroup
import com.rmsftc.cmdlib.scheduler.QueueScheduler
import com.rmsftc.cmdlib.scheduler.Scheduler
import com.rmsftc.util.blackboard.Blackboard
import com.rmsftc.util.logger.LogSystem
import com.rmsftc.util.logger.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.function.BooleanSupplier

/** A command that records which board it runs with. */
class BoardCommand(
    private val results: List<Status> = listOf(Status.FINISHED),
    vararg tokens: Any
) : Command() {
    var sawBoard: Blackboard? = null
        private set
    var onStart: ((Blackboard) -> Unit)? = null

    private var index = 0

    init {
        addRequirements(*tokens)
    }

    override fun start() {
        sawBoard = blackboard()
        onStart?.invoke(blackboard())
    }

    override fun loop() {}

    override fun status(): Status {
        if (index < results.size) {
            return results[index++]
        }
        return Status.FINISHED
    }

    override fun end(endCondition: EndCondition) {}
}

class BlackboardAndLoggingTest {

    private val events = ArrayList<Pair<String, String>>()
    private val recorder: LogSystem = LogSystem { caption, data, _ -> events.add(caption to data) }

    @BeforeEach
    fun attachRecorder() {
        Logger.reset()
        Logger.attachLogger(recorder)
    }

    @AfterEach
    fun detachRecorder() {
        Logger.reset()
    }

    @Test
    fun `scheduler shares its board with scheduled commands`() {
        val board = Blackboard()
        val s = Scheduler(blackboard = board)
        var seenBySecond: String? = null
        val first = BoardCommand()
        first.onStart = { it.set("shared", "ready") }
        val second = BoardCommand()
        second.onStart = { seenBySecond = it.getIfPresent("shared", String::class.java) }
        s.schedule(first)
        s.execute()
        s.schedule(second)
        s.execute()
        assertEquals(board, first.sawBoard)
        assertEquals(board, second.sawBoard)
        assertEquals("ready", seenBySecond)
    }

    @Test
    fun `group children share the scheduler board`() {
        val board = Blackboard()
        val s = Scheduler(blackboard = board)
        val child1 = BoardCommand()
        val child2 = BoardCommand()
        val group = SequentialCommandGroup(child1, child2)
        s.schedule(group)
        s.execute()
        assertEquals(board, child1.sawBoard)
        assertEquals(board, child2.sawBoard)
    }

    @Test
    fun `logging records schedule, finish, cancel and interrupt`() {
        val s = Scheduler(isOpModeActive = BooleanSupplier { true })
        val instant = BoardCommand()
        instant.name = "instant"
        s.schedule(instant)
        s.execute()
        assertTrue(events.contains(Logger.CMD_SCHEDULED to "name=instant"))
        assertTrue(events.contains(Logger.CMD_FINISHED to "name=instant"))

        val forever = RunForeverCommand("arm")
        forever.name = "forever"
        s.schedule(forever)
        s.execute()
        s.cancel(forever)
        assertTrue(events.contains(Logger.CMD_CANCELLED to "name=forever"))

        val holder = RunForeverCommand("arm")
        holder.name = "holder"
        s.schedule(holder)
        val interrupter = BoardCommand(listOf(Status.FINISHED), "arm")
        interrupter.name = "interrupter"
        interrupter.priority = 10
        s.schedule(interrupter)
        assertTrue(events.contains(Logger.CMD_INTERRUPTED to "name=holder"))
        assertTrue(events.contains(Logger.CMD_SCHEDULED to "name=interrupter"))
    }

    @Test
    fun `queue scheduler shares its board and logs`() {
        val board = Blackboard()
        val s = QueueScheduler(board)
        val cmd = BoardCommand()
        cmd.name = "queued"
        s.schedule(cmd)
        s.execute()
        assertEquals(board, cmd.sawBoard)
        assertTrue(events.contains(Logger.CMD_SCHEDULED to "name=queued"))
        assertTrue(events.contains(Logger.CMD_FINISHED to "name=queued"))
    }

    @Test
    fun `an unscheduled command keeps its own board`() {
        val cmd = BoardCommand()
        cmd.execute()
        val board = cmd.sawBoard
        assertTrue(board != null)
        val other = BoardCommand()
        other.execute()
        assertNotEquals(board, other.sawBoard)
    }
}
