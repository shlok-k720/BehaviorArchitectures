package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.blackboard.Blackboard

/**
 * A selector with memory: ticks children in priority order and returns the first
 * SUCCESS or RUNNING result, but — unlike [ReactiveSelectorNode] — it does not
 * re-evaluate higher-priority children on every tick. Once a child is RUNNING,
 * later ticks resume from that child, so a lower-priority branch is never preempted
 * mid-execution by a higher-priority child that became true meanwhile. Returns
 * FAILURE only when every child has failed.
 */
class FallbackNode(name: String, board: Blackboard, children: List<BehaviorNode>) : BehaviorNode(name, board) {

    // Defensive copy: the caller's list must not be able to mutate
    // this node's children after construction.
    private val childList: List<BehaviorNode> = children.toList()

    private var currentIndex = 0

    override fun children(): List<BehaviorNode> = childList

    override fun onTick(): NodeStatus {
        while (currentIndex < childList.size) {
            when (childList[currentIndex].tick()) {
                NodeStatus.SUCCESS -> return NodeStatus.SUCCESS
                NodeStatus.RUNNING -> return NodeStatus.RUNNING
                NodeStatus.FAILURE -> currentIndex++
            }
        }
        return NodeStatus.FAILURE
    }

    override fun onEnd() {
        // Only aborts the current child when this node itself is being
        // aborted (or failed while the child was still running). When
        // the child already finished, its invalidate() is a no-op under
        // BehaviorNode's started-guard, so onEnd() is never called
        // twice for the same activation.
        if (status == NodeStatus.FAILURE && currentIndex < childList.size) {
            childList[currentIndex].invalidate()
        }
        // The run is over: the next activation starts from the first child.
        currentIndex = 0
    }

    override fun reset() {
        super.reset()
        currentIndex = 0
        childList.forEach { it.reset() }
    }
}
