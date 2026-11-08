# Changelog

All notable changes to this project are documented here, in Keep a Changelog format.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-11-07

First tagged release of BehaviorArchitectures (tag `v0.2.0`).

### Added

- `behaviorTrees` module (from BehaviorTrees v0.1.0): `InverterNode`,
  `SucceederNode`, `RetryNode`, `RepeatNode`, and `FallbackNode` decorators /
  nodes, with builder methods in both `JavaBuilder` and `KotlinBuilder`;
  `ParallelPolicy.ANY_SUCCESS` and `ParallelPolicy.atLeast(n)` threshold
  policy; `Blackboard.getIfPresent()`, `removeValue()`, `removeCallbacks()`,
  and `putIfAbsent()` (the read has no write side effect; value and callback
  removal are now independent); `NodeStatus.isTerminal()` / `isRunning()`
  helpers; `BehaviorNode.children()`, `forEachNode()`, and `toTreeString()`
  for inspecting and debugging trees; `JavaBuilder.action(name, tick)`
  tick-only overload.
- `cmdlib`: Ivy-style command framework (WPILib v2 lifecycle, untyped token
  requirements, no subsystems): `Scheduler` with priority and interruption
  behavior, groups, selectors, decorators, triggers, and `Commands` factories.
- `fsm`: standalone switch/case state machines, tick-driven via `update()`,
  with Java and Kotlin builders and built-in cases mirroring the cmdlib
  commands.
- `util`: `Blackboard` and `Logger` / `LogSystem`, shared by all modules.
- `ftc`: `ArchitectureManager` — one entry point for trees, machines, and
  commands (shared blackboard, one scheduler, one `tickAll()`), holding the
  `Trees`, `StateMachines`, and `CommandsBuilder` builders;
  `ArchitectureConverter` to convert between all three architectures;
  `ArchitecturalOpMode`, a `LinearOpMode` base class that runs everything
  through one manager; `CommandsBuilder` and
  `ArchitectureManager.commands { }` for building and scheduling commands
  in the manager.

### Removed

- `TreesManager`, removed in favor of `ArchitectureManager` (which covers
  trees plus machines and commands) and `ArchitecturalOpMode`.

### Changed

- `java.time.Duration` is not used in library code (ftc minSdk 24):
  durations are plain `long` milliseconds throughout (`WaitCommand`,
  `withTimeout`, `waitMs`, `debounce`, `TimeoutNode`).
- `Logger.log()` returns early when no loggers are attached, skipping the
  timestamp and iteration on every node tick.

## [0.1.0] - 2026-11-05

First published release (JitPack build verified for `core` and `ftc`).

### Added

- Core behavior-tree nodes: `ActionNode`, `ConditionNode`, `SequenceNode`,
  `ReactiveSelectorNode`, `ParallelNode` (`JOIN` / `SUCCESS` / `FAILURE`),
  `TimeoutNode`.
- `JavaBuilder` and `KotlinBuilder` DSLs, typed `Blackboard` with callbacks,
  and static `Logger` / `LogSystem` lifecycle logging.
- `ftc` module (Android library, FTC SDK 12.0.0 `compileOnly`).
- MIT license.
