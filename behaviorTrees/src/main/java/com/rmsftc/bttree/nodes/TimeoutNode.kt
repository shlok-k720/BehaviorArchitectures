package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus
import com.rmsftc.utils.Clock
import com.rmsftc.utils.blackboard.Blackboard

/**
 * Fails the child after [durationMs] milliseconds. The clock is a
 * nanosecond [Clock] so tests can drive it with a fake clock instead of
 * sleeping; it defaults to `System.nanoTime()`.
 */
class TimeoutNode(
    name: String,
    blackboard: Blackboard,
    private val durationMs: Long,
    private val child: BehaviorNode,
    private val clock: Clock = Clock.SYSTEM
) : BehaviorNode(name, blackboard) {
    private var startTimeNs = 0L

    override fun children(): List<BehaviorNode> = listOf(child)

    override fun onStart() {
        // Monotonic clock: wall-clock time can jump forwards or
        // backwards, elapsed time must not depend on it.
        startTimeNs = clock.nowNanos()
    }

    override fun onTick(): NodeStatus {
        val elapsedMilliseconds = (clock.nowNanos() - startTimeNs) / 1_000_000
        if (elapsedMilliseconds >= durationMs) {
            child.invalidate() // Force abort the running action
            return NodeStatus.FAILURE
        }

        return child.tick()
    }

    override fun onEnd() {
        // No-op if the child already finished: BehaviorNode.invalidate()
        // only acts on a started node, so onEnd() is not called twice
        // for the child when it terminated normally.
        child.invalidate()
    }

    override fun reset() {
        super.reset()
        startTimeNs = 0L
        child.reset()
    }
}
