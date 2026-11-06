# Command Library (`cmdlib`)

A Java-first, pure-JVM command-based library for FTC robots, in the shape of
WPILib's commands v2 with ideas from Pedro Pathing's Ivy and WPILib v3. The
library separates reusable command logic from FTC-specific integration so
decision-making code can be tested on the JVM before it runs on a robot. The
`ftc` module owns the OpMode integration; this module knows nothing about
OpModes.

## Install from JitPack

```kotlin
dependencies {
    implementation("com.github.shlok-k720.BehaviorArchitectures:cmdlib:<version>")
}
```

Replace `<version>` with a release tag, such as `v0.2.0`, after it has been
built by JitPack.

## The model

A **command** is a unit of work. The scheduler ticks it once per loop; the
command reports whether it is still running.

```kotlin
// Subclass Command for a mechanism action. Requirements are untyped tokens
// (Ivy-style): any object, usually a string naming the mechanism, or the
// mechanism itself. No two running commands may hold the same token.
class DriveToTarget(private val drivetrain: Drivetrain) : Command() {
    init { addRequirements("drivetrain") }
    override fun start() { drivetrain.beginDrive() }
    override fun loop() { drivetrain.update() }
    override fun status() = Status.of(!drivetrain.atTarget())
    override fun end(condition: EndCondition) { drivetrain.stop() }
}
```

Or build one from lambdas with the `Commands` factories:

```kotlin
val score = Commands.sequence(
    Commands.runOnce({ intake.close() }, "intake"),
    Commands.waitUntil { shooter.atSpeed() },
    Commands.runOnce({ shooter.fire() }, "shooter")
)
IScheduler.instance.schedule(score)
```

Lifecycle: `start()` runs once on the first tick, `loop()` runs every tick,
`status()` reports running/finished, and `end(condition)` runs once at the
end — with `NATURALLY` for a normal finish and `INTERRUPTED` for a cancel
or a lost requirement conflict. Cancelling a command that never started does
nothing (its `end()` never runs).

## The scheduler

The OpMode loop calls `IScheduler.instance.execute()` once per loop; `stop()`
calls `cancelAll()`. Each tick polls triggers, ticks every scheduled command,
then promotes queued commands.

Requirements are enforced: no two running commands may hold the same token.
Conflicts are decided by integer `priority` first (higher interrupts lower;
lower is blocked), and at equal priority by the *running* command's
`InterruptionBehavior` (`CANCEL_SELF` ends it, `CANCEL_INCOMING` rejects the
newcomer). Scheduling returns a typed `ScheduleResult`, so a dropped schedule
is visible instead of silent. A command whose `blockedBehavior` is `QUEUE`
is parked and retried every tick.

## Groups and decorators

- **Groups:** `SequentialCommandGroup` (one at a time, same-tick advance),
  `ParallelCommandGroup` (ends when the last ends; children may not share a
  requirement token), `ParallelRaceGroup` (ends when the first ends; losers are
  interrupted), `ParallelDeadlineGroup` (ends when the deadline ends).
  `Commands.sequence / parallel / race / deadline` build them.
- **Selectors:** `either` / `select` (key) / `branch` (first true of N) /
  `match` (enum switch) choose a child once, at start; `defer` builds its
  command lazily (requirements must be declared up front).
- **Decorators** (on any command): `andThen`, `beforeStarting`, `alongWith`,
  `raceWith`, `deadlineWith` / `withDeadline`, `withTimeout`, `until`,
  `onlyWhile`, `onlyIf` / `unless`, `repeatedly`, `asProxy`, `finallyDo`,
  `handleInterrupt`, `withInterruptBehavior`, `ignoringDisable`, `withName`.
  A command that has been composed into a group or decorator is consumed —
  scheduling it directly or reusing it throws.
- **Forking:** `ProxyCommand` schedules another command and ends when it
  ends; `ScheduleCommand` fires and forgets.
- **Triggers:** `Trigger(BooleanSupplier)` bindings — `onTrue`, `onFalse`,
  `whileTrue`, `whileFalse`, `toggleOnTrue`, `onChange`, `ifTrue`, `and` /
  `or` / `negate`, `debounce(Duration, RISING/FALLING/BOTH)`. Gamepad bindings
  belong in the `ftc` module, not here.

## Tests

```bash
./gradlew :cmdlib:test
./gradlew :cmdlib:test --tests com.rmsftc.cmdlib.UnitTestAll
```
