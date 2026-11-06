package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ParallelPolicyTest {
    @Test
    fun `join waits for all children then succeeds`() {
        assertEquals(NodeStatus.RUNNING, ParallelPolicy.JOIN.status(listOf(NodeStatus.SUCCESS, NodeStatus.RUNNING)))
        assertEquals(NodeStatus.SUCCESS, ParallelPolicy.JOIN.status(listOf(NodeStatus.FAILURE)))
    }

    @Test
    fun `success requires every completed child to succeed`() {
        assertEquals(NodeStatus.RUNNING, ParallelPolicy.SUCCESS.status(listOf(NodeStatus.SUCCESS, NodeStatus.RUNNING)))
        assertEquals(NodeStatus.SUCCESS, ParallelPolicy.SUCCESS.status(listOf(NodeStatus.SUCCESS, NodeStatus.SUCCESS)))
        assertEquals(NodeStatus.FAILURE, ParallelPolicy.SUCCESS.status(listOf(NodeStatus.SUCCESS, NodeStatus.FAILURE)))
    }

    @Test
    fun `failure fails only when every completed child failed`() {
        assertEquals(NodeStatus.RUNNING, ParallelPolicy.FAILURE.status(listOf(NodeStatus.FAILURE, NodeStatus.RUNNING)))
        assertEquals(NodeStatus.FAILURE, ParallelPolicy.FAILURE.status(listOf(NodeStatus.FAILURE, NodeStatus.FAILURE)))
        assertEquals(NodeStatus.SUCCESS, ParallelPolicy.FAILURE.status(listOf(NodeStatus.FAILURE, NodeStatus.SUCCESS)))
    }

    @Test
    fun `any success succeeds when any completed child succeeded`() {
        assertEquals(NodeStatus.RUNNING, ParallelPolicy.ANY_SUCCESS.status(listOf(NodeStatus.FAILURE, NodeStatus.RUNNING)))
        assertEquals(NodeStatus.SUCCESS, ParallelPolicy.ANY_SUCCESS.status(listOf(NodeStatus.FAILURE, NodeStatus.SUCCESS)))
        assertEquals(NodeStatus.FAILURE, ParallelPolicy.ANY_SUCCESS.status(listOf(NodeStatus.FAILURE, NodeStatus.FAILURE)))
        assertEquals(NodeStatus.FAILURE, ParallelPolicy.ANY_SUCCESS.status(emptyList()))
    }

    @Test
    fun `at least succeeds only once the threshold is met`() {
        val atLeastTwo = ParallelPolicy.atLeast(2)
        assertEquals(NodeStatus.RUNNING, atLeastTwo.status(listOf(NodeStatus.SUCCESS, NodeStatus.RUNNING)))
        assertEquals(NodeStatus.FAILURE, atLeastTwo.status(listOf(NodeStatus.SUCCESS, NodeStatus.FAILURE)))
        assertEquals(NodeStatus.SUCCESS, atLeastTwo.status(listOf(NodeStatus.SUCCESS, NodeStatus.SUCCESS, NodeStatus.FAILURE)))
        assertEquals(NodeStatus.FAILURE, atLeastTwo.status(emptyList()))
    }

    @Test
    fun `at least rejects a threshold below one`() {
        assertThrows(IllegalArgumentException::class.java) { ParallelPolicy.atLeast(0) }
    }
}
