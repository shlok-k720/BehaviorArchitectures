package com.rmsftc.bttree.builders

import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.nodes.ActionNode
import com.rmsftc.bttree.nodes.ConditionNode
import com.rmsftc.bttree.nodes.FallbackNode
import com.rmsftc.bttree.nodes.InverterNode
import com.rmsftc.bttree.nodes.ParallelNode
import com.rmsftc.bttree.nodes.ParallelPolicy
import com.rmsftc.bttree.nodes.ReactiveSelectorNode
import com.rmsftc.bttree.nodes.RepeatNode
import com.rmsftc.bttree.nodes.RetryNode
import com.rmsftc.bttree.nodes.SequenceNode
import com.rmsftc.bttree.nodes.SucceederNode
import com.rmsftc.bttree.nodes.TimeoutNode
import java.time.Duration
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Supplier

class JavaBuilder private constructor(private val blackboard: Blackboard) {
    private val children = ArrayList<BehaviorNode>()

    companion object {
        // Single master entry point for an OpMode to build a tree root
        @JvmStatic
        fun rootTree(blackboard: Blackboard, block: Consumer<JavaBuilder>): ReactiveSelectorNode {
            val builder = JavaBuilder(blackboard)
            block.accept(builder)
            return ReactiveSelectorNode("Root", blackboard, builder.children.toList())
        }
    }

    // Secondary constructor used internally to fork nested scopes
    private constructor(blackboard: Blackboard, nestedBlock: Consumer<JavaBuilder>) : this(blackboard) {
        nestedBlock.accept(this)
    }

    fun node(node: BehaviorNode): JavaBuilder {
        children.add(node)
        return this
    }

    fun sequence(name: String, block: Consumer<JavaBuilder>): JavaBuilder {
        val nestedBuilder = JavaBuilder(blackboard, block)
        children.add(SequenceNode(name, blackboard, nestedBuilder.children.toList()))
        return this
    }

    fun reactiveSelector(name: String, block: Consumer<JavaBuilder>): JavaBuilder {
        val nestedBuilder = JavaBuilder(blackboard, block)
        children.add(ReactiveSelectorNode(name, blackboard, nestedBuilder.children.toList()))
        return this
    }

    fun fallback(name: String, block: Consumer<JavaBuilder>): JavaBuilder {
        val nestedBuilder = JavaBuilder(blackboard, block)
        children.add(FallbackNode(name, blackboard, nestedBuilder.children.toList()))
        return this
    }

    fun condition(name: String, check: BooleanSupplier): JavaBuilder {
        children.add(ConditionNode(name, blackboard, check))
        return this
    }

    fun action(name: String, start: Runnable?, tick: Supplier<NodeStatus>, end: Runnable?): JavaBuilder {
        children.add(ActionNode(name, blackboard, { start?.run() }, tick, { end?.run() }))
        return this
    }

    // Tick-only action: no start or end callbacks.
    fun action(name: String, tick: Supplier<NodeStatus>): JavaBuilder {
        return action(name, null, tick, null)
    }

    // Decorators wrap exactly one child. The block form requires the
    // block to build a single node (wrap several nodes in sequence()
    // first); the node form takes an already-built node.
    private fun singleChild(decoratorKind: String, decoratorName: String, block: Consumer<JavaBuilder>): BehaviorNode {
        val nestedBuilder = JavaBuilder(blackboard, block)
        require(nestedBuilder.children.size == 1) {
            "$decoratorKind \"$decoratorName\" requires exactly one child node, " +
                "but the block built ${nestedBuilder.children.size}"
        }
        return nestedBuilder.children.single()
    }

    fun inverter(name: String, block: Consumer<JavaBuilder>): JavaBuilder {
        children.add(InverterNode(name, blackboard, singleChild("inverter", name, block)))
        return this
    }

    fun inverter(name: String, child: BehaviorNode): JavaBuilder {
        children.add(InverterNode(name, blackboard, child))
        return this
    }

    fun succeeder(name: String, block: Consumer<JavaBuilder>): JavaBuilder {
        children.add(SucceederNode(name, blackboard, singleChild("succeeder", name, block)))
        return this
    }

    fun succeeder(name: String, child: BehaviorNode): JavaBuilder {
        children.add(SucceederNode(name, blackboard, child))
        return this
    }

    fun retry(name: String, maxAttempts: Int, block: Consumer<JavaBuilder>): JavaBuilder {
        children.add(RetryNode(name, blackboard, maxAttempts, singleChild("retry", name, block)))
        return this
    }

    fun retry(name: String, maxAttempts: Int, child: BehaviorNode): JavaBuilder {
        children.add(RetryNode(name, blackboard, maxAttempts, child))
        return this
    }

    fun repeat(name: String, targetCount: Int, block: Consumer<JavaBuilder>): JavaBuilder {
        children.add(RepeatNode(name, blackboard, targetCount, singleChild("repeat", name, block)))
        return this
    }

    fun repeat(name: String, targetCount: Int, child: BehaviorNode): JavaBuilder {
        children.add(RepeatNode(name, blackboard, targetCount, child))
        return this
    }

    // hardcodes reactive selector as the child of the timeout node, so that if the timeout expires, the entire subtree is invalidated
    fun waitMs(name: String, durationMs: Long, block: Consumer<JavaBuilder>): JavaBuilder {
        val nestedBuilder = JavaBuilder(blackboard, block)
        children.add(
            TimeoutNode(
                name,
                blackboard,
                durationMs,
                ReactiveSelectorNode("$name-inner", blackboard, nestedBuilder.children.toList())
            )
        )
        return this
    }

    // Duration overload: waitMs("intake", Duration.ofSeconds(2)) { ... }
    fun waitMs(name: String, duration: Duration, block: Consumer<JavaBuilder>): JavaBuilder {
        return waitMs(name, duration.toMillis(), block)
    }

    fun parallel(name: String, policy: ParallelPolicy, block: Consumer<JavaBuilder>): JavaBuilder {
        val nestedBuilder = JavaBuilder(blackboard, block)
        children.add(
            ParallelNode(
                name,
                blackboard,
                policy,
                nestedBuilder.children.toList()
            )
        )
        return this
    }

    fun onCallbackTriggered(name: String, key: String, callback: Consumer<Any?>, block: Consumer<JavaBuilder>): JavaBuilder {
        val nestedBuilder = JavaBuilder(blackboard, block)
        val compositeNode = SequenceNode(name, blackboard, nestedBuilder.children.toList())

        // Intentionally registered at build time, not in a node
        // lifecycle: this is an interceptor, so the callback must
        // fire on Blackboard.set() even while an earlier branch is
        // RUNNING and this branch is not being ticked (for example
        // drive.stop() when target_stolen is set mid-drive). It
        // stays registered for the lifetime of this blackboard.
        // Build a tree once per blackboard, or call
        // Blackboard.clearAll()/deregisterCallback() before
        // rebuilding on the same blackboard, or callbacks will
        // accumulate on rebuilds.
        blackboard.registerCallback(key) { _, newValue -> callback.accept(newValue) }

        children.add(compositeNode)
        return this
    }
}
