package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus

class TimeoutNode(name: String, blackboard: Blackboard, private val durationMs: Long, private val child: BehaviorNode) : BehaviorNode(name, blackboard) {
    /**
     * Creates a timeout from a [java.time.Duration] instead of a raw millisecond count,
     * so call sites can write `Duration.ofSeconds(2)` instead of a magic number.
     */
    constructor(name: String, blackboard: Blackboard, duration: java.time.Duration, child: BehaviorNode) :
        this(name, blackboard, duration.toMillis(), child)

    private var startTimeNs = 0L

    override fun children(): List<BehaviorNode> = listOf(child)

    override fun onStart() {
        // Monotonic clock: wall-clock time can jump forwards or
        // backwards, elapsed time must not depend on it.
        startTimeNs = System.nanoTime()
    }

    override fun onTick(): NodeStatus {
        val elapsedMilliseconds = (System.nanoTime() - startTimeNs) / 1_000_000
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
