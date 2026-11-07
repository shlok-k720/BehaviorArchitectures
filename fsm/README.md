# Finite State Machine (`fsm`)

A Java-first, pure-JVM finite state machine for FTC robots, shaped like a
normal Java switch statement: each state is a `Case`, and the OpMode loop
ticks the current case once per loop.

## Install from JitPack

```kotlin
dependencies {
    implementation("com.github.shlok-k720.BehaviorArchitectures:fsm:<version>")
}
```

Replace `<version>` with a release tag, such as `v0.2.0`, after it has been
built by JitPack.

## States

A state is a `State`: either a string or an enum value. Internally a state
is just its string form — the string itself, or the enum constant's name —
so `State.of("INTAKE")` and `State.of(IntakeStates.INTAKE)` are the same
state.

```java
enum IntakeStates { IDLE, INTAKING, FULL }

State.of("IDLE");               // from a string
State.of(IntakeStates.FULL);    // from an enum
```

## The model

A **case** is a state. The machine holds an ordered list of them, one active
at a time, and `update()` is the `switch`:

```java
class IntakeCase extends Case {
    IntakeCase() { super(State.of(IntakeStates.IDLE)); }

    @Override public void onStart() { intake.start(); }
    @Override public void onExecute() { /* runs every tick while active */ }
    @Override public void onTransition(Case other) { intake.stop(); }
}
```

Lifecycle: `onStart()` runs on entry, `onExecute()` runs every tick, and
`onTransition(other)` runs on the case being left, just before the switch —
with the case being transitioned into (or null when the machine stops or
finishes). It is the opposite of `onStart`.

A case moves the machine with the `transition` utilities, applied at the
end of the current tick:

```java
transition(State.of(IntakeStates.FULL));  // jump to a state
transition(otherCase);                    // jump to a case
transitionToNext();                       // advance to the next case in the list
```

Advancing past the last case stops the machine. Outside a case, the same
moves are `machine.transitionTo(state)` and `machine.transitionToNext()`.

```java
StateMachine machine = new StateMachine(intake, shoot, park);
machine.start();
machine.update();   // once per loop
```

Times are plain `long` milliseconds (no `java.time.Duration`: `Duration` is
API 26+ on Android and this library targets minSdk 24).

## CaseBuilder

A case built from lambdas, like Ivy's `CommandBuilder`:

```java
CaseBuilder wait = new CaseBuilder(State.of("WAIT"));
wait.setOnExecute(() -> {
    if (intake.isFull()) wait.transitionToNext();
});
```

Defaults are all no-ops, so a bare builder runs forever until something
transitions the machine away. `CaseBuilder.build(state)` is the static
factory, like Ivy's `Command.build()`.

## Built-in cases

The seven base commands from `cmdlib`, mirrored as cases (each takes its
own state name). `CaseBuilder` is the Functional one; the other six live
in `com.rmsftc.fsm.cases`:

- `WaitCase(state, millis)` — waits, then advances (or jumps to a given target state).
- `WaitUntilCase(state, condition)` — waits for a condition, then advances (or jumps).
- `RunCase(state, action)` — runs the action every tick, never finishes on its own.
- `InstantCase(state, action)` — runs once, then advances (or jumps).
- `StartEndCase(state, onStart, onEnd)` — start action on entry, end action on exit.
- `PrintCase(state, message)` — prints once (to `System.out`), then advances (or jumps).

## Builders

Java:

```java
StateMachine machine = JavaBuilder.stateMachine()
        .addCase(intake)
        .addCase(shoot)
        .initial(State.of("intake"))
        .build();
```

Kotlin DSL (same core types, Kotlin-idiomatic: the blocks run with the
case's DSL as receiver, so transitions are called from inside them):

```kotlin
val machine = stateMachine {
    case("intake") {
        onStart { intake.start() }
        onExecute { if (intake.isFull) transitionTo("shoot") }
    }
    case(IntakeStates.SHOOT) {
        onStart { shooter.fire() }
        onExecute { if (shooter.done()) transitionToNext() }
    }
}
```

## Tests

```bash
./gradlew :fsm:test
./gradlew :fsm:test --tests com.rmsftc.fsm.UnitTestAll
```
