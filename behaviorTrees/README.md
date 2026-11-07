# Behavior Trees

A Java and Kotlin behavior-tree library for FTC robots. The library separates reusable tree logic from FTC-specific integration so that decision-making code can be tested on the JVM before it runs on a robot.

## Modules

| Artifact | Purpose |
| --- | --- |
| `behaviorTrees` | Platform-independent behavior-tree nodes. This is the dependency most robot projects need. Shared state and logging live in `util`. The builders live in `ftc` (`com.rmsftc.ftcImpl.Trees`). |
| `ftc` | Android/FTC-facing module that depends on `behaviorTrees` and compiles against the FTC SDK. |

## Install from JitPack

This project is configured to publish each module as a JitPack artifact. Add JitPack to the consuming project's repositories:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then depend on the module required by the robot project:

```kotlin
dependencies {
    implementation("com.github.shlok-k720.BehaviorArchitectures:behaviorTrees:<version>")
    // implementation("com.github.shlok-k720.BehaviorArchitectures:ftc:<version>")
}
```

Replace `<version>` with a release tag, such as `v0.2.0`, after it has been built by JitPack. The `ftc` artifact already brings in `behaviorTrees`.

## Building a Tree

Every `BehaviorNode` returns one of three statuses:

| Status | Meaning |
| --- | --- |
| `RUNNING` | Work is still in progress; tick the tree again on the next loop. |
| `SUCCESS` | The node completed its goal. |
| `FAILURE` | The node could not complete its goal or was invalidated. |

Call `tree.tick()` once per OpMode loop. A node runs its start callback once for an activation, its tick callback on every loop while active, and its end callback when it finishes or is invalidated.

The builders live in the `ftc` module, under `com.rmsftc.ftcImpl.Trees` — this artifact holds the nodes only. Or build through `ArchitectureManager.tree(...)`, which manages the tree and shares one blackboard with your machines and commands.

### Kotlin DSL

```kotlin
import com.rmsftc.bttree.NodeStatus
import com.rmsftc.util.blackboard.Blackboard
import com.rmsftc.ftcImpl.Trees

val blackboard = Blackboard()

val tree = Trees.KotlinBuilder.rootTree(blackboard) {
    sequence("score preload") {
        condition("has target") { blackboard.has("target") }
        action(
            name = "drive to target",
            start = { drivetrain.beginDrive() },
            tick = {
                if (drivetrain.atTarget()) NodeStatus.SUCCESS else NodeStatus.RUNNING
            },
            end = { drivetrain.stop() }
        )
    }
}

// In the OpMode loop:
tree.tick()
```

### Java Builder

```java
Blackboard blackboard = new Blackboard();

ReactiveSelectorNode tree = Trees.JavaBuilder.rootTree(blackboard, root ->
    root.sequence("score preload", sequence -> {
        sequence.condition("has target", () -> blackboard.has("target"));
        sequence.action(
            "drive to target",
            () -> drivetrain.beginDrive(),
            () -> drivetrain.atTarget() ? NodeStatus.SUCCESS : NodeStatus.RUNNING,
            () -> drivetrain.stop()
        );
    })
);

// In the OpMode loop:
tree.tick();
```

## Node Types

- **`ConditionNode`** evaluates a boolean predicate and returns `SUCCESS` or `FAILURE`.
- **`ActionNode`** adapts start, tick, and end callbacks into a behavior node.
- **`SequenceNode`** processes children in order; it fails on the first failure and succeeds only when all children succeed.
- **`ReactiveSelectorNode`** re-evaluates children from highest priority on every tick. A higher-priority child can preempt a lower-priority child that is running.
- **`ParallelNode`** ticks all unfinished children. Use `ParallelPolicy.JOIN` to wait for all children, `SUCCESS` to require all children to succeed, or `FAILURE` to fail only when all children fail.
- **`TimeoutNode`** ticks its child until the duration in milliseconds elapses, then invalidates the child and returns `FAILURE`. `waitMs` in either builder creates this timeout wrapper around a nested reactive selector.
- **`InverterNode`** swaps its child's SUCCESS and FAILURE; RUNNING passes through unchanged.
- **`SucceederNode`** turns its child's FAILURE into SUCCESS; SUCCESS and RUNNING pass through unchanged.
- **`RetryNode`** re-ticks its child until it succeeds, up to a maximum number of attempts (a RUNNING child does not consume an attempt), then returns FAILURE.
- **`RepeatNode`** ticks its child until it has succeeded a target number of times, then returns SUCCESS; a single child FAILURE fails it immediately.
- **`FallbackNode`** is a selector with memory: it returns the first SUCCESS or RUNNING result, but unlike `ReactiveSelectorNode` it does not re-evaluate higher-priority children on every tick, so a running lower-priority branch is never preempted.

### Parallel policies

The result of a `ParallelNode` depends on its policy once every child has finished
(any still-RUNNING child keeps the node RUNNING under every policy):

| Children finished as | `JOIN` | `SUCCESS` | `FAILURE` | `ANY_SUCCESS` | `atLeast(n)` |
| --- | --- | --- | --- | --- | --- |
| All succeed | SUCCESS | SUCCESS | SUCCESS | SUCCESS | SUCCESS if count ≥ n |
| All fail | SUCCESS | FAILURE | FAILURE | FAILURE | FAILURE |
| Mixed success/failure | SUCCESS | FAILURE | SUCCESS | SUCCESS | SUCCESS if successes ≥ n, else FAILURE |

Note the two surprises: `JOIN` reports SUCCESS even when every child failed (it only
waits for all children, it does not judge them), and `FAILURE` reports SUCCESS on mixed
results (it only fails when *all* children failed). If a failure must fail the node,
use `SUCCESS`; if any single success is enough, use `ANY_SUCCESS`.

Call `reset()` before reusing a tree when its state should return to never-ticked. Finished nodes also start fresh when ticked again, while `invalidate()` aborts only a currently running node.

## Tree lifecycle

- **Tick:** call `tree.tick()` once per OpMode loop. A node's `onStart()` runs once per
  activation, `onTick()` runs every loop while active, and `onEnd()` runs when it
  finishes or is invalidated.
- **Restart:** a node that finished with SUCCESS or FAILURE starts fresh the next time it
  is ticked. Composites reset their state in `onStart()`, so a sequence re-runs from its
  first child and a timeout restarts its timer.
- **Invalidate:** `invalidate()` aborts a node that is currently RUNNING (and calls its
  `onEnd()` once). It is a no-op on a node that already finished, so aborting a finished
  child never calls its `onEnd()` a second time or flips its result to FAILURE.
- **Reset:** `reset()` returns a node (and, for composites, its descendants) to the
  never-ticked state, aborting it first if it is running. It is optional between runs:
  ticking a finished node already restarts it.
- **Debugging:** `tree.toTreeString()` prints the tree with each node's name, type, and
  current status; `tree.forEachNode { ... }` visits every node depth-first, and
  `node.children()` lists a node's direct children.

## Blackboard, Callbacks, and Logging

`Blackboard` and `Logger` live in the `util` module
(`com.rmsftc.util.blackboard`, `com.rmsftc.util.logger`) and are shared
with `fsm` and `cmdlib`. `Blackboard` is typed shared state for tree
nodes. It accepts both string and enum keys:

```kotlin
blackboard.set("target", "backdrop")
val target = blackboard.get("target", String::class.java)
```

Two behaviors catch people out:

- **`getOrDefault()` writes the default.** When the key is missing (or holds a value of
  the wrong type), the default is stored into the board — and its callbacks fire — before
  it is returned. For a read with no side effects, use `getIfPresent(key, type)`, which
  returns null instead.
- **`remove()` and `clearAll()` also delete callbacks.** Clearing a value between
  autonomous runs silently deregisters its interceptors. Use `removeValue(key)` to clear
  only the value (callbacks stay) or `removeCallbacks(key)` to drop only the callbacks
  (the value stays). `putIfAbsent(key, type, value)` stores a default only when the key
  is absent, without the write-on-read behavior of `getOrDefault()`.

Register callbacks for changes that must take effect even if the corresponding branch is not currently being ticked:

```kotlin
blackboard.registerCallback("emergency_stop") { _, value ->
    if (value == true) drivetrain.stop()
}
```

Callbacks remain registered until they are explicitly deregistered, their key is removed, or `clearAll()` is called. Build a tree once per blackboard, or clear/deregister its callbacks before rebuilding it.

Attach a `LogSystem` to receive node lifecycle events:

```kotlin
Logger.attachLogger { caption, data, timestampMs ->
    telemetry.addData(caption, "$timestampMs $data")
}
```

## Tests

Run the core test suite:

```bash
./gradlew :behaviorTrees:test
```

Run the aggregate suite explicitly:

```bash
./gradlew :behaviorTrees:test --tests com.rmsftc.bttree.UnitTestAll
```

## Publishing

The publishing setup follows the [FTC library publishing guide](https://shlok-k720.github.io/documentation-guides/guides/ftc_library_publishing_guide.html#jitpack-build-files):

1. Update `VERSION_NAME` in `gradle.properties` to match the release tag without its leading `v`.
2. Verify the publishable modules locally:

   ```bash
   ./gradlew clean :behaviorTrees:build :ftc:assembleRelease
   ```

3. Commit the version change, create and push the matching tag, for example `v0.2.0`.
4. Open the repository on JitPack and wait for the tagged build to succeed before using the version in a consumer project.

JitPack builds this repository with the OpenJDK 17 configuration in `jitpack.yml`. The published `behaviorTrees` artifact targets JVM 8, and the `ftc` artifact is an Android library that marks FTC SDK dependencies as `compileOnly` so consuming robot projects provide their own SDK runtime.
