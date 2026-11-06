package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.TestBehaviorNode
import com.rmsftc.bttree.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InverterNodeTest {
    @Test
    fun `inverts success to failure and failure to success`() {
        val board = Blackboard()
        val succeeding = InverterNode(
            "inverter", board,
            TestBehaviorNode("child", board, mutableListOf(NodeStatus.SUCCESS))
        )
        val failing = InverterNode(
            "inverter", board,
            TestBehaviorNode("child", board, mutableListOf(NodeStatus.FAILURE))
        )

        assertEquals(NodeStatus.FAILURE, succeeding.tick())
        assertEquals(NodeStatus.SUCCESS, failing.tick())
    }

    @Test
    fun `running passes through until the child finishes`() {
        val board = Blackboard()
        val child = TestBehaviorNode(
            "child", board, mutableListOf(NodeStatus.RUNNING, NodeStatus.SUCCESS)
        )
        val node = InverterNode("inverter", board, child)

        assertEquals(NodeStatus.RUNNING, node.tick())
        assertEquals(NodeStatus.FAILURE, node.tick())
    }

    @Test
    fun `invalidate aborts a running child`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board)
        val node = InverterNode("inverter", board, child)

        assertEquals(NodeStatus.RUNNING, node.tick())
        node.invalidate()
        assertEquals(1, child.ends)
        assertEquals(NodeStatus.FAILURE, child.status)
    }
}
