package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConditionNodeTest {
    @Test
    fun `maps predicate results to terminal statuses`() {
        assertEquals(NodeStatus.SUCCESS, ConditionNode("true", Blackboard()) { true }.tick())
        assertEquals(NodeStatus.FAILURE, ConditionNode("false", Blackboard()) { false }.tick())
    }
}
