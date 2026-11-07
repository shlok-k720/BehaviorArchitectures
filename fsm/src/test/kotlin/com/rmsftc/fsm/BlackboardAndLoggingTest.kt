package com.rmsftc.fsm

import com.rmsftc.util.blackboard.Blackboard
import com.rmsftc.util.logger.LogSystem
import com.rmsftc.util.logger.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A Case that exposes the machine's blackboard, for tests. */
class BoardCase(state: State) : Case(state) {
    var sawBoard: Blackboard? = null
        private set
    var readFull: Boolean? = null
        private set

    override fun onExecute() {
        sawBoard = blackboard()
    }
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
    fun `cases share the machine blackboard`() {
        val board = Blackboard()
        val writer = object : Case(State.of("write")) {
            override fun onExecute() {
                blackboard().set("full", "yes")
                transitionToNext()
            }
        }
        val reader = BoardCase(State.of("read"))
        val m = StateMachine(board, writer, reader)
        assertEquals(board, m.blackboard())
        m.start()
        m.update() // writer sets "full", advances
        assertEquals(State.of("read"), m.getCurrentState())
        m.update() // reader records the board it sees
        assertEquals(board, reader.sawBoard)
        assertEquals("yes", reader.sawBoard?.getIfPresent("full", String::class.java))
        // Outside code sees the same board too.
        assertEquals("yes", board.getIfPresent("full", String::class.java))
    }

    @Test
    fun `a machine without a board gets its own`() {
        val a = BoardCase(State.of("a"))
        val m = StateMachine(a)
        m.start()
        m.update()
        assertEquals(m.blackboard(), a.sawBoard)
    }

    @Test
    fun `transitions log entry, exit and finish`() {
        val a = TestCase(State.of("a"))
        val b = TestCase(State.of("b"))
        a.advanceOnExecute = true
        b.advanceOnExecute = true
        val m = StateMachine(a, b)
        m.start()
        assertEquals(
            listOf(Logger.FSM_STATE_ENTERED to "state=a"),
            events
        )
        m.update() // a -> b
        assertEquals(
            listOf(
                Logger.FSM_STATE_ENTERED to "state=a",
                Logger.FSM_STATE_EXITED to "state=a,to=b",
                Logger.FSM_STATE_ENTERED to "state=b"
            ),
            events
        )
        m.update() // b finishes
        assertEquals(
            listOf(
                Logger.FSM_STATE_ENTERED to "state=a",
                Logger.FSM_STATE_EXITED to "state=a,to=b",
                Logger.FSM_STATE_ENTERED to "state=b",
                Logger.FSM_STATE_EXITED to "state=b",
                Logger.FSM_FINISHED to "state=b"
            ),
            events
        )
        assertTrue(m.isFinished())
    }

    @Test
    fun `stop logs the exit`() {
        val a = TestCase(State.of("a"))
        val m = StateMachine(a)
        m.start()
        m.stop()
        assertEquals(
            listOf(
                Logger.FSM_STATE_ENTERED to "state=a",
                Logger.FSM_STATE_EXITED to "state=a"
            ),
            events
        )
    }
}
