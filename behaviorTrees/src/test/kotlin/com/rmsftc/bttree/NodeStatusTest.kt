package com.rmsftc.bttree

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NodeStatusTest {
    @Test
    fun `exposes all behavior tree outcomes`() {
        assertEquals(
            listOf(NodeStatus.SUCCESS, NodeStatus.FAILURE, NodeStatus.RUNNING),
            NodeStatus.entries
        )
    }

    @Test
    fun `terminal and running helpers match the enum value`() {
        assertEquals(true, NodeStatus.SUCCESS.isTerminal())
        assertEquals(true, NodeStatus.FAILURE.isTerminal())
        assertEquals(false, NodeStatus.RUNNING.isTerminal())
        assertEquals(false, NodeStatus.SUCCESS.isRunning())
        assertEquals(false, NodeStatus.FAILURE.isRunning())
        assertEquals(true, NodeStatus.RUNNING.isRunning())
    }
}
