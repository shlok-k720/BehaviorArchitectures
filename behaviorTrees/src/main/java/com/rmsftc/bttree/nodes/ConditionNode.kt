package com.rmsftc.bttree.nodes

import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus
import java.util.function.BooleanSupplier

class ConditionNode(name: String, blackboard: Blackboard, private val check: BooleanSupplier) : BehaviorNode(name, blackboard) {
    override fun onTick() = if (check.asBoolean) NodeStatus.SUCCESS else NodeStatus.FAILURE
}