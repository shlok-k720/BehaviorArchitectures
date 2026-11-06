package com.rmsftc.bttree

import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.logger.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BehaviorNodeTest {
    @BeforeEach
    fun setUp() = Logger.reset()

    @AfterEach
    fun tearDown() = Logger.reset()

    @Test
    fun `tick starts ends and restarts a completed node`() {
        val node = TestBehaviorNode("node", Blackboard(), mutableListOf(NodeStatus.SUCCESS, NodeStatus.FAILURE))

        assertEquals(NodeStatus.SUCCESS, node.tick())
        assertEquals(1, node.starts)
        assertEquals(1, node.ticks)
        assertEquals(1, node.ends)

        assertEquals(NodeStatus.FAILURE, node.tick())
        assertEquals(2, node.starts)
        assertEquals(2, node.ticks)
        assertEquals(2, node.ends)
    }

    @Test
    fun `invalidate only ends active nodes and reset clears status`() {
        val node = TestBehaviorNode("node", Blackboard())

        assertEquals(NodeStatus.RUNNING, node.tick())
        node.invalidate()
        assertEquals(NodeStatus.FAILURE, node.status)
        assertEquals(1, node.ends)

        node.invalidate()
        assertEquals(1, node.ends)
        node.reset()
        assertNull(node.status)
    }

    @Test
    fun `lifecycle events include status and manual logs include data`() {
        val events = mutableListOf<Pair<String, String>>()
        Logger.attachLogger { caption, data, _ -> events += caption to data }
        val node = object : BehaviorNode("logged", Blackboard()) {
            override fun onTick() = NodeStatus.SUCCESS
            fun writeLog() = log("detail")
        }

        node.tick()
        node.writeLog()

        assertEquals(
            listOf(Logger.NODE_STARTED, Logger.NODE_TICKED, Logger.NODE_FINISHED, "com.rmsftc.bttree: Manual Node Log"),
            events.map { it.first }
        )
        assertEquals("name=logged,type=,status=SUCCESS", events[1].second)
        assertEquals("name=logged,type=,status=SUCCESS,data=detail", events.last().second)
    }
}
