package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.TestBehaviorNode
import com.rmsftc.bttree.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReactiveSelectorNodeTest {
    @Test
    fun `higher priority child preempts previously running lower priority child`() {
        val board = Blackboard()
        val highPriority = TestBehaviorNode("high", board, mutableListOf(NodeStatus.FAILURE, NodeStatus.SUCCESS))
        val lowPriority = TestBehaviorNode("low", board)
        val selector = ReactiveSelectorNode("selector", board, listOf(highPriority, lowPriority))

        assertEquals(NodeStatus.RUNNING, selector.tick())
        assertEquals(NodeStatus.SUCCESS, selector.tick())
        assertEquals(1, lowPriority.ends)
        assertEquals(NodeStatus.FAILURE, lowPriority.status)
    }

    @Test
    fun `all failures fail and reset resets descendants`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board, mutableListOf(NodeStatus.FAILURE))
        val selector = ReactiveSelectorNode("selector", board, listOf(child))

        assertEquals(NodeStatus.FAILURE, selector.tick())
        selector.reset()
        assertEquals(null, child.status)
    }
}
