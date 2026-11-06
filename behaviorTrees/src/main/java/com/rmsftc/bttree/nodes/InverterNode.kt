package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.blackboard.Blackboard

/**
 * Decorator that inverts its child's terminal result: SUCCESS becomes FAILURE and
 * FAILURE becomes SUCCESS. A RUNNING child passes through unchanged, so an in-progress
 * child is never reported as finished.
 */
class InverterNode(name: String, blackboard: Blackboard, private val child: BehaviorNode) : BehaviorNode(name, blackboard) {
    override fun children(): List<BehaviorNode> = listOf(child)

    override fun onTick(): NodeStatus {
        return when (child.tick()) {
            NodeStatus.SUCCESS -> NodeStatus.FAILURE
            NodeStatus.FAILURE -> NodeStatus.SUCCESS
            NodeStatus.RUNNING -> NodeStatus.RUNNING
        }
    }

    override fun onEnd() {
        // No-op if the child already finished: BehaviorNode.invalidate()
        // only acts on a started node, so onEnd() is not called twice
        // for the child when it terminated normally.
        child.invalidate()
    }

    override fun reset() {
        super.reset()
        child.reset()
    }
}
