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
import java.util.function.Consumer
import java.util.function.Supplier

@DslMarker
annotation class BTMarker

@BTMarker
abstract class NodeGroupBuilder(protected val blackboard: Blackboard) {
    val children = mutableListOf<BehaviorNode>()

    fun node(node: BehaviorNode) {
        children.add(node)
    }

    fun sequence(name: String, init: KotlinSequenceBuilder.() -> Unit) {
        children.add(KotlinSequenceBuilder(name, blackboard).apply(init).build())
    }

    fun sequence(name: String, init: Consumer<KotlinSequenceBuilder>) {
        children.add(KotlinSequenceBuilder(name, blackboard).apply { init.accept(this) }.build())
    }

    // A selector in this DSL is a reactive selector: it re-ticks
    // from the first child on every tick. See reactiveSelector(),
    // which is the same node under its explicit name.
    fun selector(name: String, init: KotlinSelectorBuilder.() -> Unit) {
        children.add(KotlinSelectorBuilder(name, blackboard).apply(init).build())
    }

    fun selector(name: String, init: Consumer<KotlinSelectorBuilder>) {
        children.add(KotlinSelectorBuilder(name, blackboard).apply { init.accept(this) }.build())
    }

    fun reactiveSelector(name: String, init: KotlinSelectorBuilder.() -> Unit) {
        selector(name, init)
    }

    fun reactiveSelector(name: String, init: Consumer<KotlinSelectorBuilder>) {
        selector(name, init)
    }

    fun fallback(name: String, init: KotlinSelectorBuilder.() -> Unit) {
        val nested = KotlinSelectorBuilder(name, blackboard).apply(init)
        children.add(FallbackNode(name, blackboard, nested.children.toList()))
    }

    fun fallback(name: String, init: Consumer<KotlinSelectorBuilder>) {
        val nested = KotlinSelectorBuilder(name, blackboard).apply { init.accept(this) }
        children.add(FallbackNode(name, blackboard, nested.children.toList()))
    }

    fun condition(name: String, check: () -> Boolean) {
        children.add(ConditionNode(name, blackboard, check))
    }

    fun action(name: String, start: (() -> Unit)? = null, tick: () -> NodeStatus, end: (() -> Unit)? = null) {
        children.add(ActionNode(name, blackboard, start, tick, end))
    }

    fun action(name: String, start: Runnable? = null, tick: Supplier<NodeStatus>, end: Runnable? = null) {
        children.add(ActionNode(name, blackboard, { start?.run() }, tick, { end?.run() }))
    }

    // Decorators wrap exactly one child. The block forms require the
    // block to build a single node (wrap several nodes in sequence()
    // first); the node forms take an already-built node.
    private fun singleChild(decoratorKind: String, decoratorName: String, nested: KotlinSelectorBuilder): BehaviorNode {
        require(nested.children.size == 1) {
            "$decoratorKind \"$decoratorName\" requires exactly one child node, " +
                "but the block built ${nested.children.size}"
        }
        return nested.children.single()
    }

    fun inverter(name: String, init: KotlinSelectorBuilder.() -> Unit) {
        val nested = KotlinSelectorBuilder(name, blackboard).apply(init)
        children.add(InverterNode(name, blackboard, singleChild("inverter", name, nested)))
    }

    fun inverter(name: String, init: Consumer<KotlinSelectorBuilder>) {
        val nested = KotlinSelectorBuilder(name, blackboard).apply { init.accept(this) }
        children.add(InverterNode(name, blackboard, singleChild("inverter", name, nested)))
    }

    fun inverter(name: String, child: BehaviorNode) {
        children.add(InverterNode(name, blackboard, child))
    }

    fun succeeder(name: String, init: KotlinSelectorBuilder.() -> Unit) {
        val nested = KotlinSelectorBuilder(name, blackboard).apply(init)
        children.add(SucceederNode(name, blackboard, singleChild("succeeder", name, nested)))
    }

    fun succeeder(name: String, init: Consumer<KotlinSelectorBuilder>) {
        val nested = KotlinSelectorBuilder(name, blackboard).apply { init.accept(this) }
        children.add(SucceederNode(name, blackboard, singleChild("succeeder", name, nested)))
    }

    fun succeeder(name: String, child: BehaviorNode) {
        children.add(SucceederNode(name, blackboard, child))
    }

    fun retry(name: String, maxAttempts: Int, init: KotlinSelectorBuilder.() -> Unit) {
        val nested = KotlinSelectorBuilder(name, blackboard).apply(init)
        children.add(RetryNode(name, blackboard, maxAttempts, singleChild("retry", name, nested)))
    }

    fun retry(name: String, maxAttempts: Int, init: Consumer<KotlinSelectorBuilder>) {
        val nested = KotlinSelectorBuilder(name, blackboard).apply { init.accept(this) }
        children.add(RetryNode(name, blackboard, maxAttempts, singleChild("retry", name, nested)))
    }

    fun retry(name: String, maxAttempts: Int, child: BehaviorNode) {
        children.add(RetryNode(name, blackboard, maxAttempts, child))
    }

    fun repeat(name: String, targetCount: Int, init: KotlinSelectorBuilder.() -> Unit) {
        val nested = KotlinSelectorBuilder(name, blackboard).apply(init)
        children.add(RepeatNode(name, blackboard, targetCount, singleChild("repeat", name, nested)))
    }

    fun repeat(name: String, targetCount: Int, init: Consumer<KotlinSelectorBuilder>) {
        val nested = KotlinSelectorBuilder(name, blackboard).apply { init.accept(this) }
        children.add(RepeatNode(name, blackboard, targetCount, singleChild("repeat", name, nested)))
    }

    fun repeat(name: String, targetCount: Int, child: BehaviorNode) {
        children.add(RepeatNode(name, blackboard, targetCount, child))
    }

    // hardcodes reactive selector as the child of the timeout node, so that if the timeout expires, the entire subtree is invalidated
    fun waitMs(name: String, durationMs: Long, init: KotlinSelectorBuilder.() -> Unit) {
        children.add(
            TimeoutNode(
                name,
                blackboard,
                durationMs,
                KotlinSelectorBuilder("$name-inner", blackboard).apply(init).build()
            )
        )
    }

    fun waitMs(name: String, durationMs: Long, init: Consumer<KotlinSelectorBuilder>) {
        children.add(
            TimeoutNode(
                name,
                blackboard,
                durationMs,
                KotlinSelectorBuilder("$name-inner", blackboard).apply { init.accept(this) }.build()
            )
        )
    }

    // Duration overloads: waitMs("intake", Duration.ofSeconds(2)) { ... }
    fun waitMs(name: String, duration: Duration, init: KotlinSelectorBuilder.() -> Unit) {
        waitMs(name, duration.toMillis(), init)
    }

    fun waitMs(name: String, duration: Duration, init: Consumer<KotlinSelectorBuilder>) {
        waitMs(name, duration.toMillis(), init)
    }

    fun parallel(name: String, policy: ParallelPolicy, init: KotlinParallelBuilder.() -> Unit) {
        children.add(KotlinParallelBuilder(name, blackboard, policy).apply(init).build())
    }

    fun parallel(name: String, policy: ParallelPolicy, init: Consumer<KotlinParallelBuilder>) {
        children.add(KotlinParallelBuilder(name, blackboard, policy).apply { init.accept(this) }.build())
    }

    fun onCallbackTriggered(name: String, key: String, callback: (Any?) -> Unit, init: KotlinSequenceBuilder.() -> Unit) {
        val nested = KotlinSequenceBuilder(name, blackboard).apply(init).build()
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
        blackboard.registerCallback(key) { _, newValue -> callback(newValue) }
        children.add(nested)
    }

    fun onCallbackTriggered(name: String, key: String, callback: Consumer<Any?>, init: Consumer<KotlinSequenceBuilder>) {
        val nested = KotlinSequenceBuilder(name, blackboard).apply { init.accept(this) }.build()
        blackboard.registerCallback(key) { _, newValue -> callback.accept(newValue) }
        children.add(nested)
    }

}

class KotlinSequenceBuilder(private val name: String, blackboard: Blackboard) : NodeGroupBuilder(blackboard) {
    fun build() = SequenceNode(name, blackboard, children.toList())
}

class KotlinSelectorBuilder(private val name: String, blackboard: Blackboard) : NodeGroupBuilder(blackboard) {
    fun build() = ReactiveSelectorNode(name, blackboard, children.toList())
}

class KotlinParallelBuilder(private val name: String, blackboard: Blackboard, private val policy: ParallelPolicy) : NodeGroupBuilder(blackboard) {
    fun build() = ParallelNode(name, blackboard, policy, children.toList())
}

// Global Clean Entry Point
class KotlinBuilder {companion object {
    @JvmStatic
    fun rootTree(blackboard: Blackboard, init: KotlinSelectorBuilder.() -> Unit): ReactiveSelectorNode {
        return KotlinSelectorBuilder("Root", blackboard).apply(init).build()
    }

    @JvmStatic
    fun rootTree(blackboard: Blackboard, init: Consumer<KotlinSelectorBuilder>): ReactiveSelectorNode {
        return KotlinSelectorBuilder("Root", blackboard).apply { init.accept(this) }.build()
    }
}}
