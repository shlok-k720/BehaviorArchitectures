# Contributing

## Getting started

This is a small library with two modules: `behaviorTrees` (plain JVM, the part most
robot projects need) and `ftc` (Android library, FTC SDK `compileOnly`).
You need JDK 17 to build (see `jitpack.yml`); the published `behaviorTrees` artifact
targets JVM 8.

```bash
./gradlew :behaviorTrees:test          # run the core test suite
./gradlew :behaviorTrees:build :ftc:assembleRelease   # verify the publishable modules
```

## How to contribute

1. Open an issue first for anything bigger than a typo, so the design can be
   agreed before code is written.
2. Work on a branch named `feature/<short-name>` (or `fix/<short-name>`)
   from `main`, one idea per branch.
3. Keep changes small and reviewable. New behavior needs tests in
   `behaviorTrees/src/test` (JUnit 5, see the existing `*Test.kt` files for the style:
   backtick test names that read as sentences, `TestBehaviorNode` for
   scripted node results).
4. Add an entry to `CHANGELOG.md` under `[Unreleased]`.
5. Open a pull request against `main`. Do not merge your own PR, and do not
   commit directly to `main`.

## Style notes

- Public API is Kotlin-first with Java-friendly overloads (`Consumer` /
  `Supplier` variants) where the builders need them; keep that pattern when
  adding builder methods.
- New node types belong in `behaviorTrees/src/main/java/com/rmsftc/bttree/nodes/` with
  KDoc explaining their tick semantics, including what happens to a RUNNING
  child on `invalidate()` and `reset()`.
- The `ftc` module must stay FTC-`compileOnly`: never add an `implementation`
  dependency on the FTC SDK there.
