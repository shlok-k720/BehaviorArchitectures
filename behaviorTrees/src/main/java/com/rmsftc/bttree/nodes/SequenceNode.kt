package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus

class SequenceNode(name: String, board: Blackboard, children: List<BehaviorNode>) : BehaviorNode(name, board) {

    // Defensive copy: the caller's list must not be able to mutate
    // this node's children after construction.
    private val childList: List<BehaviorNode> = children.toList()

    private var currentIndex = 0

    override fun children(): List<BehaviorNode> = childList

    override fun onStart() { currentIndex = 0 }

    override fun onTick(): NodeStatus {
        while (currentIndex < childList.size) {
            val childStatus = childList[currentIndex].tick()
            when (childStatus) {
                NodeStatus.RUNNING -> return NodeStatus.RUNNING
                NodeStatus.FAILURE -> return NodeStatus.FAILURE
                NodeStatus.SUCCESS -> currentIndex++
            }
        }
        return NodeStatus.SUCCESS
    }

    override fun onEnd() {
        // Only aborts the current child when this sequence itself is
        // being aborted (or failed while the child was still running).
        // When the child already finished, its invalidate() is a
        // no-op under BehaviorNode's started-guard, so onEnd() is
        // never called twice for the same activation.
        if (status == NodeStatus.FAILURE && currentIndex < childList.size) {
            childList[currentIndex].invalidate()
        }
    }

    override fun reset() {
        super.reset()
        childList.forEach { it.reset() }
    }
}
