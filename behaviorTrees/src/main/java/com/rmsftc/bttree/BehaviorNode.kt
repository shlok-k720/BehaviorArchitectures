package com.rmsftc.bttree

import com.rmsftc.bttree.blackboard.Blackboard
import com.rmsftc.bttree.logger.Logger

abstract class BehaviorNode(val name: String, protected val blackboard: Blackboard) {
    var status: NodeStatus? = null
        private set

    // Whether this node is currently mid-execution (onStart has run and
    // onEnd has not yet run for the current activation). This, not
    // `status == null`, decides whether onStart() runs: a node that
    // finished with SUCCESS/FAILURE must start fresh the next time it
    // is ticked, so composites that reset state in onStart() (Sequence,
    // Timeout, ReactiveSelector) actually reset on reuse.
    private var started = false

    fun tick(): NodeStatus {
        if (!started) {
            onStart()
            started = true
            status = NodeStatus.RUNNING
            logLifecycleEvent(Logger.NODE_STARTED)
        }
        status = onTick()
        if (status != NodeStatus.RUNNING) {
            onEnd()
            started = false
        }
        logLifecycleEvent(Logger.NODE_TICKED)
        if (status != NodeStatus.RUNNING) {
            logLifecycleEvent(Logger.NODE_FINISHED)
        }
        return status!!
    }

    protected open fun onStart() {}
    protected abstract fun onTick(): NodeStatus
    protected open fun onEnd() {}

    /**
     * The direct children of this node, or an empty list for leaf nodes.
     * Composites and decorators override this; it powers [forEachNode]
     * and [toTreeString].
     */
    open fun children(): List<BehaviorNode> = emptyList()

    /** Calls [action] on this node and then, depth-first, on every descendant. */
    fun forEachNode(action: (BehaviorNode) -> Unit) {
        action(this)
        children().forEach { it.forEachNode(action) }
    }

    /**
     * An indented dump of this subtree: one line per node with its name,
     * node type, and current status. Handy for telemetry or logcat while
     * debugging a tree.
     */
    fun toTreeString(): String {
        val sb = StringBuilder()
        appendNode(this, sb, 0)
        return sb.toString().trimEnd()
    }

    private fun appendNode(node: BehaviorNode, sb: StringBuilder, depth: Int) {
        repeat(depth) { sb.append("  ") }
        sb.append(node.name).append(" [").append(node.javaClass.simpleName).append("] ")
            .append(node.status?.name ?: "NONE").append('\n')
        node.children().forEach { appendNode(it, sb, depth + 1) }
    }

    /**
     * Abort this node if it is currently running. A no-op on a node
     * that is not started, so aborting an already-finished child does
     * not call onEnd() a second time or flip its finished result to
     * FAILURE.
     */
    fun invalidate() {
        if (!started) return
        status = NodeStatus.FAILURE
        onEnd()
        started = false
        logLifecycleEvent(Logger.NODE_INVALIDATED)
    }

    /**
     * Return this node to its never-ticked state. Aborts it first if
     * it is running. Composites override this to reset their children
     * as well. Note that reset() is optional between runs: ticking a
     * finished node already restarts it, because [started] is cleared
     * when it finishes.
     */
    open fun reset() {
        if (started) invalidate()
        status = null
        logLifecycleEvent(Logger.NODE_RESET)
    }

    private fun logLifecycleEvent(caption: String) {
        val type = javaClass.simpleName
        val nodeStatus = status?.name ?: "NONE"
        Logger.log(caption, "name=$name,type=$type,status=$nodeStatus")
    }

    protected fun log(data: String) {
        val type = javaClass.simpleName
        val nodeStatus = status?.name ?: "NONE"
        Logger.log("com.rmsftc.bttree: Manual Node Log", "name=$name,type=$type,status=$nodeStatus,data=$data")
    }
}
