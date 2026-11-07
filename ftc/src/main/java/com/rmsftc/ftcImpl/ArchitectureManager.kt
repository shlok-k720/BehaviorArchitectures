package com.rmsftc.ftcImpl

import com.rmsftc.bttree.BehaviorNode
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
import com.rmsftc.cmdlib.InterruptionBehavior
import com.rmsftc.cmdlib.ScheduleResult
import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.cmdlib.cmd.Commands
import com.rmsftc.cmdlib.cmd.ICommand
import com.rmsftc.cmdlib.cmd.groups.ParallelCommandGroup
import com.rmsftc.cmdlib.cmd.groups.ParallelDeadlineGroup
import com.rmsftc.cmdlib.cmd.groups.ParallelRaceGroup
import com.rmsftc.cmdlib.cmd.groups.SequentialCommandGroup
import com.rmsftc.cmdlib.scheduler.Scheduler
import com.rmsftc.fsm.Case
import com.rmsftc.fsm.CaseBuilder
import com.rmsftc.fsm.State
import com.rmsftc.fsm.StateMachine
import com.rmsftc.fsm.cases.InstantCase
import com.rmsftc.fsm.cases.PrintCase
import com.rmsftc.fsm.cases.RunCase
import com.rmsftc.fsm.cases.StartEndCase
import com.rmsftc.fsm.cases.WaitCase
import com.rmsftc.fsm.cases.WaitUntilCase
import com.rmsftc.util.blackboard.Blackboard
import com.rmsftc.util.logger.LogSystem
import com.rmsftc.util.logger.Logger
import org.firstinspires.ftc.robotcore.external.Telemetry
import java.util.function.BiConsumer
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * The one entry point for all three architectures in an OpMode: behavior
 * trees, state machines, and commands.
 *
 * - One shared [Blackboard]: trees, machines, and scheduled commands all
 *   read and write the same board, so a tree can set a value a case reads.
 * - One [Scheduler]: commands scheduled here get the shared board attached
 *   (see [Scheduler]), so they see the same data as the trees and cases.
 * - One loop call: [tickAll] ticks every tree, updates every machine, and
 *   executes the scheduler.
 *
 * The builders for trees, state machines, and commands live here too:
 * trees and state machines are grouped under [Trees] and [StateMachines],
 * commands under [CommandsBuilder] (they moved out of the behaviorTrees
 * and fsm modules, so OpMode code has one place to look):
 *
 * ```
 * val manager = ArchitectureManager()
 * val tree = manager.tree { action("score") { NodeStatus.SUCCESS } }
 * val machine = manager.stateMachine {
 *     case("intake") { onExecute { if (full) transitionTo("shoot") } }
 * }
 * manager.schedule(Commands.runOnce({ shooter.fire() }, "shooter"))
 * // in start(): manager.startAll()
 * // in the loop: manager.tickAll()
 * ```
 *
 * Trees start themselves on their first tick, but state machines do not:
 * call [startAll] once (in the OpMode's `start()`) before [tickAll] starts
 * updating them. Calling [startAll] again restarts every machine from its
 * first case.
 *
 * To convert one architecture into another (a machine as a command, a
 * command as a case, ...), see [ArchitectureConverter].
 */
class ArchitectureManager @JvmOverloads constructor(
    private val clock: Supplier<Long> = Supplier { System.nanoTime() },
    private val isOpModeActive: BooleanSupplier = BooleanSupplier { true }
) {
    private var blackboard = Blackboard()
    private var scheduler = Scheduler(clock, isOpModeActive, blackboard)
    private val trees = mutableListOf<ReactiveSelectorNode>()
    private val machines = mutableListOf<StateMachine>()

    /** The shared blackboard: trees, machines, and scheduled commands all use it. */
    fun blackboard(): Blackboard = blackboard

    /** The scheduler this manager runs commands on (it owns the shared blackboard). */
    fun scheduler(): Scheduler = scheduler

    /**
     * Aborts everything managed here, clears the trees and machines, and
     * starts fresh with a new blackboard and scheduler. Trees, machines,
     * and commands created before this call reference the previous
     * blackboard and should not be ticked afterward.
     */
    fun resetAndInitialize() {
        invalidateAll()
        trees.clear()
        machines.clear()
        blackboard = Blackboard()
        scheduler = Scheduler(clock, isOpModeActive, blackboard)
    }

    /** The trees built through this manager, in creation order. */
    fun trees(): List<ReactiveSelectorNode> = trees.toList()

    /** The state machines built through this manager, in creation order. */
    fun machines(): List<StateMachine> = machines.toList()

    /** Builds a tree from the Java builder and manages it. */
    @JvmName("jTree")
    fun tree(builderConsumer: Consumer<Trees.JavaBuilder>): ReactiveSelectorNode {
        return Trees.JavaBuilder.rootTree(
            blackboard,
            builderConsumer
        ).also(trees::add)
    }

    /** Builds a tree from the Kotlin builder (Java-facing overload) and manages it. */
    @JvmName("kTree")
    fun tree(builderConsumer: Consumer<Trees.KotlinSelectorBuilder>): ReactiveSelectorNode {
        return Trees.KotlinBuilder.rootTree(
            blackboard,
            builderConsumer
        ).also(trees::add)
    }

    /** Builds a tree from the Kotlin DSL and manages it. */
    @JvmName("kDSLTree")
    fun tree(builder: Trees.KotlinSelectorBuilder.() -> Unit): ReactiveSelectorNode {
        return Trees.KotlinBuilder.rootTree(
            blackboard,
            builder
        ).also(trees::add)
    }

    /**
     * Builds a state machine from the Java builder and manages it. The
     * machine uses this manager's shared blackboard, even if the block
     * sets its own (the manager's board is applied after the block runs).
     */
    @JvmName("jStateMachine")
    fun stateMachine(block: Consumer<StateMachines.JavaBuilder>): StateMachine {
        return StateMachines.JavaBuilder.stateMachine(
            Consumer { b ->
                block.accept(b)
                b.blackboard(blackboard)
            }
        ).also(machines::add)
    }

    /**
     * Builds a state machine from the Kotlin builder (Java-facing overload)
     * and manages it. The machine uses this manager's shared blackboard,
     * even if the block sets its own.
     */
    @JvmName("kStateMachine")
    fun stateMachine(block: Consumer<StateMachines.KotlinBuilder>): StateMachine {
        val builder = StateMachines.KotlinBuilder()
        block.accept(builder)
        builder.blackboard(blackboard)
        return builder.build().also(machines::add)
    }

    /**
     * Builds a state machine from the Kotlin DSL and manages it. The
     * machine uses this manager's shared blackboard, even if the block
     * sets its own.
     */
    @JvmName("kDSLStateMachine")
    fun stateMachine(init: StateMachines.KotlinBuilder.() -> Unit): StateMachine {
        val builder = StateMachines.KotlinBuilder()
        builder.init()
        builder.blackboard(blackboard)
        return builder.build().also(machines::add)
    }

    /** Schedules a command on this manager's scheduler (it gets the shared blackboard). */
    fun schedule(command: ICommand): ScheduleResult = scheduler.schedule(command)

    /**
     * Builds a command with [CommandsBuilder] (Kotlin DSL) and schedules it
     * on this manager's scheduler (it gets the shared blackboard). Returns
     * the built command.
     *
     * ```
     * val auto = manager.commands {
     *     runOnce({ intake.start() }, "intake")
     *     waitMs(500)
     *     runOnce({ shooter.fire() }, "shooter")
     * }
     * ```
     *
     * Scheduling happens immediately: if this manager was constructed with
     * a custom `isOpModeActive` that is currently false, the scheduler
     * rejects the command while disabled — check
     * `scheduler().isScheduled(cmd)` in that case, or build with
     * [CommandsBuilder] directly and [schedule] it later.
     */
    fun commands(init: CommandsBuilder.() -> Unit): Command {
        val cmd = CommandsBuilder().apply(init).build()
        scheduler.schedule(cmd)
        return cmd
    }

    /** Builds a command with [CommandsBuilder] (Java-facing overload) and schedules it. See [commands]. */
    fun commands(block: Consumer<CommandsBuilder>): Command {
        val builder = CommandsBuilder()
        block.accept(builder)
        val cmd = builder.build()
        scheduler.schedule(cmd)
        return cmd
    }

    /** Cancels a command scheduled through this manager. */
    fun cancel(command: ICommand) {
        scheduler.cancel(command)
    }

    /** Cancels every command scheduled through this manager. */
    fun cancelAllCommands() {
        scheduler.cancelAll()
    }

    /**
     * Starts every managed state machine from its first case. Call once,
     * in the OpMode's `start()` — machines do not start themselves the way
     * trees do on their first tick. Calling it again restarts every
     * machine from its first case (including finished ones).
     */
    fun startAll() {
        machines.forEach { it.start() }
    }

    /**
     * One loop tick for everything managed here: ticks every tree, updates
     * every machine, then executes the scheduler. Call once per OpMode loop.
     */
    fun tickAll() {
        trees.forEach { it.tick() }
        machines.forEach { it.update() }
        scheduler.execute()
    }

    /** Ticks every managed tree once and returns each result in creation order. */
    fun tickTrees(): List<NodeStatus> = trees.map { it.tick() }

    /** Updates every managed state machine once. */
    fun updateMachines() {
        machines.forEach { it.update() }
    }

    /** Executes this manager's scheduler once. */
    fun executeScheduler() {
        scheduler.execute()
    }

    /** Aborts every managed tree and machine and cancels every scheduled command. */
    fun invalidateAll() {
        trees.forEach { it.invalidate() }
        machines.forEach { it.stop() }
        scheduler.cancelAll()
    }

    /** Returns every managed tree and machine to its never-started state, and cancels every command. */
    fun resetAll() {
        trees.forEach { it.reset() }
        machines.forEach { it.reset() }
        scheduler.reset()
    }

    /**
     * Logs to telemetry: attaches a [LogSystem] that writes every log line
     * (tree, machine, and command events) to the OpMode telemetry. Keep the
     * returned instance and call `Logger.removeLogger(it)` to detach it —
     * calling this twice attaches two loggers and doubles the output.
     */
    fun telemetryLogger(telemetry: Telemetry): LogSystem {
        return LogSystem { caption, data, timeStampMS ->
            telemetry.addData("$caption ($timeStampMS)", data)
        }.also(Logger::attachLogger)
    }
}

/**
 * The behavior-tree builders, moved here from the behaviorTrees module so
 * OpMode code has one place to look. Build a tree with
 * [Trees.JavaBuilder] from Java or [Trees.KotlinBuilder] from Kotlin, or
 * through [ArchitectureManager.tree].
 */
object Trees {

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
    class KotlinBuilder {
        companion object {
            @JvmStatic
            fun rootTree(blackboard: Blackboard, init: KotlinSelectorBuilder.() -> Unit): ReactiveSelectorNode {
                return KotlinSelectorBuilder("Root", blackboard).apply(init).build()
            }

            @JvmStatic
            fun rootTree(blackboard: Blackboard, init: Consumer<KotlinSelectorBuilder>): ReactiveSelectorNode {
                return KotlinSelectorBuilder("Root", blackboard).apply { init.accept(this) }.build()
            }
        }
    }

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
}

/**
 * The state-machine builders, moved here from the fsm module so OpMode code
 * has one place to look. Build a machine with [StateMachines.JavaBuilder]
 * from Java or [StateMachines.KotlinBuilder] (or the top-level
 * [stateMachine] DSL) from Kotlin, or through
 * [ArchitectureManager.stateMachine].
 */
object StateMachines {

    @DslMarker
    annotation class FSMMarker

    /**
     * One case inside the [stateMachine] DSL. The [onStart], [onExecute] and
     * [onTransition] blocks run with this as their receiver, so transitions
     * are called from inside them:
     *
     * ```
     * val machine = stateMachine {
     *     case("intake") {
     *         onStart { intake.start() }
     *         onExecute { if (intake.isFull) transitionTo("shoot") }
     *     }
     *     case("shoot") {
     *         onStart { shooter.fire() }
     *         onExecute { if (shooter.done()) transitionToNext() }
     *     }
     * }
     * ```
     *
     * Call [transitionTo] and [transitionToNext] only from inside those
     * blocks (they need the case to be part of a machine, which happens when
     * the machine is built).
     */
    @FSMMarker
    class CaseDsl internal constructor(internal val builder: CaseBuilder) {
        /** Runs once when the case becomes active. */
        fun onStart(action: CaseDsl.() -> Unit) {
            builder.setOnStart(Runnable { action.invoke(this) })
        }

        /** Runs once when the case becomes active (Java-facing overload). */
        fun onStart(action: Consumer<CaseDsl>) {
            builder.setOnStart(Runnable { action.accept(this) })
        }

        /** Runs every tick while the case is active. */
        fun onExecute(action: CaseDsl.() -> Unit) {
            builder.setOnExecute(Runnable { action.invoke(this) })
        }

        /** Runs every tick while the case is active (Java-facing overload). */
        fun onExecute(action: Consumer<CaseDsl>) {
            builder.setOnExecute(Runnable { action.accept(this) })
        }

        /** Runs on this case just before the machine leaves it; receives the case being transitioned into (null on stop/finish). */
        fun onTransition(action: CaseDsl.(Case?) -> Unit) {
            builder.setOnTransition(Consumer { other -> action.invoke(this, other) })
        }

        /** Runs on this case just before the machine leaves it (Java-facing overload). */
        fun onTransition(action: BiConsumer<CaseDsl, Case?>) {
            builder.setOnTransition(Consumer { other -> action.accept(this, other) })
        }

        /** Jumps to the case with this state, applied at the end of the current tick. */
        fun transitionTo(state: State) {
            builder.transition(state)
        }

        /** Jumps to the case with this state name, applied at the end of the current tick. */
        fun transitionTo(name: String) {
            builder.transition(State.of(name))
        }

        /** Jumps to the case with this enum state, applied at the end of the current tick. */
        fun <E : Enum<E>> transitionTo(state: E) {
            builder.transition(State.of(state))
        }

        /** Advances to the next case in the machine's list, applied at the end of the current tick. */
        fun transitionToNext() {
            builder.transitionToNext()
        }
    }

    /** Builds a [StateMachine] with the [stateMachine] DSL. */
    @FSMMarker
    class KotlinBuilder {
        companion object {
            /** Java entry point: builds a machine from a Consumer configuring the builder. */
            @JvmStatic
            fun stateMachine(block: Consumer<KotlinBuilder>): StateMachine =
                KotlinBuilder().apply { block.accept(this) }.build()
        }

        private val cases = ArrayList<Case>()
        private var initialState: State? = null
        private var blackboard: Blackboard = Blackboard()
        private var clock: Supplier<Long> = Supplier { System.nanoTime() }

        /** Declares a case for this state. Returns the built case. */
        fun case(state: State, init: CaseDsl.() -> Unit): Case {
            val builder = CaseBuilder(state)
            CaseDsl(builder).init()
            cases.add(builder)
            return builder
        }

        /** Declares a case for this state (Java-facing overload). Returns the built case. */
        fun case(state: State, init: Consumer<CaseDsl>): Case {
            val builder = CaseBuilder(state)
            init.accept(CaseDsl(builder))
            cases.add(builder)
            return builder
        }

        /** Declares a case for this state name. Returns the built case. */
        fun case(name: String, init: CaseDsl.() -> Unit): Case =
            case(State.of(name), init)

        /** Declares a case for this state name (Java-facing overload). Returns the built case. */
        fun case(name: String, init: Consumer<CaseDsl>): Case =
            case(State.of(name), init)

        /** Declares a case for this enum state. Returns the built case. */
        fun <E : Enum<E>> case(state: E, init: CaseDsl.() -> Unit): Case =
            case(State.of(state), init)

        /** Declares a case for this enum state (Java-facing overload). Returns the built case. */
        fun <E : Enum<E>> case(state: E, init: Consumer<CaseDsl>): Case =
            case(State.of(state), init)

        /**
         * Declares a case for this state. The Java-facing form of [case]:
         * `case` is a keyword in Java, so Java code cannot call it.
         * Returns the built case.
         */
        fun addCase(state: State, init: Consumer<CaseDsl>): Case =
            case(state, init)

        /** Declares a case for this state name (Java-facing form of [case]). Returns the built case. */
        fun addCase(name: String, init: Consumer<CaseDsl>): Case =
            case(name, init)

        /** Declares a case for this enum state (Java-facing form of [case]). Returns the built case. */
        fun <E : Enum<E>> addCase(state: E, init: Consumer<CaseDsl>): Case =
            case(state, init)

        /** Adds a prebuilt case (built with Java or by subclassing [Case]). */
        fun add(case: Case) {
            cases.add(case)
        }

        /** The state the machine starts from (defaults to the first case declared). */
        fun initial(state: State) {
            initialState = state
        }

        /** The state the machine starts from, by name. */
        fun initial(name: String) {
            initialState = State.of(name)
        }

        /** The state the machine starts from, by enum value. */
        fun <E : Enum<E>> initial(state: E) {
            initialState = State.of(state)
        }

        /** The shared [Blackboard] cases read and write (defaults to a fresh one). */
        fun blackboard(blackboard: Blackboard) {
            this.blackboard = blackboard
        }

        /** The nanosecond clock (defaults to `System.nanoTime()`; inject a fake one for tests). */
        fun clock(supplier: Supplier<Long>) {
            clock = supplier
        }

        internal fun build(): StateMachine {
            val ordered = ArrayList(cases)
            initialState?.let { state ->
                val initial = ordered.find { it.state == state }
                    ?: throw IllegalArgumentException("KotlinBuilder: no case with state: ${state.name()}")
                ordered.remove(initial)
                ordered.add(0, initial)
            }
            return StateMachine(blackboard, clock, *ordered.toTypedArray())
        }
    }

    /**
     * Builds a [StateMachine] in Java, in the shape of the behavior-tree
     * [Trees.JavaBuilder]: one static entry point that takes a [Consumer]
     * configuring the builder.
     *
     * ```
     * StateMachine machine = JavaBuilder.stateMachine(b -> b
     *         .addCase(intake)
     *         .waitMs(State.of("wait"), 500)
     *         .initial(State.of("intake")));
     * machine.start();
     * // in the loop: machine.update();
     * ```
     */
    class JavaBuilder private constructor() {
        private val cases = ArrayList<Case>()
        private var initial: State? = null
        private var blackboard: Blackboard = Blackboard()
        private var clock: Supplier<Long> = Supplier { System.nanoTime() }

        companion object {
            /** Single entry point: configures a builder and returns the built machine. */
            @JvmStatic
            fun stateMachine(block: Consumer<JavaBuilder>): StateMachine {
                val builder = JavaBuilder()
                block.accept(builder)
                return builder.build()
            }
        }

        /** Adds a case to the end of the machine's list. */
        fun addCase(c: Case): JavaBuilder {
            cases.add(c)
            return this
        }

        /** The state the machine starts from (defaults to the first case added). */
        fun initial(state: State): JavaBuilder {
            initial = state
            return this
        }

        /** The shared [Blackboard] cases read and write (defaults to a fresh one). */
        fun blackboard(blackboard: Blackboard): JavaBuilder {
            this.blackboard = blackboard
            return this
        }

        /** The nanosecond clock (defaults to `System.nanoTime()`; inject a fake one for tests). */
        fun clock(clock: Supplier<Long>): JavaBuilder {
            this.clock = clock
            return this
        }

        /** Adds a case built from lambdas (see [CaseBuilder]). */
        fun caseBuilder(state: State, block: Consumer<CaseBuilder>): JavaBuilder {
            val builder = CaseBuilder(state)
            block.accept(builder)
            cases.add(builder)
            return this
        }

        /** Adds a [WaitCase]: waits [millis], then advances to the next case. */
        fun waitMs(state: State, millis: Long): JavaBuilder {
            cases.add(WaitCase(state, millis))
            return this
        }

        /** Adds a [WaitUntilCase]: waits for [condition], then advances. */
        fun waitUntil(state: State, condition: Supplier<Boolean>): JavaBuilder {
            cases.add(WaitUntilCase(state, condition))
            return this
        }

        /** Adds a [RunCase]: runs [action] every tick, forever. */
        fun run(state: State, action: Runnable): JavaBuilder {
            cases.add(RunCase(state, action))
            return this
        }

        /** Adds an [InstantCase]: runs [action] once, then advances. */
        fun instant(state: State, action: Runnable): JavaBuilder {
            cases.add(InstantCase(state, action))
            return this
        }

        /** Adds a [StartEndCase]: start action on entry, end action on exit. */
        fun startEnd(state: State, onStart: Runnable, onEnd: Runnable): JavaBuilder {
            cases.add(StartEndCase(state, onStart, onEnd))
            return this
        }

        /** Adds a [PrintCase]: prints [message] once, then advances. */
        fun print(state: State, message: String): JavaBuilder {
            cases.add(PrintCase(state, message))
            return this
        }

        private fun build(): StateMachine {
            val ordered = ArrayList(cases)
            initial?.let { state ->
                val first = ordered.find { it.state == state }
                    ?: throw IllegalArgumentException(
                        "JavaBuilder: no case with state: " + state.name()
                    )
                ordered.remove(first)
                ordered.add(0, first)
            }
            return StateMachine(blackboard, clock, *ordered.toTypedArray())
        }
    }
}

/** Builds a [StateMachine] with the Kotlin DSL. See [StateMachines.CaseDsl]. */
fun stateMachine(init: StateMachines.KotlinBuilder.() -> Unit): StateMachine =
    StateMachines.KotlinBuilder().apply(init).build()

/**
 * Builds a command from steps, the command equivalent of the tree and
 * state-machine builders in [Trees] and [StateMachines]. Build it through
 * [ArchitectureManager.commands] (which also schedules the result) or
 * directly when you want to schedule it later yourself:
 *
 * ```
 * val cmd = CommandsBuilder()
 *     .runOnce({ intake.start() }, "intake")
 *     .waitMs(500)
 *     .run({ shooter.spin() }, "shooter")
 *     .build()
 * manager.schedule(cmd)
 * ```
 *
 * Every step method returns this builder, so calls chain. The step methods
 * take the same arguments as the [Commands] factories of the same name, so
 * `runOnce(action, tokens)` here is `Commands.runOnce(action, tokens)` as a
 * step. Nested groups ([sequence], [parallel], [race], [deadline]) take a
 * block that builds their steps with another [CommandsBuilder].
 *
 * The built command runs its steps in order ([SequentialCommandGroup]).
 * For the other shapes — a command that repeats, times out, or runs until a
 * condition — use the fluent methods on the built command itself
 * ([Command.repeatedly], [Command.withTimeout], [Command.until]).
 */
class CommandsBuilder {
    private val steps = mutableListOf<ICommand>()
    private var priority: Int = 0
    private var interruptionBehavior: InterruptionBehavior = InterruptionBehavior.CANCEL_SELF

    /** Runs [action] once, then finishes. */
    fun runOnce(action: Runnable, vararg tokens: Any): CommandsBuilder {
        steps.add(Commands.runOnce(action, *tokens))
        return this
    }

    /** Runs [action] on every tick, forever. */
    fun run(action: Runnable, vararg tokens: Any): CommandsBuilder {
        steps.add(Commands.run(action, *tokens))
        return this
    }

    /** Runs [start] once on start and [end] once on end, never finishing alone. */
    fun startEnd(start: Runnable, end: Runnable, vararg tokens: Any): CommandsBuilder {
        steps.add(Commands.startEnd(start, end, *tokens))
        return this
    }

    /** Runs [run] on every tick and [end] once on end, never finishing alone. */
    fun runEnd(run: Runnable, end: Runnable, vararg tokens: Any): CommandsBuilder {
        steps.add(Commands.runEnd(run, end, *tokens))
        return this
    }

    /** Runs [start] once on start, then [run] on every tick, never finishing alone. */
    fun startRun(start: Runnable, run: Runnable, vararg tokens: Any): CommandsBuilder {
        steps.add(Commands.startRun(start, run, *tokens))
        return this
    }

    /** Prints [message] once, then finishes. */
    fun print(message: String): CommandsBuilder {
        steps.add(Commands.print(message))
        return this
    }

    /** Finishes after [millis] milliseconds, then moves to the next step. */
    fun waitMs(millis: Long): CommandsBuilder {
        steps.add(Commands.waitMs(millis))
        return this
    }

    /** Finishes as soon as [condition] becomes true, then moves to the next step. */
    fun waitUntil(condition: BooleanSupplier): CommandsBuilder {
        steps.add(Commands.waitUntil(condition))
        return this
    }

    /** Adds an already-built command as a step. */
    fun command(cmd: ICommand): CommandsBuilder {
        steps.add(cmd)
        return this
    }

    /** Runs the block's steps in order, as one step. */
    fun sequence(init: CommandsBuilder.() -> Unit): CommandsBuilder {
        steps.add(CommandsBuilder().apply(init).build())
        return this
    }

    /** Runs the block's steps in order, as one step (Java-facing overload). */
    fun sequence(block: Consumer<CommandsBuilder>): CommandsBuilder {
        val nested = CommandsBuilder()
        block.accept(nested)
        steps.add(nested.build())
        return this
    }

    /** Runs the block's steps at the same time, as one step; finishes when all finish. */
    fun parallel(init: CommandsBuilder.() -> Unit): CommandsBuilder {
        val nested = CommandsBuilder().apply(init)
        steps.add(ParallelCommandGroup(*nested.steps.toTypedArray()))
        return this
    }

    /** Runs the block's steps at the same time, as one step (Java-facing overload). */
    fun parallel(block: Consumer<CommandsBuilder>): CommandsBuilder {
        val nested = CommandsBuilder()
        block.accept(nested)
        steps.add(ParallelCommandGroup(*nested.steps.toTypedArray()))
        return this
    }

    /** Runs the block's steps at the same time, as one step; finishes when the first finishes. */
    fun race(init: CommandsBuilder.() -> Unit): CommandsBuilder {
        val nested = CommandsBuilder().apply(init)
        steps.add(ParallelRaceGroup(*nested.steps.toTypedArray()))
        return this
    }

    /** Runs the block's steps at the same time, as one step (Java-facing overload). */
    fun race(block: Consumer<CommandsBuilder>): CommandsBuilder {
        val nested = CommandsBuilder()
        block.accept(nested)
        steps.add(ParallelRaceGroup(*nested.steps.toTypedArray()))
        return this
    }

    /**
     * Runs [deadline] alongside the block's steps, as one step; finishes
     * when [deadline] finishes (the other steps are interrupted then).
     */
    fun deadline(deadline: ICommand, init: CommandsBuilder.() -> Unit): CommandsBuilder {
        val nested = CommandsBuilder().apply(init)
        steps.add(ParallelDeadlineGroup(deadline, *nested.steps.toTypedArray()))
        return this
    }

    /** Runs [deadline] alongside the block's steps, as one step (Java-facing overload). */
    fun deadline(deadline: ICommand, block: Consumer<CommandsBuilder>): CommandsBuilder {
        val nested = CommandsBuilder()
        block.accept(nested)
        steps.add(ParallelDeadlineGroup(deadline, *nested.steps.toTypedArray()))
        return this
    }

    /** The priority of the built command (see [Command.priority]). Applied by [build]. */
    fun priority(priority: Int): CommandsBuilder {
        this.priority = priority
        return this
    }

    /**
     * What happens when a higher-priority command needs this one's
     * requirements (see [Command.interruptionBehavior]). Applied by [build].
     */
    fun interruptBehavior(behavior: InterruptionBehavior): CommandsBuilder {
        this.interruptionBehavior = behavior
        return this
    }

    /**
     * Builds the command: no steps becomes [Commands.none]; one step is
     * returned as-is (wrapped in a one-command group only if it is a bare
     * [ICommand], not a [Command]); several steps become a
     * [SequentialCommandGroup]. The [priority] and [interruptBehavior] set
     * on this builder are applied to the result.
     */
    fun build(): Command {
        val built: Command = when (steps.size) {
            0 -> Commands.none()
            1 -> {
                val only = steps.single()
                if (only is Command) only else SequentialCommandGroup(only)
            }
            else -> SequentialCommandGroup(*steps.toTypedArray())
        }
        built.priority = priority
        built.interruptionBehavior = interruptionBehavior
        return built
    }
}
