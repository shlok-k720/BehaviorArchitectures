package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.TestBehaviorNode
import com.rmsftc.bttree.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RepeatNodeTest {
    @Test
    fun `succeeds after the target number of child successes`() {
        val board = Blackboard()
        val child = TestBehaviorNode(
            "child", board,
            mutableListOf(NodeStatus.SUCCESS, NodeStatus.SUCCESS, NodeStatus.SUCCESS)
        )
        val node = RepeatNode("repeat", board, 3, child)

        assertEquals(NodeStatus.SUCCESS, node.tick())
        assertEquals(3, child.ticks)
    }

    @Test
    fun `a single child failure fails the node immediately`() {
        val board = Blackboard()
        val child = TestBehaviorNode(
            "child", board, mutableListOf(NodeStatus.SUCCESS, NodeStatus.FAILURE)
        )
        val node = RepeatNode("repeat", board, 3, child)

        assertEquals(NodeStatus.FAILURE, node.tick())
        assertEquals(2, child.ticks)
    }

    @Test
    fun `running passes through without counting toward the total`() {
        val board = Blackboard()
        val child = TestBehaviorNode(
            "child", board,
            mutableListOf(NodeStatus.RUNNING, NodeStatus.SUCCESS, NodeStatus.SUCCESS)
        )
        val node = RepeatNode("repeat", board, 2, child)

        assertEquals(NodeStatus.RUNNING, node.tick())
        assertEquals(NodeStatus.SUCCESS, node.tick())
        assertEquals(3, child.ticks)
    }

    @Test
    fun `targetCount below one is rejected`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board)

        assertThrows(IllegalArgumentException::class.java) {
            RepeatNode("repeat", board, 0, child)
        }
    }
}
