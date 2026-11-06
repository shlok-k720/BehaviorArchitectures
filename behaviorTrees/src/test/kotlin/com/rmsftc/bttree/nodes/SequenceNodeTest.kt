package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.TestBehaviorNode
import com.rmsftc.bttree.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SequenceNodeTest {
    @Test
    fun `continues through successful children and resumes after running child`() {
        val board = Blackboard()
        val first = TestBehaviorNode("first", board, mutableListOf(NodeStatus.SUCCESS))
        val second = TestBehaviorNode("second", board, mutableListOf(NodeStatus.RUNNING, NodeStatus.SUCCESS))
        val third = TestBehaviorNode("third", board, mutableListOf(NodeStatus.SUCCESS))
        val sequence = SequenceNode("sequence", board, listOf(first, second, third))

        assertEquals(NodeStatus.RUNNING, sequence.tick())
        assertEquals(NodeStatus.SUCCESS, sequence.tick())
        assertEquals(1, first.ticks)
        assertEquals(2, second.ticks)
        assertEquals(1, third.ticks)
    }

    @Test
    fun `failure aborts current child and reset resets descendants`() {
        val board = Blackboard()
        val failing = TestBehaviorNode("failing", board, mutableListOf(NodeStatus.FAILURE))
        val sequence = SequenceNode("sequence", board, listOf(failing))

        assertEquals(NodeStatus.FAILURE, sequence.tick())
        assertEquals(1, failing.ends)
        sequence.reset()
        assertEquals(null, failing.status)
    }

    @Test
    fun `mutating the callers child list after construction does not change the sequence`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board, mutableListOf(NodeStatus.SUCCESS))
        val mutable = mutableListOf<BehaviorNode>(child)
        val sequence = SequenceNode("sequence", board, mutable)
        mutable.clear()

        assertEquals(NodeStatus.SUCCESS, sequence.tick())
    }
}
