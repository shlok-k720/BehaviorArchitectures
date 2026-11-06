package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.TestBehaviorNode
import com.rmsftc.bttree.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParallelNodeTest {
    @Test
    fun `does not retick finished children while waiting for other children`() {
        val board = Blackboard()
        val finished = TestBehaviorNode("finished", board, mutableListOf(NodeStatus.SUCCESS))
        val running = TestBehaviorNode("running", board, mutableListOf(NodeStatus.RUNNING, NodeStatus.SUCCESS))
        val node = ParallelNode("parallel", board, ParallelPolicy.SUCCESS, listOf(finished, running))

        assertEquals(NodeStatus.RUNNING, node.tick())
        assertEquals(NodeStatus.SUCCESS, node.tick())
        assertEquals(1, finished.ticks)
        assertEquals(2, running.ticks)
    }

    @Test
    fun `terminal parallel aborts still running children and reset resets all children`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board)
        val node = ParallelNode("parallel", board, ParallelPolicy.JOIN, listOf(child))

        assertEquals(NodeStatus.RUNNING, node.tick())
        node.invalidate()
        assertEquals(1, child.ends)
        node.reset()
        assertEquals(null, child.status)
    }
}
