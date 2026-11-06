package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.blackboard.Blackboard

/**
 * Decorator that always succeeds: a child's FAILURE is reported as SUCCESS, while
 * SUCCESS and RUNNING pass through unchanged. The child still runs to completion;
 * only its failure is tolerated, so this is for best-effort steps that must not
 * fail the sequence they sit in.
 */
class SucceederNode(name: String, blackboard: Blackboard, private val child: BehaviorNode) : BehaviorNode(name, blackboard) {
    override fun children(): List<BehaviorNode> = listOf(child)

    override fun onTick(): NodeStatus {
        return when (child.tick()) {
            NodeStatus.SUCCESS -> NodeStatus.SUCCESS
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
