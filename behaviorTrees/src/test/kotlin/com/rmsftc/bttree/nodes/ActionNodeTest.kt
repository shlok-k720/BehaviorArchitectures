package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.NullStatusSupplier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.function.Supplier

class ActionNodeTest {
    @Test
    fun `runs start tick and end across a running action`() {
        val events = mutableListOf<String>()
        val results = ArrayDeque(listOf(NodeStatus.RUNNING, NodeStatus.SUCCESS))
        val node = ActionNode(
            "action",
            Blackboard(),
            { events += "start" },
            Supplier { results.removeFirst() },
            { events += "end" }
        )

        assertEquals(NodeStatus.RUNNING, node.tick())
        assertEquals(NodeStatus.SUCCESS, node.tick())
        assertEquals(listOf("start", "end"), events)
    }

    @Test
    fun `null Java supplier results fail safely`() {
        val node = ActionNode("action", Blackboard(), null, NullStatusSupplier(), null)

        assertEquals(NodeStatus.FAILURE, node.tick())
    }
}
