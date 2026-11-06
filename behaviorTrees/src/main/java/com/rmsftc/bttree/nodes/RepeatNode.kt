package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.blackboard.Blackboard

/**
 * Decorator that ticks its child until it has succeeded [targetCount] times, then
 * returns SUCCESS. A single FAILURE fails this node immediately, and a RUNNING child
 * passes through unchanged without counting toward the total.
 *
 * @param targetCount how many child successes are required; must be at least 1.
 */
class RepeatNode(
    name: String,
    blackboard: Blackboard,
    private val targetCount: Int,
    private val child: BehaviorNode
) : BehaviorNode(name, blackboard) {
    init {
        require(targetCount >= 1) { "targetCount must be at least 1, was $targetCount" }
    }

    private var successes = 0

    override fun children(): List<BehaviorNode> = listOf(child)

    override fun onStart() {
        successes = 0
    }

    override fun onTick(): NodeStatus {
        while (true) {
            when (child.tick()) {
                NodeStatus.SUCCESS -> {
                    successes++
                    if (successes >= targetCount) return NodeStatus.SUCCESS
                    // Otherwise loop and tick the child again immediately:
                    // a finished child restarts fresh on its next tick.
                }
                NodeStatus.RUNNING -> return NodeStatus.RUNNING
                NodeStatus.FAILURE -> return NodeStatus.FAILURE
            }
        }
    }

    override fun onEnd() {
        // Aborts the in-progress repetition, if any. No-op if the child
        // already finished: BehaviorNode.invalidate() only acts on a
        // started node.
        child.invalidate()
    }

    override fun reset() {
        super.reset()
        successes = 0
        child.reset()
    }
}
