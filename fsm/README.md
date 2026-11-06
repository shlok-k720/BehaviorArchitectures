# Finite State Machine (`fsm`)

A Java-first, pure-JVM finite state machine for FTC robots, in the shape of
a normal switch/case machine: each state is a `Case`, and the OpMode loop
ticks the current case once per loop. The model follows 23641 Cuberobot's
`util/fsm` (State/Transition/StateMachine), with direct case references
instead of string names, a per-tick `execute()`, and an injected clock.

## Install from JitPack

```kotlin
dependencies {
    implementation("com.github.shlok-k720.BehaviorArchitectures:fsm:<version>")
}
```

Replace `<version>` with a release tag, such as `v0.2.0`, after it has been
built by JitPack.

## The model

A **case** is a state. The machine holds an ordered list of them, one active
at a time, and `update()` is the `switch`:

```java
// Subclass Case for a state with per-tick work:
class IntakeCase extends Case {
    IntakeCase() { super("intake"); }

    @Override public void onEnter() { intake.start(); }
    @Override public void execute() { /* runs every tick while active */ }
    @Override public void onExit() { intake.stop(); }
}
```

Or build one from lambdas with `SimpleCase`, the 23641 style (work started
in `onEnter`, transitions poll conditions):

```java
new SimpleCase("intake", () -> intake.start(), null, () -> intake.stop())
```

A case ends in priority order: an explicit `transition(other)` call inside
`execute()` (applied at the end of the tick); a declarative transition
whose condition is true (checked in the order added, once the case has been
active at least its minimum time); or its maximum time expiring (to its
fallback case, or the next case in the list). A transition with a `null`
target advances to the next case, like falling through. Advancing past the
last case stops the machine.

```java
StateMachine machine = new StateMachine(intake, shoot, park);
machine.start();
machine.update();   // once per loop
```

Times are plain `long` milliseconds (no `java.time.Duration`: `Duration` is
API 26+ on Android and this library targets minSdk 24).

## Transitions and timing

```java
intake.minTimeMs(600)              // transitions cannot fire before 600 ms
      .addTransition(() -> intake.isFull(), shoot)
      .addTransition(() -> intake.isJammed())   // null target: advance
      .maxTimeMs(3000)             // forced finish after 3 s
      .fallback(shoot);            // where the timeout goes
```

`transition(other)` inside `execute()` is never gated by either time — it
is the case deciding for itself, right now. From outside a case, use
`machine.transitionTo(target)` (applied at the end of the current tick).

## Machine lifecycle

`start()` begins from the first case (or `start(case)` from a given one);
`update()` ticks; `stop()` exits the current case; `reset()` stops and
rewinds to the first case. `isFinished()` is true once the machine has run
out of cases; `getCurrentCase()` is the active case (null when stopped).
`StateMachineListener` reports `onStarted`, `onTransition`, `onFinished`,
and `onStopped` — all default no-ops, so implement only what you need.

## Builders

Java:

```java
StateMachine machine = JavaBuilder.stateMachine()
        .addCase(intake)
        .addCase(shoot)
        .initial(intake)
        .onTransition((from, to) -> telemetry.addData("state", to.getName()))
        .build();
```

Kotlin DSL (targets are case *names*, resolved when the machine is built):

```kotlin
val machine = stateMachine {
    case("intake") {
        onEnter { intake.start() }
        transition({ intake.isFull }, "shoot")
        maxTimeMs(3000)
        fallback("shoot")
    }
    case("shoot") {
        onEnter { shooter.fire() }
    }
}
```

## Tests

```bash
./gradlew :fsm:test
./gradlew :fsm:test --tests com.rmsftc.fsm.UnitTestAll
```
