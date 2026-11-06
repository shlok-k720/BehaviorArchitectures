package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.blackboard.Blackboard

/**
 * Decorator that re-ticks its child until it succeeds, up to [maxAttempts] attempts.
 * A child that is still RUNNING does not consume an attempt: only a finished FAILURE
 * counts, so a slow child is not cut off for taking several ticks. Returns FAILURE
 * once the child has failed [maxAttempts] times.
 *
 * @param maxAttempts how many times the child may fail before this node fails; must be at least 1.
 */
class RetryNode(
    name: String,
    blackboard: Blackboard,
    private val maxAttempts: Int,
    private val child: BehaviorNode
) : BehaviorNode(name, blackboard) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1, was $maxAttempts" }
    }

    private var attempts = 0

    override fun children(): List<BehaviorNode> = listOf(child)

    override fun onStart() {
        attempts = 0
    }

    override fun onTick(): NodeStatus {
        while (true) {
            when (child.tick()) {
                NodeStatus.SUCCESS -> return NodeStatus.SUCCESS
                NodeStatus.RUNNING -> return NodeStatus.RUNNING
                NodeStatus.FAILURE -> {
                    attempts++
                    if (attempts >= maxAttempts) return NodeStatus.FAILURE
                    // Otherwise loop and tick the child again immediately:
                    // a finished child restarts fresh on its next tick.
                }
            }
        }
    }

    override fun onEnd() {
        // Aborts the in-progress attempt, if any. No-op if the child
        // already finished: BehaviorNode.invalidate() only acts on a
        // started node.
        child.invalidate()
    }

    override fun reset() {
        super.reset()
        attempts = 0
        child.reset()
    }
}
