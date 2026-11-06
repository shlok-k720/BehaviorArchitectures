package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.TestBehaviorNode
import com.rmsftc.bttree.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SucceederNodeTest {
    @Test
    fun `failure becomes success and success stays success`() {
        val board = Blackboard()
        val failing = SucceederNode(
            "succeeder", board,
            TestBehaviorNode("child", board, mutableListOf(NodeStatus.FAILURE))
        )
        val succeeding = SucceederNode(
            "succeeder", board,
            TestBehaviorNode("child", board, mutableListOf(NodeStatus.SUCCESS))
        )

        assertEquals(NodeStatus.SUCCESS, failing.tick())
        assertEquals(NodeStatus.SUCCESS, succeeding.tick())
    }

    @Test
    fun `running passes through until the child finishes`() {
        val board = Blackboard()
        val child = TestBehaviorNode(
            "child", board, mutableListOf(NodeStatus.RUNNING, NodeStatus.FAILURE)
        )
        val node = SucceederNode("succeeder", board, child)

        assertEquals(NodeStatus.RUNNING, node.tick())
        assertEquals(NodeStatus.SUCCESS, node.tick())
    }
}
