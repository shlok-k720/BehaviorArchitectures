# Changelog

All notable changes to this project are documented here, in Keep a Changelog format.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-11-05

### Added

- `TreesManager` for creating, ticking, resetting, and invalidating FTC behavior
  trees that share a blackboard, with a telemetry logger helper.
- `InverterNode`, `SucceederNode`, `RetryNode`, `RepeatNode`, and `FallbackNode`
  decorators / nodes, with builder methods in both `JavaBuilder` and `KotlinBuilder`.
- `ParallelPolicy.ANY_SUCCESS` and `ParallelPolicy.atLeast(n)` threshold policy.
- `Blackboard.getIfPresent()`, `removeValue()`, `removeCallbacks()`, and
  `putIfAbsent()` (the read has no write side effect; value and callback removal
  are now independent).
- `NodeStatus.isTerminal()` / `isRunning()` helpers.
- `BehaviorNode.children()`, `forEachNode()`, and `toTreeString()` for inspecting
  and debugging trees.
- `java.time.Duration` overloads for `TimeoutNode` and `waitMs` in both builders.
- `JavaBuilder.action(name, tick)` tick-only overload.
- GitHub Actions CI: `:core:test` and `:ftc:assembleRelease` on push and PR.
- README sections for the tree lifecycle, the `ParallelPolicy` truth table, and
  Blackboard gotchas (`getOrDefault()` writes; `remove()`/`clearAll()` delete
  callbacks).

### Fixed

- Stale `LimiterConfig` reference in a `core/build.gradle.kts` comment.
- Removed the tracked `.kotlin/sessions` compiler file from the repository
- Aligned `gradle/libs.versions.toml` plugin versions with the versions actually
  applied in the root build file (Kotlin 2.1.20, AGP 8.7.0).

### Changed

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
