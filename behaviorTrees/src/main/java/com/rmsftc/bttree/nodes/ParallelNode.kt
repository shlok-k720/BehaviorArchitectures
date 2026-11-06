package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.NodeStatus


class ParallelNode(name: String, blackboard: Blackboard, private val policy: ParallelPolicy, children: List<BehaviorNode>) : BehaviorNode(name, blackboard) {

    // Defensive copy: the caller's list must not be able to mutate
    // this node's children after construction.
    private val childList: List<BehaviorNode> = children.toList()

    // Latched terminal result per child. A child that already finished
    // is not re-ticked until this node starts a fresh activation, so
    // a finished ActionNode's onStart()/tick do not run again on
    // every parallel tick.
    private val finished = MutableList(childList.size) { false }
    private val childStatuses = MutableList(childList.size) { NodeStatus.RUNNING }

    override fun children(): List<BehaviorNode> = childList

    override fun onStart() {
        finished.fill(false)
        childStatuses.fill(NodeStatus.RUNNING)
    }

    override fun onTick(): NodeStatus {
        childList.forEachIndexed { index, child ->
            if (!finished[index]) {
                val childStatus = child.tick()
                childStatuses[index] = childStatus
                if (childStatus != NodeStatus.RUNNING) {
                    finished[index] = true
                }
            }
        }
        return policy.status(childStatuses.toList())
    }

    override fun onEnd() {
        // Aborts only still-running children: BehaviorNode.invalidate()
        // is a no-op on finished children, so a successful child is
        // not flipped to FAILURE here.
        childList.forEach { it.invalidate() }
    }

    override fun reset() {
        super.reset()
        finished.fill(false)
        childStatuses.fill(NodeStatus.RUNNING)
        childList.forEach { it.reset() }
    }

}
