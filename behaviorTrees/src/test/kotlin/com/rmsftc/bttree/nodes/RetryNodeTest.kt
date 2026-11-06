package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.TestBehaviorNode
import com.rmsftc.bttree.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RetryNodeTest {
    @Test
    fun `retries failures until the child succeeds`() {
        val board = Blackboard()
        val child = TestBehaviorNode(
            "child", board,
            mutableListOf(NodeStatus.FAILURE, NodeStatus.FAILURE, NodeStatus.SUCCESS)
        )
        val node = RetryNode("retry", board, 3, child)

        assertEquals(NodeStatus.SUCCESS, node.tick())
        assertEquals(3, child.ticks)
    }

    @Test
    fun `fails after the maximum number of attempts`() {
        val board = Blackboard()
        val child = TestBehaviorNode(
            "child", board,
            mutableListOf(NodeStatus.FAILURE, NodeStatus.FAILURE, NodeStatus.FAILURE)
        )
        val node = RetryNode("retry", board, 2, child)

        assertEquals(NodeStatus.FAILURE, node.tick())
        assertEquals(2, child.ticks)
    }

    @Test
    fun `a running child does not consume an attempt`() {
        val board = Blackboard()
        val child = TestBehaviorNode(
            "child", board, mutableListOf(NodeStatus.RUNNING, NodeStatus.SUCCESS)
        )
        val node = RetryNode("retry", board, 1, child)

        assertEquals(NodeStatus.RUNNING, node.tick())
        assertEquals(NodeStatus.SUCCESS, node.tick())
        assertEquals(2, child.ticks)
    }

    @Test
    fun `invalidate aborts the in-progress attempt`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board)
        val node = RetryNode("retry", board, 3, child)

        assertEquals(NodeStatus.RUNNING, node.tick())
        node.invalidate()
        assertEquals(1, child.ends)
    }

    @Test
    fun `maxAttempts below one is rejected`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board)

        assertThrows(IllegalArgumentException::class.java) {
            RetryNode("retry", board, 0, child)
        }
    }
}
