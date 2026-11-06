package com.rmsftc.bttree

import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.nodes.ConditionNode
import com.rmsftc.bttree.nodes.InverterNode
import com.rmsftc.bttree.nodes.SequenceNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TreeTraversalTest {
    private fun sampleTree(board: Blackboard): SequenceNode {
        val a = ConditionNode("a", board) { true }
        val b = ConditionNode("b", board) { false }
        val inverter = InverterNode("inv", board, b)
        return SequenceNode("seq", board, listOf(a, inverter))
    }

    @Test
    fun `children lists direct children and leaves are empty`() {
        val board = Blackboard()
        val tree = sampleTree(board)
        val inverter = tree.children()[1]

        assertEquals(2, tree.children().size)
        assertEquals(1, inverter.children().size)
        assertEquals(0, tree.children()[0].children().size)
    }

    @Test
    fun `forEachNode visits the whole tree depth first`() {
        val board = Blackboard()
        val tree = sampleTree(board)
        val names = mutableListOf<String>()

        tree.forEachNode { names += it.name }

        assertEquals(listOf("seq", "a", "inv", "b"), names)
    }

    @Test
    fun `toTreeString prints names types and statuses`() {
        val board = Blackboard()
        val tree = sampleTree(board)

        assertEquals(NodeStatus.SUCCESS, tree.tick())
        assertEquals(
            "seq [SequenceNode] SUCCESS\n" +
                "  a [ConditionNode] SUCCESS\n" +
                "  inv [InverterNode] SUCCESS\n" +
                "    b [ConditionNode] FAILURE",
            tree.toTreeString()
        )
    }
}
