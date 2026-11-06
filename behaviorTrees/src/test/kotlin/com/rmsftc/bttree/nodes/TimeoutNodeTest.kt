package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.TestBehaviorNode
import com.rmsftc.bttree.blackboard.Blackboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class TimeoutNodeTest {
    @Test
    fun `returns child result before deadline and resets child`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board, mutableListOf(NodeStatus.SUCCESS))
        val timeout = TimeoutNode("timeout", board, 1_000, child)

        assertEquals(NodeStatus.SUCCESS, timeout.tick())
        timeout.reset()
        assertEquals(null, child.status)
    }

    @Test
    fun `deadline fails and aborts active child`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board)
        val timeout = TimeoutNode("timeout", board, 1, child)

        assertEquals(NodeStatus.RUNNING, timeout.tick())
        Thread.sleep(10)
        assertEquals(NodeStatus.FAILURE, timeout.tick())
        assertEquals(NodeStatus.FAILURE, child.status)
        assertEquals(1, child.ends)
    }

    @Test
    fun `timeout restarts its timer when ticked again after expiry`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board)
        val timeout = TimeoutNode("timeout", board, 1, child)

        assertEquals(NodeStatus.RUNNING, timeout.tick())
        Thread.sleep(10)
        assertEquals(NodeStatus.FAILURE, timeout.tick())
        // A fresh activation restarts the timer instead of failing at once.
        assertEquals(NodeStatus.RUNNING, timeout.tick())
    }

    @Test
    fun `duration constructor accepts a Duration`() {
        val board = Blackboard()
        val child = TestBehaviorNode("child", board, mutableListOf(NodeStatus.SUCCESS))
        val timeout = TimeoutNode("timeout", board, Duration.ofSeconds(5), child)

        assertEquals(NodeStatus.SUCCESS, timeout.tick())
    }
}
