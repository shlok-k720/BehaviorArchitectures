# FTC integration (`ftc`)

The Android/FTC-facing module: it depends on `behaviorTrees`, `cmdlib`,
`fsm`, and `util`, and compiles against the FTC SDK. This is the module
robot code depends on.

## Install from JitPack

```kotlin
dependencies {
    implementation("com.github.shlok-k720.BehaviorArchitectures:ftc:<version>")
}
```

Replace `<version>` with a release tag, such as `v0.2.0`, after it has been
built by JitPack. The `ftc` artifact brings in the other modules.

## ArchitectureManager

One entry point for all three architectures, in an OpMode:

```kotlin
val manager = ArchitectureManager()

val tree = manager.tree {
    action("score", tick = { NodeStatus.SUCCESS })
}
val machine = manager.stateMachine {
    case("intake") {
        onExecute { if (full) transitionTo("shoot") }
    }
}
manager.schedule(Commands.runOnce({ shooter.fire() }, "shooter"))

// in start(): manager.startAll()
// in the loop: manager.tickAll()
```

- One shared `Blackboard`: trees, machines, and scheduled commands all
  read and write the same board.
- One `Scheduler`: commands scheduled here get the shared board attached.
- `tickAll()` ticks every tree, updates every machine, and executes the
  scheduler. Trees start themselves on their first tick; machines need
  `startAll()` once, in `start()`.
- `commands { }` builds a command with `CommandsBuilder` and schedules it
  in one step; use `CommandsBuilder` directly plus `schedule()` to build
  one and schedule it later.
- `resetAndInitialize()` clears everything and starts fresh with a new
  board; `invalidateAll()` / `resetAll()` abort or reset everything.
- `telemetryLogger(telemetry)` writes every log line (tree, machine, and
  command events) to OpMode telemetry.

## Builders

The tree and state-machine builders live here, grouped under
`com.rmsftc.ftcImpl.Trees` and `com.rmsftc.ftcImpl.StateMachines` (plus the
top-level `com.rmsftc.ftcImpl.stateMachine` function for the Kotlin DSL),
and the command builder `com.rmsftc.ftcImpl.CommandsBuilder`:

```kotlin
// Kotlin
val tree = Trees.KotlinBuilder.rootTree(board) { action("ok") { NodeStatus.SUCCESS } }
val machine = stateMachine { case("intake") { onExecute { } } }
val cmd = CommandsBuilder()
    .runOnce({ intake.start() }, "intake")
    .waitMs(500)
    .build()
```

```java
// Java
ReactiveSelectorNode tree = Trees.JavaBuilder.rootTree(board, root -> ...);
StateMachine machine = StateMachines.JavaBuilder.stateMachine(b -> b.waitMs(State.of("wait"), 500));
Command cmd = new CommandsBuilder()
        .runOnce(() -> intake.start(), "intake")
        .waitMs(500)
        .build();
```

Build through the manager when the tree, machine, or command should share
the manager's board and be ticked by `tickAll()` — `manager.commands { }`
builds a command with `CommandsBuilder` and schedules it in one step.

## ArchitecturalOpMode

A `LinearOpMode` base class that runs everything through one
`ArchitectureManager`. Extend it, set up hardware in `onInit()`, and build
the architectures in `build()`:

```java
@Autonomous
public class MyAuto extends ArchitecturalOpMode {
    @Override
    protected void build(ArchitectureManager manager) {
        manager.commands(b -> b
                .runOnce(() -> intake.start(), "intake")
                .waitMs(500)
                .runOnce(() -> shooter.fire(), "shooter"));
    }
}
```

`runOpMode()` is final: it calls `onInit()`, then `build()`, then
`waitForStart()`, then `manager.startAll()`, then loops `tickAll()` (plus
`onLoop()` and a telemetry update) while the OpMode is active, and calls
`onStop()` at the end, which invalidates everything. Call `interrupt()` to
abort everything and end the run early.

## ArchitectureConverter

Switch between architectures — a machine as a command, a command as a
case, a tree as a command, and so on:

```kotlin
val command = ArchitectureConverter.toCommand(machine)   // StateMachine -> Command
val case = ArchitectureConverter.toCase(command, State.of("run"))  // Command -> Case
val node = ArchitectureConverter.toNode(machine, board)   // StateMachine -> BehaviorNode
```

See the converter's docs for the rules: a converted thing runs inline
where it lands (a command-as-case is ticked by the machine, not by a
scheduler, so its requirements are not checked and it keeps its own
blackboard), it must be driven in exactly one place, a single case
converts through a one-case machine (so it can only finish by advancing),
and commands have no FAILURE (a finished command-as-node reports
SUCCESS, a FAILED tree-as-command still counts as finished).

## Tests

```bash
./gradlew :ftc:testDebugUnitTest
./gradlew :ftc:testDebugUnitTest --tests com.rmsftc.ftcImpl.UnitTestAll
```

The JUnit 5 tests (moved builder tests, converter and manager tests) run
under the JUnit Platform; the older JUnit 4 tests keep running through
the vintage engine.
