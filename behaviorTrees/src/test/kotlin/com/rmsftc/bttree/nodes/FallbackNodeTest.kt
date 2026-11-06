package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.TestBehaviorNode
import com.rmsftc.bttree.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FallbackNodeTest {
    @Test
    fun `falls through failures to the first success`() {
        val board = Blackboard()
        val failing = TestBehaviorNode("failing", board, mutableListOf(NodeStatus.FAILURE))
        val succeeding = TestBehaviorNode("succeeding", board, mutableListOf(NodeStatus.SUCCESS))
        val node = FallbackNode("fallback", board, listOf(failing, succeeding))

        assertEquals(NodeStatus.SUCCESS, node.tick())
        assertEquals(1, failing.ticks)
        assertEquals(1, succeeding.ticks)
    }

    @Test
    fun `all failures fail`() {
        val board = Blackboard()
        val first = TestBehaviorNode("first", board, mutableListOf(NodeStatus.FAILURE))
        val second = TestBehaviorNode("second", board, mutableListOf(NodeStatus.FAILURE))
        val node = FallbackNode("fallback", board, listOf(first, second))

        assertEquals(NodeStatus.FAILURE, node.tick())
    }

    @Test
    fun `does not re-evaluate higher priority children while a lower one runs`() {
        val board = Blackboard()
        // Would succeed if re-ticked, like a reactive selector would do.
        val first = TestBehaviorNode(
            "first", board, mutableListOf(NodeStatus.FAILURE, NodeStatus.SUCCESS)
        )
        val second = TestBehaviorNode(
            "second", board, mutableListOf(NodeStatus.RUNNING, NodeStatus.SUCCESS)
        )
        val node = FallbackNode("fallback", board, listOf(first, second))

        assertEquals(NodeStatus.RUNNING, node.tick())
        assertEquals(NodeStatus.SUCCESS, node.tick())
        // First child was ticked once (its failure) and never again.
        assertEquals(1, first.ticks)
        assertEquals(2, second.ticks)
    }

    @Test
    fun `failure aborts current child and reset resets descendants`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board)
        val node = FallbackNode("fallback", board, listOf(child))

        assertEquals(NodeStatus.RUNNING, node.tick())
        node.invalidate()
        assertEquals(1, child.ends)
        node.reset()
        assertEquals(null, child.status)
    }
}
