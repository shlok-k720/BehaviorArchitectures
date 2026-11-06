package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus

class ReactiveSelectorNode(name: String, board: Blackboard, children: List<BehaviorNode>) : BehaviorNode(name, board) {
    // Defensive copy: the caller's list must not be able to mutate
    // this node's children after construction.
    private val childList: List<BehaviorNode> = children.toList()

    private var lastRunningIndex = -1

    override fun children(): List<BehaviorNode> = childList

    override fun onStart() {
        lastRunningIndex = -1
    }

    override fun onTick(): NodeStatus {
        for (i in childList.indices) {
            val childStatus = childList[i].tick()
            if (childStatus == NodeStatus.RUNNING || childStatus == NodeStatus.SUCCESS) {
                if (lastRunningIndex != -1 && lastRunningIndex > i) {
                    childList[lastRunningIndex].invalidate()
                }
                lastRunningIndex = if (childStatus == NodeStatus.RUNNING) i else -1
                return childStatus
            }
        }
        lastRunningIndex = -1
        return NodeStatus.FAILURE
    }

    override fun onEnd() {
        if (lastRunningIndex != -1) {
            childList[lastRunningIndex].invalidate()
            lastRunningIndex = -1
        }
    }

    override fun reset() {
        super.reset()
        childList.forEach { it.reset() }
    }
}
