# Utilities (`util`)

Shared support for the other modules: the `Blackboard` (typed shared
state with change callbacks) and the `Logger` (a static log fan-out).
`behaviorTrees`, `fsm`, and `cmdlib` all depend on it; nothing in here
depends on them.

## Install from JitPack

```kotlin
dependencies {
    implementation("com.github.shlok-k720.BehaviorArchitectures:util:<version>")
}
```

Replace `<version>` with a release tag, such as `v0.2.0`, after it has been
built by JitPack.

## Blackboard

Typed shared state, keyed by string or enum. Nodes, cases, and commands
read and write it instead of passing values through constructors:

```kotlin
val blackboard = Blackboard()
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

Register callbacks for changes that must take effect even if the
corresponding branch is not currently being ticked:

```kotlin
blackboard.registerCallback("emergency_stop") { _, value ->
    if (value == true) drivetrain.stop()
}
```

Enum keys are converted to `DeclaringClass#CONSTANT` strings, so the same
enum value is the same key everywhere.

## Logger

A static fan-out: callers log events, and every attached `LogSystem`
receives them with a caption, data, and a timestamp. Nothing is attached
by default, and logging is a no-op until something is:

```kotlin
Logger.attachLogger(LogSystem { caption, data, timeMs ->
    telemetry.addData(caption, data)
})
Logger.log(Logger.NODE_STARTED, "name=intake,type=action,status=RUNNING")
```

Each module logs its own lifecycle with its own captions on `Logger`:
behavior trees use `NODE_STARTED` / `NODE_TICKED` / `NODE_FINISHED` /
`NODE_INVALIDATED` / `NODE_RESET`, `fsm` uses `FSM_STATE_ENTERED` /
`FSM_STATE_EXITED` / `FSM_FINISHED`, and `cmdlib` uses `CMD_SCHEDULED` /
`CMD_FINISHED` / `CMD_CANCELLED` / `CMD_INTERRUPTED`. The `ftc` module's
`TreesManager.telemetryLogger(telemetry)` attaches a log system that
writes to FTC telemetry.

`Logger.reset()` detaches every log system (tests use it between runs).

## Tests

```bash
./gradlew :util:test
./gradlew :util:test --tests com.rmsftc.util.UnitTestAll
```
