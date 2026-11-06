package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.NodeStatus
import java.util.function.Supplier
import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.BehaviorNode

class ActionNode(name: String, blackboard: Blackboard, private val start: (() -> Unit)?, private val tick: Supplier<NodeStatus>, private val end: (() -> Unit)?) : BehaviorNode(name, blackboard) {
    override fun onStart() {
        start?.invoke()
    }

    override fun onTick(): NodeStatus {
        // Supplier.get() is a platform type: a Java supplier may
        // return null, which would crash BehaviorNode.tick() on
        // `status!!`. Treat a null tick result as FAILURE instead.
        return tick.get() ?: NodeStatus.FAILURE
    }

    override fun onEnd() {
        end?.invoke()
    }
}
