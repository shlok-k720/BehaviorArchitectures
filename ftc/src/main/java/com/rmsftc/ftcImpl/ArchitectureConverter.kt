package com.rmsftc.ftcImpl

import com.rmsftc.bttree.BehaviorNode
import com.rmsftc.bttree.NodeStatus
import com.rmsftc.bttree.nodes.ActionNode
import com.rmsftc.cmdlib.EndCondition
import com.rmsftc.cmdlib.Status
import com.rmsftc.cmdlib.cmd.Command
import com.rmsftc.cmdlib.cmd.ICommand
import com.rmsftc.fsm.Case
import com.rmsftc.fsm.State
import com.rmsftc.fsm.StateMachine
import com.rmsftc.util.blackboard.Blackboard
import java.util.function.Supplier

/**
 * Runs a [StateMachine] as a command: [start] starts the machine, each
 * [loop] ticks it once, and the command finishes when the machine runs out
 * of cases. Interrupting the command stops the machine.
 *
 * Do not also tick this machine somewhere else (an [ArchitectureManager],
 * the OpMode loop) while the command is running — the machine would
 * advance twice per loop.
 */
class StateMachineCommand(val machine: StateMachine, name: String = "StateMachineCommand") : Command() {
    init {
        this.name = name
    }

    override fun start() {
        machine.start()
    }

    override fun loop() {
        machine.update()
    }

    override fun status(): Status = Status.of(!machine.isFinished())

    override fun end(endCondition: EndCondition) {
        if (endCondition == EndCondition.INTERRUPTED) {
            machine.stop()
        }
    }
}

/**
 * Runs a behavior tree (its [root]) as a command: each [loop] ticks the
 * tree once, and the command finishes when the tick returns anything other
 * than RUNNING. A tree that returns FAILURE still finishes the command —
 * commands have no failure, so FAILURE maps to "done" just like SUCCESS.
 * Interrupting the command invalidates the tree.
 *
 * Do not also tick this tree somewhere else while the command is running.
 */
class TreeCommand(val root: BehaviorNode, name: String = "TreeCommand") : Command() {
    private var lastStatus: NodeStatus = NodeStatus.RUNNING

    init {
        this.name = name
    }

    override fun start() {
        // Nothing to do: the first tick() starts the tree.
    }

    override fun loop() {
        lastStatus = root.tick()
    }

    override fun status(): Status = Status.of(lastStatus == NodeStatus.RUNNING)

    override fun end(endCondition: EndCondition) {
        if (endCondition == EndCondition.INTERRUPTED) {
            root.invalidate()
        }
    }
}

/**
 * Runs a [command] as a [Case]: each [onExecute] ticks the command once,
 * and the case advances to the next case when the command finishes. Leaving
 * the case early (a jump, or the machine stopping) interrupts the command.
 *
 * The command runs inline, not through a scheduler: its requirements are
 * not checked against anything else, and it keeps its own blackboard (the
 * scheduler is what attaches the shared board). Schedule the command
 * instead if it must share data or conflict with other commands.
 */
class CommandCase(state: State, val command: ICommand) : Case(state) {
    override fun onExecute() {
        val status = command.execute()
        if (!status.isRunning()) {
            transitionToNext()
        }
    }

    override fun onTransition(other: Case?) {
        // Safe after a natural finish too: Command.invalidate() is a
        // no-op once end() has run, and ICommand requires it to be safe
        // on a command that never started.
        command.invalidate()
    }
}

/**
 * Runs a behavior tree (its [root]) as a [Case]: each [onExecute] ticks
 * the tree once, and the case advances when the tick returns anything
 * other than RUNNING. Leaving the case early invalidates the tree.
 * (A tree that returns FAILURE still advances the case — nodes have no
 * "command failed" equivalent on this side either.)
 */
class TreeCase(state: State, val root: BehaviorNode) : Case(state) {
    override fun onExecute() {
        val status = root.tick()
        if (status != NodeStatus.RUNNING) {
            transitionToNext()
        }
    }

    override fun onTransition(other: Case?) {
        root.invalidate()
    }
}

/**
 * Runs a [command] as a behavior-tree node: each tick executes the command
 * once. A finished command reports SUCCESS — commands have no failure, so
 * there is no FAILURE mapping. Invalidating the node interrupts the
 * command. (Invalidating after a natural finish is a no-op.)
 *
 * Like [CommandCase], the command runs inline, not through a scheduler:
 * its requirements are not checked, and it keeps its own blackboard.
 */
class CommandNode(
    val command: ICommand,
    blackboard: Blackboard,
    name: String = command.name
) : ActionNode(
    name,
    blackboard,
    start = null,
    tick = Supplier {
        if (command.execute().isRunning()) NodeStatus.RUNNING else NodeStatus.SUCCESS
    },
    end = { command.invalidate() }
)

/**
 * Runs a [machine] as a behavior-tree node: the first tick starts the
 * machine, each tick updates it once, and the node reports SUCCESS when
 * the machine runs out of cases. Ending the node (including invalidation)
 * stops the machine. Ticking the node again after it finished restarts
 * the machine from its first case.
 *
 * Do not also update this machine somewhere else while the node is running.
 */
class StateMachineNode(
    val machine: StateMachine,
    blackboard: Blackboard,
    name: String = "StateMachineNode"
) : ActionNode(
    name,
    blackboard,
    start = { machine.start() },
    tick = Supplier {
        machine.update()
        if (machine.isFinished()) NodeStatus.SUCCESS else NodeStatus.RUNNING
    },
    end = { machine.stop() }
)

/**
 * Switches between architectures: a state machine, a case, a command, and
 * a behavior tree can each be wrapped to run as one of the others.
 *
 * | From | To command | To case | To node |
 * |---|---|---|---|
 * | StateMachine | [toCommand] | — (wrap its cases instead) | [toNode] |
 * | Case | [toCommand] | — | [toNode] |
 * | Command | — | [toCase] | [toNode] |
 * | BehaviorNode | [toCommand] | [toCase] | — (it already is one) |
 *
 * Two rules apply to every conversion:
 * - A converted thing runs inline where it lands: a command-as-case or
 *   command-as-node is ticked by the machine/tree directly, not by a
 *   scheduler, so its requirements are not checked and it keeps its own
 *   blackboard. Only a command scheduled through a scheduler shares the
 *   manager's board.
 * - A converted thing must be driven in exactly one place: do not tick a
 *   machine (or tree) both through its wrapper and through an
 *   [ArchitectureManager] or the OpMode loop.
 * - A single [Case] converts through a one-case machine, so it can only
 *   finish by advancing ([Case.transitionToNext]); jumping to a state
 *   that is not in that one-case machine throws.
 * - Commands have no FAILURE: a finished command-as-node reports SUCCESS,
 *   and a FAILED tree-as-command still counts as finished.
 */
object ArchitectureConverter {

    /** Wraps a state machine as a command. */
    @JvmStatic
    fun toCommand(machine: StateMachine): StateMachineCommand =
        StateMachineCommand(machine)

    /** Wraps a state machine as a command, with a custom command name. */
    @JvmStatic
    fun toCommand(machine: StateMachine, name: String): StateMachineCommand =
        StateMachineCommand(machine, name)

    /** Wraps a single case as a command (through a one-case machine; see the class docs). */
    @JvmStatic
    fun toCommand(case: Case): StateMachineCommand =
        StateMachineCommand(StateMachine(case))

    /** Wraps a single case as a command, with a custom command name. */
    @JvmStatic
    fun toCommand(case: Case, name: String): StateMachineCommand =
        StateMachineCommand(StateMachine(case), name)

    /** Wraps a behavior tree (its root node) as a command. */
    @JvmStatic
    fun toCommand(root: BehaviorNode): TreeCommand =
        TreeCommand(root)

    /** Wraps a behavior tree (its root node) as a command, with a custom command name. */
    @JvmStatic
    fun toCommand(root: BehaviorNode, name: String): TreeCommand =
        TreeCommand(root, name)

    /** Wraps a command as a case with this state. */
    @JvmStatic
    fun toCase(command: ICommand, state: State): CommandCase =
        CommandCase(state, command)

    /** Wraps a behavior tree (its root node) as a case with this state. */
    @JvmStatic
    fun toCase(root: BehaviorNode, state: State): TreeCase =
        TreeCase(state, root)

    /** Wraps a command as a behavior-tree node. */
    @JvmStatic
    fun toNode(command: ICommand, blackboard: Blackboard): CommandNode =
        CommandNode(command, blackboard)

    /** Wraps a command as a behavior-tree node, with a custom node name. */
    @JvmStatic
    fun toNode(command: ICommand, blackboard: Blackboard, name: String): CommandNode =
        CommandNode(command, blackboard, name)

    /** Wraps a state machine as a behavior-tree node. */
    @JvmStatic
    fun toNode(machine: StateMachine, blackboard: Blackboard): StateMachineNode =
        StateMachineNode(machine, blackboard)

    /** Wraps a state machine as a behavior-tree node, with a custom node name. */
    @JvmStatic
    fun toNode(machine: StateMachine, blackboard: Blackboard, name: String): StateMachineNode =
        StateMachineNode(machine, blackboard, name)

    /** Wraps a single case as a behavior-tree node (through a one-case machine; see the class docs). */
    @JvmStatic
    fun toNode(case: Case, blackboard: Blackboard): StateMachineNode =
        StateMachineNode(StateMachine(case), blackboard)

    /** Wraps a single case as a behavior-tree node, with a custom node name. */
    @JvmStatic
    fun toNode(case: Case, blackboard: Blackboard, name: String): StateMachineNode =
        StateMachineNode(StateMachine(case), blackboard, name)
}
