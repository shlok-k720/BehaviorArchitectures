package com.rmsftc.bttree

import com.rmsftc.bttree.blackboard.Blackboard

internal class TestBehaviorNode(
    name: String,
    blackboard: Blackboard,
    private val results: MutableList<NodeStatus> = mutableListOf(NodeStatus.RUNNING)
) : BehaviorNode(name, blackboard) {
    var starts = 0
    var ticks = 0
    var ends = 0

    override fun onStart() {
        starts++
    }

    override fun onTick(): NodeStatus {
        ticks++
        return if (results.isEmpty()) NodeStatus.RUNNING else results.removeAt(0)
    }

    override fun onEnd() {
        ends++
    }
}
